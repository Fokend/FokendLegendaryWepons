package fokend.legendaryweapons;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * SQLite storage of the Legend Chronicle.
 *
 * <p>Threading: there is exactly one connection and it is used only by one dedicated thread. Every public
 * method queues work on that thread and returns a future, so callers on the server thread never wait for
 * disk. Because the queue is FIFO, writes are applied in the order they were submitted. Rows passed in are
 * immutable snapshots taken on the server thread, so no game object is touched off the server thread.
 */
final class ChronicleDatabase {
    static final int SCHEMA_VERSION = 1;

    record LegendRow(String uid, String type, int gen, String owner, long ownerSince, String status,
                     String holder, String holderId, String world, int x, int y, int z,
                     String hint, String hintActor, String hintFrom, long hintAt, byte[] snapshot,
                     int callDx, int callDz, long callStarted, long lastAnnounce, int announces,
                     boolean released, boolean spawnPending, long lastSeen, long createdAt) {
    }

    record HistoryRow(long id, String uid, String type, String from, String to, String reason, long at,
                      String world, int x, int y, int z, String flag) {
    }

    record QuarantineRow(long id, String uid, String type, int gen, String reason, long foundAt,
                         String foundBy, String holder, String world, int x, int y, int z, byte[] item,
                         boolean resolved) {
    }

    record PlayerRow(String uuid, String name, String ip) {
    }

    @FunctionalInterface
    interface SqlWork<T> {
        T run(Connection c) throws SQLException;
    }

    private final File file;
    private final Logger log;
    private final ExecutorService executor;
    private Connection connection;
    private volatile boolean closed;

    ChronicleDatabase(File file, Logger log) {
        this.file = file;
        this.log = log;
        this.executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "LegendChronicle-DB");
            t.setDaemon(true);
            return t;
        });
    }

    /** Opens the file and creates the schema. Completes exceptionally if SQLite is unavailable. */
    CompletableFuture<Void> open() {
        return this.submit(c -> null, true);
    }

    private Connection connection() throws SQLException {
        if (this.connection == null) {
            try {
                Class.forName("org.sqlite.JDBC");
            } catch (ClassNotFoundException e) {
                throw new SQLException("SQLite JDBC driver (org.sqlite.JDBC) is not on the classpath", e);
            }
            File dir = this.file.getParentFile();
            if (dir != null) {
                dir.mkdirs();
            }
            Connection c = DriverManager.getConnection("jdbc:sqlite:" + this.file.getAbsolutePath());
            try (Statement st = c.createStatement()) {
                st.execute("PRAGMA journal_mode=WAL");
                st.execute("PRAGMA synchronous=NORMAL");
                st.execute("PRAGMA foreign_keys=ON");
                st.execute("PRAGMA busy_timeout=5000");
            }
            this.createSchema(c);
            this.connection = c;
        }
        return this.connection;
    }

    private void createSchema(Connection c) throws SQLException {
        try (Statement st = c.createStatement()) {
            st.execute("CREATE TABLE IF NOT EXISTS meta(k TEXT PRIMARY KEY, v TEXT NOT NULL)");
            st.execute("""
                    CREATE TABLE IF NOT EXISTS legends(
                      uid TEXT PRIMARY KEY, type TEXT NOT NULL, gen INTEGER NOT NULL,
                      owner TEXT, owner_since INTEGER NOT NULL DEFAULT 0, status TEXT NOT NULL,
                      holder TEXT NOT NULL, holder_id TEXT, world TEXT, x INTEGER, y INTEGER, z INTEGER,
                      hint TEXT, hint_actor TEXT, hint_from TEXT, hint_at INTEGER NOT NULL DEFAULT 0,
                      snapshot BLOB, call_dx INTEGER NOT NULL DEFAULT 0, call_dz INTEGER NOT NULL DEFAULT 0,
                      call_started INTEGER NOT NULL DEFAULT 0, last_announce INTEGER NOT NULL DEFAULT 0,
                      announces INTEGER NOT NULL DEFAULT 0, released INTEGER NOT NULL DEFAULT 0,
                      spawn_pending INTEGER NOT NULL DEFAULT 0, last_seen INTEGER NOT NULL DEFAULT 0,
                      created_at INTEGER NOT NULL DEFAULT 0)""");
            // At most one live instance of each legendary type, enforced by the database itself.
            st.execute("CREATE UNIQUE INDEX IF NOT EXISTS legends_one_live ON legends(type) WHERE status <> 'RETIRED'");
            st.execute("""
                    CREATE TABLE IF NOT EXISTS history(
                      id INTEGER PRIMARY KEY AUTOINCREMENT,
                      uid TEXT NOT NULL REFERENCES legends(uid), type TEXT NOT NULL,
                      from_uuid TEXT, to_uuid TEXT, reason TEXT NOT NULL, at INTEGER NOT NULL,
                      world TEXT, x INTEGER, y INTEGER, z INTEGER, flag TEXT)""");
            st.execute("CREATE INDEX IF NOT EXISTS history_by_uid ON history(uid, id)");
            st.execute("CREATE INDEX IF NOT EXISTS history_by_type ON history(type, id)");
            st.execute("""
                    CREATE TABLE IF NOT EXISTS quarantine(
                      id INTEGER PRIMARY KEY AUTOINCREMENT, uid TEXT, type TEXT, gen INTEGER NOT NULL DEFAULT 0,
                      reason TEXT NOT NULL, found_at INTEGER NOT NULL, found_by TEXT, holder TEXT,
                      world TEXT, x INTEGER, y INTEGER, z INTEGER, item BLOB NOT NULL,
                      resolved INTEGER NOT NULL DEFAULT 0)""");
            st.execute("""
                    CREATE TABLE IF NOT EXISTS sessions(
                      id INTEGER PRIMARY KEY AUTOINCREMENT, player TEXT NOT NULL,
                      start INTEGER NOT NULL, end INTEGER NOT NULL)""");
            st.execute("CREATE INDEX IF NOT EXISTS sessions_by_player ON sessions(player, end)");
            st.execute("""
                    CREATE TABLE IF NOT EXISTS players(
                      uuid TEXT PRIMARY KEY, name TEXT NOT NULL, ip TEXT, updated INTEGER NOT NULL)""");
            st.execute("INSERT OR IGNORE INTO meta(k, v) VALUES ('schema', '" + SCHEMA_VERSION + "')");
        }
    }

    // ---------------------------------------------------------------- queueing

    <T> CompletableFuture<T> submit(SqlWork<T> work) {
        return this.submit(work, false);
    }

    private <T> CompletableFuture<T> submit(SqlWork<T> work, boolean opening) {
        CompletableFuture<T> f = new CompletableFuture<>();
        if (this.closed && !opening) {
            f.completeExceptionally(new IllegalStateException("chronicle database is closed"));
            return f;
        }
        try {
            this.executor.execute(() -> {
                try {
                    f.complete(work.run(this.connection()));
                } catch (Throwable t) {
                    this.log.log(Level.SEVERE, "[Chronicle] database operation failed", t);
                    f.completeExceptionally(t);
                }
            });
        } catch (Exception rejected) {
            f.completeExceptionally(rejected);
        }
        return f;
    }

    /** Completes once every operation queued before it has finished. */
    CompletableFuture<Void> flush() {
        return this.submit(c -> null);
    }

    boolean isDatabaseThread() {
        return Thread.currentThread().getName().equals("LegendChronicle-DB");
    }

    /** Lets queued writes finish (bounded wait), then closes the connection. */
    void close() {
        if (this.closed) {
            return;
        }
        this.closed = true;
        this.executor.execute(() -> {
            if (this.connection != null) {
                try {
                    this.connection.close();
                } catch (SQLException e) {
                    this.log.log(Level.WARNING, "[Chronicle] close failed", e);
                }
                this.connection = null;
            }
        });
        this.executor.shutdown();
        try {
            if (!this.executor.awaitTermination(10, TimeUnit.SECONDS)) {
                this.log.severe("[Chronicle] database did not finish pending writes within 10 s");
                this.executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static <T> T inTransaction(Connection c, SqlWork<T> work) throws SQLException {
        boolean auto = c.getAutoCommit();
        c.setAutoCommit(false);
        try {
            T result = work.run(c);
            c.commit();
            return result;
        } catch (SQLException | RuntimeException e) {
            c.rollback();
            throw e;
        } finally {
            c.setAutoCommit(auto);
        }
    }

    // ---------------------------------------------------------------- legends

    private static final String UPSERT_LEGEND = """
            INSERT INTO legends(uid, type, gen, owner, owner_since, status, holder, holder_id, world, x, y, z,
              hint, hint_actor, hint_from, hint_at, snapshot, call_dx, call_dz, call_started, last_announce,
              announces, released, spawn_pending, last_seen, created_at)
            VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
            ON CONFLICT(uid) DO UPDATE SET type=excluded.type, gen=excluded.gen, owner=excluded.owner,
              owner_since=excluded.owner_since, status=excluded.status, holder=excluded.holder,
              holder_id=excluded.holder_id, world=excluded.world, x=excluded.x, y=excluded.y, z=excluded.z,
              hint=excluded.hint, hint_actor=excluded.hint_actor, hint_from=excluded.hint_from,
              hint_at=excluded.hint_at, snapshot=COALESCE(excluded.snapshot, legends.snapshot),
              call_dx=excluded.call_dx, call_dz=excluded.call_dz, call_started=excluded.call_started,
              last_announce=excluded.last_announce, announces=excluded.announces, released=excluded.released,
              spawn_pending=excluded.spawn_pending, last_seen=excluded.last_seen""";

    private static void upsert(Connection c, LegendRow r) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(UPSERT_LEGEND)) {
            int i = 1;
            ps.setString(i++, r.uid());
            ps.setString(i++, r.type());
            ps.setInt(i++, r.gen());
            ps.setString(i++, r.owner());
            ps.setLong(i++, r.ownerSince());
            ps.setString(i++, r.status());
            ps.setString(i++, r.holder());
            ps.setString(i++, r.holderId());
            ps.setString(i++, r.world());
            ps.setInt(i++, r.x());
            ps.setInt(i++, r.y());
            ps.setInt(i++, r.z());
            ps.setString(i++, r.hint());
            ps.setString(i++, r.hintActor());
            ps.setString(i++, r.hintFrom());
            ps.setLong(i++, r.hintAt());
            if (r.snapshot() != null) {
                ps.setBytes(i++, r.snapshot());
            } else {
                ps.setNull(i++, Types.BLOB);
            }
            ps.setInt(i++, r.callDx());
            ps.setInt(i++, r.callDz());
            ps.setLong(i++, r.callStarted());
            ps.setLong(i++, r.lastAnnounce());
            ps.setInt(i++, r.announces());
            ps.setInt(i++, r.released() ? 1 : 0);
            ps.setInt(i++, r.spawnPending() ? 1 : 0);
            ps.setLong(i++, r.lastSeen());
            ps.setLong(i, r.createdAt());
            ps.executeUpdate();
        }
    }

    private static void insertHistory(Connection c, HistoryRow h) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO history(uid, type, from_uuid, to_uuid, reason, at, world, x, y, z, flag) VALUES (?,?,?,?,?,?,?,?,?,?,?)")) {
            ps.setString(1, h.uid());
            ps.setString(2, h.type());
            ps.setString(3, h.from());
            ps.setString(4, h.to());
            ps.setString(5, h.reason());
            ps.setLong(6, h.at());
            ps.setString(7, h.world());
            ps.setInt(8, h.x());
            ps.setInt(9, h.y());
            ps.setInt(10, h.z());
            ps.setString(11, h.flag());
            ps.executeUpdate();
        }
    }

    CompletableFuture<Void> saveLegend(LegendRow row) {
        return this.submit(c -> {
            upsert(c, row);
            return null;
        });
    }

    /**
     * Writes several legend rows and history rows in one transaction, rows first and in the given order
     * (so a retired row is written before the row that replaces it and the one-live-per-type index holds).
     */
    CompletableFuture<Void> commit(List<LegendRow> rows, List<HistoryRow> history) {
        List<LegendRow> r = List.copyOf(rows);
        List<HistoryRow> h = List.copyOf(history);
        return this.submit(c -> inTransaction(c, conn -> {
            for (LegendRow row : r) {
                upsert(conn, row);
            }
            for (HistoryRow row : h) {
                insertHistory(conn, row);
            }
            return null;
        }));
    }

    CompletableFuture<List<LegendRow>> loadLegends() {
        return this.submit(c -> {
            List<LegendRow> out = new ArrayList<>();
            try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery("SELECT * FROM legends")) {
                while (rs.next()) {
                    out.add(new LegendRow(rs.getString("uid"), rs.getString("type"), rs.getInt("gen"),
                            rs.getString("owner"), rs.getLong("owner_since"), rs.getString("status"),
                            rs.getString("holder"), rs.getString("holder_id"), rs.getString("world"),
                            rs.getInt("x"), rs.getInt("y"), rs.getInt("z"), rs.getString("hint"),
                            rs.getString("hint_actor"), rs.getString("hint_from"), rs.getLong("hint_at"),
                            rs.getBytes("snapshot"), rs.getInt("call_dx"), rs.getInt("call_dz"),
                            rs.getLong("call_started"), rs.getLong("last_announce"), rs.getInt("announces"),
                            rs.getInt("released") != 0, rs.getInt("spawn_pending") != 0,
                            rs.getLong("last_seen"), rs.getLong("created_at")));
                }
            }
            return out;
        });
    }

    /** History of a legendary type across all its instances (a replaced instance keeps its past), newest first. */
    CompletableFuture<List<HistoryRow>> history(String type, int limit, int offset) {
        return this.submit(c -> {
            List<HistoryRow> out = new ArrayList<>();
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT * FROM history WHERE type = ? ORDER BY id DESC LIMIT ? OFFSET ?")) {
                ps.setString(1, type);
                ps.setInt(2, limit);
                ps.setInt(3, offset);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        out.add(new HistoryRow(rs.getLong("id"), rs.getString("uid"), rs.getString("type"),
                                rs.getString("from_uuid"), rs.getString("to_uuid"), rs.getString("reason"),
                                rs.getLong("at"), rs.getString("world"), rs.getInt("x"), rs.getInt("y"),
                                rs.getInt("z"), rs.getString("flag")));
                    }
                }
            }
            return out;
        });
    }

    CompletableFuture<Integer> historyCount(String type) {
        return this.submit(c -> {
            try (PreparedStatement ps = c.prepareStatement("SELECT COUNT(*) FROM history WHERE type = ?")) {
                ps.setString(1, type);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? rs.getInt(1) : 0;
                }
            }
        });
    }

    // ---------------------------------------------------------------- quarantine

    CompletableFuture<Long> quarantine(QuarantineRow q) {
        return this.submit(c -> {
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO quarantine(uid, type, gen, reason, found_at, found_by, holder, world, x, y, z, item) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)",
                    Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, q.uid());
                ps.setString(2, q.type());
                ps.setInt(3, q.gen());
                ps.setString(4, q.reason());
                ps.setLong(5, q.foundAt());
                ps.setString(6, q.foundBy());
                ps.setString(7, q.holder());
                ps.setString(8, q.world());
                ps.setInt(9, q.x());
                ps.setInt(10, q.y());
                ps.setInt(11, q.z());
                ps.setBytes(12, q.item());
                ps.executeUpdate();
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    return keys.next() ? keys.getLong(1) : -1L;
                }
            }
        });
    }

    private static QuarantineRow readQuarantine(ResultSet rs) throws SQLException {
        return new QuarantineRow(rs.getLong("id"), rs.getString("uid"), rs.getString("type"), rs.getInt("gen"),
                rs.getString("reason"), rs.getLong("found_at"), rs.getString("found_by"), rs.getString("holder"),
                rs.getString("world"), rs.getInt("x"), rs.getInt("y"), rs.getInt("z"), rs.getBytes("item"),
                rs.getInt("resolved") != 0);
    }

    CompletableFuture<List<QuarantineRow>> openQuarantine() {
        return this.submit(c -> {
            List<QuarantineRow> out = new ArrayList<>();
            try (Statement st = c.createStatement();
                 ResultSet rs = st.executeQuery("SELECT * FROM quarantine WHERE resolved = 0 ORDER BY id")) {
                while (rs.next()) {
                    out.add(readQuarantine(rs));
                }
            }
            return out;
        });
    }

    CompletableFuture<QuarantineRow> quarantineEntry(long id) {
        return this.submit(c -> {
            try (PreparedStatement ps = c.prepareStatement("SELECT * FROM quarantine WHERE id = ?")) {
                ps.setLong(1, id);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? readQuarantine(rs) : null;
                }
            }
        });
    }

    /** Marks an entry resolved; returns false if it was already resolved (so two admins cannot both act on it). */
    CompletableFuture<Boolean> resolveQuarantine(long id) {
        return this.submit(c -> {
            try (PreparedStatement ps = c.prepareStatement("UPDATE quarantine SET resolved = 1 WHERE id = ? AND resolved = 0")) {
                ps.setLong(1, id);
                return ps.executeUpdate() == 1;
            }
        });
    }

    /** Puts a resolved entry back to open, used when the action that resolved it could not be completed. */
    CompletableFuture<Void> reopenQuarantine(long id) {
        return this.submit(c -> {
            try (PreparedStatement ps = c.prepareStatement("UPDATE quarantine SET resolved = 0 WHERE id = ?")) {
                ps.setLong(1, id);
                ps.executeUpdate();
            }
            return null;
        });
    }

    // ---------------------------------------------------------------- activity

    CompletableFuture<Long> openSession(String player, long start) {
        return this.submit(c -> {
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO sessions(player, start, end) VALUES (?,?,?)", Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, player);
                ps.setLong(2, start);
                ps.setLong(3, start);
                ps.executeUpdate();
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    return keys.next() ? keys.getLong(1) : -1L;
                }
            }
        });
    }

    CompletableFuture<Void> touchSession(long id, long end) {
        return this.submit(c -> {
            try (PreparedStatement ps = c.prepareStatement("UPDATE sessions SET end = MAX(end, ?) WHERE id = ?")) {
                ps.setLong(1, end);
                ps.setLong(2, id);
                ps.executeUpdate();
            }
            return null;
        });
    }

    record Activity(long activeMillis, long lastSeen) {
    }

    /** Online time inside [since, now] and the end of the latest session (0 if never seen). */
    CompletableFuture<Activity> activity(String player, long since, long now) {
        return this.submit(c -> {
            long active = 0;
            long last = 0;
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT start, end FROM sessions WHERE player = ? AND end >= ?")) {
                ps.setString(1, player);
                ps.setLong(2, since);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        long s = Math.max(rs.getLong(1), since);
                        long e = Math.min(rs.getLong(2), now);
                        if (e > s) {
                            active += e - s;
                        }
                    }
                }
            }
            try (PreparedStatement ps = c.prepareStatement("SELECT MAX(end) FROM sessions WHERE player = ?")) {
                ps.setString(1, player);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        last = rs.getLong(1);
                    }
                }
            }
            return new Activity(active, last);
        });
    }

    /**
     * When the chronicle first ran on this server. Activity is only recorded from then on, so inactivity is
     * never counted from before it (otherwise every offline owner would be "inactive" on the first day).
     */
    CompletableFuture<Long> installedAt(long now) {
        return this.submit(c -> {
            try (PreparedStatement ps = c.prepareStatement("INSERT OR IGNORE INTO meta(k, v) VALUES ('installed_at', ?)")) {
                ps.setString(1, Long.toString(now));
                ps.executeUpdate();
            }
            try (PreparedStatement ps = c.prepareStatement("SELECT v FROM meta WHERE k = 'installed_at'")) {
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? Long.parseLong(rs.getString(1)) : now;
                }
            }
        });
    }

    // ---------------------------------------------------------------- players

    CompletableFuture<Void> savePlayer(String uuid, String name, String ip, long now) {
        return this.submit(c -> {
            try (PreparedStatement ps = c.prepareStatement("""
                    INSERT INTO players(uuid, name, ip, updated) VALUES (?,?,?,?)
                    ON CONFLICT(uuid) DO UPDATE SET name=excluded.name, ip=COALESCE(excluded.ip, players.ip),
                      updated=excluded.updated""")) {
                ps.setString(1, uuid);
                ps.setString(2, name);
                ps.setString(3, ip);
                ps.setLong(4, now);
                ps.executeUpdate();
            }
            return null;
        });
    }

    CompletableFuture<Map<String, PlayerRow>> loadPlayers() {
        return this.submit(c -> {
            Map<String, PlayerRow> out = new HashMap<>();
            try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery("SELECT uuid, name, ip FROM players")) {
                while (rs.next()) {
                    out.put(rs.getString(1), new PlayerRow(rs.getString(1), rs.getString(2), rs.getString(3)));
                }
            }
            return out;
        });
    }
}
