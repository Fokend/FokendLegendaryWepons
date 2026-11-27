package fokend.legendaryweapons;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/**
 * Play sessions, for Legend Call's activity check. A session row is opened on join and its end is moved
 * forward periodically and on quit, so a crash loses at most one touch interval.
 */
final class ActivityTracker {
    private final ChronicleDatabase db;
    private final LongSupplier clock;
    private final Map<UUID, CompletableFuture<Long>> open = new ConcurrentHashMap<>();

    ActivityTracker(ChronicleDatabase db, LongSupplier clock) {
        this.db = db;
        this.clock = clock;
    }

    void join(UUID player) {
        CompletableFuture<Long> previous = this.open.put(player, this.db.openSession(player.toString(), this.clock.getAsLong()));
        if (previous != null) {
            this.close(previous);
        }
    }

    void quit(UUID player) {
        CompletableFuture<Long> f = this.open.remove(player);
        if (f != null) {
            this.close(f);
        }
    }

    boolean isOpen(UUID player) {
        return this.open.containsKey(player);
    }

    private void close(CompletableFuture<Long> f) {
        long t = this.clock.getAsLong();
        f.thenAccept(id -> this.db.touchSession(id, t));
    }

    void touchAll() {
        long t = this.clock.getAsLong();
        for (CompletableFuture<Long> f : this.open.values()) {
            f.thenAccept(id -> this.db.touchSession(id, t));
        }
    }

    void closeAll() {
        for (UUID id : this.open.keySet()) {
            this.quit(id);
        }
    }
}
