package fokend.legendaryweapons;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.event.inventory.InventoryType;

/**
 * The rules of the Legend Chronicle. Everything here runs on the server thread; the database only persists
 * what this class decided.
 *
 * <p>Validity of an item is decided against the records, never by the item alone: an item is the live
 * instance of its type only if its uid is the live record of that type and its generation equals the
 * record's. Anything else is a copy. Copies are never destroyed: they are moved to quarantine, where an
 * admin can approve (make it the live instance) or discard them.
 */
final class LegendRegistry {
    /** Legendaries that have a chronicle. Temporary and visual ids (lightning, spear ice, mask forms) and cores do not. */
    static final List<String> ORDER = List.of(
            FokendLegendaryWepons.ID_RA_BLADE, FokendLegendaryWepons.ID_CHAOS_BOOMERANG,
            FokendLegendaryWepons.ID_TWINS_SWORD, FokendLegendaryWepons.ID_WARDEN_HELMET,
            FokendLegendaryWepons.ID_MAGNET, FokendLegendaryWepons.ID_TIME_CLOCK,
            FokendLegendaryWepons.ID_ICE_SPEAR, FokendLegendaryWepons.ID_DEFAULT_MASK,
            FokendLegendaryWepons.ID_SOUL_BOW, "death_note", "fate_scales");
    static final Set<String> CHRONICLED = Set.copyOf(ORDER);
    /** The Death Note's owner is a secret of its own mechanic (/check), so the chronicle never names it publicly. */
    static final String SECRET_TYPE = "death_note";

    enum Verdict {
        /** Not a chronicled legendary. */
        IGNORE,
        /** Valid; the item was not changed. */
        OK,
        /** Valid; the item was stamped and must be written back to where it was read from. */
        REWRITE,
        /** A copy; it has been recorded in quarantine and the caller must remove it. */
        QUARANTINE
    }

    /** Where an item was seen. {@code player} is the holder for PLAYER/ENDER_CHEST and the viewer otherwise. */
    record Sighting(LegendRecord.Holder kind, UUID player, UUID entity, Location loc) {
        String key() {
            return placeKey(this.kind, this.kind == LegendRecord.Holder.GROUND ? this.entity : this.player,
                    this.loc == null || this.loc.getWorld() == null ? null : this.loc.getWorld().getName(),
                    this.loc == null ? 0 : this.loc.getBlockX(), this.loc == null ? 0 : this.loc.getBlockY(),
                    this.loc == null ? 0 : this.loc.getBlockZ());
        }

        boolean personal() {
            return this.kind == LegendRecord.Holder.PLAYER || this.kind == LegendRecord.Holder.ENDER_CHEST;
        }
    }

    static String placeKey(LegendRecord.Holder kind, UUID id, String world, int x, int y, int z) {
        return switch (kind) {
            case PLAYER -> "P:" + id;
            case ENDER_CHEST -> "E:" + id;
            case GROUND -> "G:" + id;
            case VIRTUAL -> "V:" + id;
            case CONTAINER -> "C:" + world + ":" + x + ":" + y + ":" + z;
            case GRAVE -> "D:" + world + ":" + x + ":" + y + ":" + z;
            case NONE -> "";
        };
    }

    private record ExpectedCraft(UUID player, long until) {
    }

    final FokendLegendaryWepons plugin;
    final ChronicleDatabase db;
    final LegendStamp stamp;
    LongSupplier clock = System::currentTimeMillis;

    final Map<UUID, LegendRecord> byUid = new HashMap<>();
    final Map<String, LegendRecord> liveByType = new HashMap<>();
    /** Read by the async chat-free GUI code only on the server thread; concurrent maps for safety on reload. */
    final Map<UUID, String> names = new ConcurrentHashMap<>();
    final Map<UUID, String> ips = new ConcurrentHashMap<>();
    final Set<UUID> sightedSinceStart = new java.util.HashSet<>();
    private final Map<String, ExpectedCraft> expectedCrafts = new HashMap<>();
    private final Map<UUID, Long> lastPersist = new HashMap<>();
    private final Map<String, Long> recentAlerts = new HashMap<>();

    LegendRegistry(FokendLegendaryWepons plugin, ChronicleDatabase db) {
        this.plugin = plugin;
        this.db = db;
        this.stamp = new LegendStamp(plugin);
    }

    long now() {
        return this.clock.getAsLong();
    }

    static void requireMainThread() {
        if (!Bukkit.isPrimaryThread()) {
            throw new IllegalStateException("Legend Chronicle state must only be touched on the server thread");
        }
    }

    /** Loads records and the name/IP cache. Called once at enable; waiting here does not stall gameplay. */
    void load() throws Exception {
        this.byUid.clear();
        this.liveByType.clear();
        for (ChronicleDatabase.LegendRow row : this.db.loadLegends().get()) {
            LegendRecord rec = LegendRecord.fromRow(row);
            this.byUid.put(rec.uid, rec);
            if (rec.live()) {
                LegendRecord clash = this.liveByType.put(rec.type, rec);
                if (clash != null) {
                    // The unique index makes this impossible unless the file was edited by hand.
                    this.plugin.getLogger().severe("[Chronicle] two live records for " + rec.type + ": " + clash.uid + ", " + rec.uid);
                }
            }
        }
        for (ChronicleDatabase.PlayerRow p : this.db.loadPlayers().get().values()) {
            try {
                UUID id = UUID.fromString(p.uuid());
                this.names.put(id, p.name());
                if (p.ip() != null) {
                    this.ips.put(id, p.ip());
                }
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    // ------------------------------------------------------------------ queries

    LegendRecord live(String type) {
        return this.liveByType.get(type);
    }

    boolean isChronicled(ItemStack item) {
        String id = this.plugin.getId(item);
        return id != null && CHRONICLED.contains(id);
    }

    /** True if crafting a new instance of this type is allowed (no live instance exists). */
    boolean canCreate(String type) {
        return !CHRONICLED.contains(type) || !this.liveByType.containsKey(type);
    }

    String name(UUID id) {
        if (id == null) {
            return "—";
        }
        String n = this.names.get(id);
        if (n != null) {
            return n;
        }
        n = Bukkit.getOfflinePlayer(id).getName();
        return n != null ? n : id.toString().substring(0, 8);
    }

    // ------------------------------------------------------------------ sightings

    /**
     * Validates one sighting of an item and updates its record. Returns what the caller must do with the item.
     * Call {@link #quarantineDuplicate} instead for copies already known to be duplicates within one scan.
     */
    Verdict observe(ItemStack item, Sighting s) {
        requireMainThread();
        String type = this.plugin.getId(item);
        if (type == null || !CHRONICLED.contains(type)) {
            return Verdict.IGNORE;
        }
        UUID uid = this.stamp.uid(item);
        int gen = this.stamp.gen(item);
        if (uid == null) {
            return this.adoptOrQuarantine(item, type, s);
        }
        LegendRecord rec = this.byUid.get(uid);
        if (rec == null) {
            if (this.liveByType.get(type) == null) {
                // A stamped item the database does not know (e.g. the database file was lost): take it in as live.
                rec = this.newRecord(uid, type, Math.max(gen, 1), s);
                List<ChronicleDatabase.HistoryRow> hist = new ArrayList<>();
                hist.add(this.history(rec, null, rec.owner, TransferReason.ADOPTED, s.loc(), null));
                this.db.commit(List.of(rec.toRow()), hist);
                if (gen != rec.gen) {
                    this.stamp.stamp(item, rec.uid, rec.gen);
                    return Verdict.REWRITE;
                }
                return Verdict.OK;
            }
            return this.quarantine(item, type, uid, gen, "UNKNOWN_UID", s);
        }
        if (!rec.type.equals(type)) {
            return this.quarantine(item, type, uid, gen, "TYPE_MISMATCH", s);
        }
        if (rec.status == LegendRecord.Status.RETIRED) {
            return this.quarantine(item, type, uid, gen, "RETIRED", s);
        }
        if (gen < rec.gen) {
            return this.quarantine(item, type, uid, gen, "STALE_GENERATION", s);
        }
        if (gen > rec.gen) {
            return this.quarantine(item, type, uid, gen, "FUTURE_GENERATION", s);
        }
        if (!s.key().equals(rec.placeKey())) {
            Presence where = this.presence(rec, s);
            if (where == Presence.STILL_THERE) {
                return this.quarantine(item, type, uid, gen, "DUPLICATE", s);
            }
            if (where == Presence.UNVERIFIABLE) {
                this.alert("move:" + uid, "Летопись: " + this.title(rec) + " найдена у " + this.describe(s)
                        + ", хотя по записи была у " + this.name(rec.holderId) + " (оффлайн). Возможна копия — проверь /legends info "
                        + rec.type);
            }
        }
        this.sightedSinceStart.add(uid);
        this.apply(rec, item, s);
        return Verdict.OK;
    }

    enum Presence { GONE, STILL_THERE, UNVERIFIABLE }

    /** Is the item still at the record's last place (which differs from the current sighting)? */
    Presence presence(LegendRecord rec, Sighting s) {
        switch (rec.holder) {
            case PLAYER -> {
                Player p = rec.holderId == null ? null : Bukkit.getPlayer(rec.holderId);
                if (p == null) {
                    return Presence.UNVERIFIABLE;
                }
                if (this.holds(p.getInventory(), rec) || this.matches(p.getItemOnCursor(), rec)) {
                    return Presence.STILL_THERE;
                }
                InventoryView view = p.getOpenInventory();
                Inventory top = view == null ? null : view.getTopInventory();
                if (top != null && top.getType() == InventoryType.CRAFTING && this.holds(top, rec)) {
                    return Presence.STILL_THERE;
                }
                return Presence.GONE;
            }
            case ENDER_CHEST -> {
                Player p = rec.holderId == null ? null : Bukkit.getPlayer(rec.holderId);
                if (p == null) {
                    return Presence.UNVERIFIABLE;
                }
                return this.holds(p.getEnderChest(), rec) ? Presence.STILL_THERE : Presence.GONE;
            }
            case CONTAINER -> {
                World w = rec.world == null ? null : Bukkit.getWorld(rec.world);
                if (w == null || !w.isChunkLoaded(rec.x >> 4, rec.z >> 4)) {
                    // Nothing moves items out of an unloaded chunk; still, without seeing it we do not accuse.
                    return Presence.UNVERIFIABLE;
                }
                try {
                    Block b = w.getBlockAt(rec.x, rec.y, rec.z);
                    BlockState st = b.getState(false);
                    if (st instanceof Container c && this.holds(c.getInventory(), rec)) {
                        return Presence.STILL_THERE;
                    }
                } catch (RuntimeException ignored) {
                    return Presence.UNVERIFIABLE;
                }
                return Presence.GONE;
            }
            case GROUND -> {
                Entity e = rec.holderId == null ? null : Bukkit.getEntity(rec.holderId);
                if (e instanceof Item it && it.isValid() && !it.getUniqueId().equals(s.entity())
                        && this.matches(it.getItemStack(), rec)) {
                    return Presence.STILL_THERE;
                }
                return Presence.GONE;
            }
            default -> {
                return Presence.GONE;
            }
        }
    }

    private boolean matches(ItemStack item, LegendRecord rec) {
        return item != null && rec.uid.equals(this.stamp.uid(item));
    }

    private boolean holds(Inventory inv, LegendRecord rec) {
        for (ItemStack it : inv.getContents()) {
            if (this.matches(it, rec)) {
                return true;
            }
        }
        return false;
    }

    private Verdict adoptOrQuarantine(ItemStack item, String type, Sighting s) {
        if (this.liveByType.get(type) != null) {
            // An unstamped item of a type that already has a live instance: which one is real is not ours to judge.
            return this.quarantine(item, type, null, 0, "UNSTAMPED_COPY", s);
        }
        LegendRecord rec = this.newRecord(UUID.randomUUID(), type, 1, s);
        TransferReason reason = TransferReason.ADOPTED;
        ExpectedCraft craft = this.expectedCrafts.get(type);
        if (craft != null && craft.until() >= this.now() && s.personal() && craft.player().equals(s.player())) {
            reason = TransferReason.CRAFT;
            this.expectedCrafts.remove(type);
        }
        this.stamp.stamp(item, rec.uid, rec.gen);
        this.captureSnapshot(rec, item);
        List<ChronicleDatabase.HistoryRow> hist = new ArrayList<>();
        hist.add(this.history(rec, null, rec.owner, reason, s.loc(), null));
        this.db.commit(List.of(rec.toRow()), hist);
        this.sightedSinceStart.add(rec.uid);
        return Verdict.REWRITE;
    }

    private LegendRecord newRecord(UUID uid, String type, int gen, Sighting s) {
        LegendRecord rec = new LegendRecord(uid, type, gen);
        long now = this.now();
        rec.createdAt = now;
        rec.lastSeen = now;
        if (s != null) {
            this.place(rec, s);
            if (s.personal()) {
                rec.owner = s.player();
                rec.ownerSince = now;
            }
        }
        this.byUid.put(uid, rec);
        this.liveByType.put(type, rec);
        return rec;
    }

    private void place(LegendRecord rec, Sighting s) {
        rec.holder = s.kind();
        rec.holderId = s.kind() == LegendRecord.Holder.GROUND ? s.entity() : s.player();
        Location l = s.loc();
        if (l != null && l.getWorld() != null) {
            rec.world = l.getWorld().getName();
            rec.x = l.getBlockX();
            rec.y = l.getBlockY();
            rec.z = l.getBlockZ();
        }
    }

    private boolean captureSnapshot(LegendRecord rec, ItemStack item) {
        try {
            byte[] bytes = LegendStamp.serialize(item);
            int h = Arrays.hashCode(bytes);
            if (rec.snapshot == null || h != rec.snapshotHash) {
                rec.snapshot = bytes;
                rec.snapshotHash = h;
                return true;
            }
        } catch (RuntimeException e) {
            this.plugin.warnOnce("chronicle snapshot", e);
        }
        return false;
    }

    /** Valid sighting: move the record, transfer ownership if a new player has it. */
    private void apply(LegendRecord rec, ItemStack item, Sighting s) {
        long now = this.now();
        String prevKey = rec.placeKey();
        LegendRecord.Holder prevHolder = rec.holder;
        UUID prevHolderId = rec.holderId;
        boolean changed = !s.key().equals(prevKey);
        List<ChronicleDatabase.HistoryRow> hist = new ArrayList<>();

        if (s.personal()) {
            UUID who = s.player();
            if (rec.status == LegendRecord.Status.CALLED) {
                // Whoever picks up a called legendary ends the call; a stranger becomes its owner.
                rec.status = LegendRecord.Status.ACTIVE;
                rec.clearCall();
                changed = true;
                if (!who.equals(rec.owner)) {
                    UUID from = rec.owner != null ? rec.owner : rec.hintFrom;
                    if (who.equals(from)) {
                        from = null; // the former owner found it again: ownership resumes, nothing changed hands
                    }
                    hist.add(this.transfer(rec, from, who, TransferReason.LEGEND_CALL, s.loc()));
                }
            } else if (!who.equals(rec.owner)) {
                TransferReason reason = this.reasonFor(rec, who);
                hist.add(this.transfer(rec, rec.owner, who, reason, s.loc()));
                changed = true;
            }
            if (rec.hint != null) {
                rec.clearHint();
                changed = true;
            }
        } else if (changed && rec.hint == null
                && (prevHolder == LegendRecord.Holder.PLAYER || prevHolder == LegendRecord.Holder.ENDER_CHEST)) {
            // Left a player's hands: remember how, so the next taker's reason is right.
            rec.hint = s.kind() == LegendRecord.Holder.GROUND ? LegendRecord.Hint.DROP : LegendRecord.Hint.STORED;
            rec.hintFrom = prevHolderId;
            rec.hintActor = null;
            rec.hintAt = now;
        }

        this.place(rec, s);
        rec.lastSeen = now;
        changed |= this.captureSnapshot(rec, item);

        if (!hist.isEmpty()) {
            this.db.commit(List.of(rec.toRow()), hist);
            this.lastPersist.put(rec.uid, now);
        } else if (changed || now - this.lastPersist.getOrDefault(rec.uid, 0L) > 300_000L) {
            this.save(rec);
        }
    }

    private TransferReason reasonFor(LegendRecord rec, UUID taker) {
        if (rec.owner == null && rec.hint == null) {
            return TransferReason.ADOPTED;
        }
        if (rec.hint == null) {
            return TransferReason.OTHER;
        }
        return switch (rec.hint) {
            case DEATH -> taker.equals(rec.hintActor) ? TransferReason.KILL : TransferReason.LOOT;
            case DROP -> TransferReason.GIFT;
            case STORED -> TransferReason.CONTAINER;
            case CALL -> TransferReason.LEGEND_CALL;
            case REFUSE_NOTE -> TransferReason.REFUSE_NOTE;
        };
    }

    private ChronicleDatabase.HistoryRow transfer(LegendRecord rec, UUID from, UUID to, TransferReason reason, Location at) {
        String flag = null;
        if (from != null && to != null && !from.equals(to)
                && (reason == TransferReason.GIFT || reason == TransferReason.CONTAINER
                || reason == TransferReason.OTHER || reason == TransferReason.LOOT)) {
            String a = this.ips.get(from);
            String b = this.ips.get(to);
            if (a != null && a.equals(b)) {
                flag = "same_ip";
                this.alert("twin:" + rec.uid + ":" + to, "Летопись: " + this.title(rec) + " перешла от " + this.name(from)
                        + " к " + this.name(to) + " (" + reason.label + "), у них один IP. Возможен твинк.");
            }
        }
        rec.owner = to;
        rec.ownerSince = this.now();
        Player p = to == null ? null : Bukkit.getPlayer(to);
        if (p != null) {
            p.sendActionBar(Component.text("📜 Летопись: теперь ты хозяин — ", NamedTextColor.GOLD)
                    .append(Component.text(this.title(rec), NamedTextColor.YELLOW)));
        }
        return this.history(rec, from, to, reason, at, flag);
    }

    ChronicleDatabase.HistoryRow history(LegendRecord rec, UUID from, UUID to, TransferReason reason, Location at, String flag) {
        boolean has = at != null && at.getWorld() != null;
        return new ChronicleDatabase.HistoryRow(0, rec.uid.toString(), rec.type, from == null ? null : from.toString(),
                to == null ? null : to.toString(), reason.name(), this.now(), has ? at.getWorld().getName() : rec.world,
                has ? at.getBlockX() : rec.x, has ? at.getBlockY() : rec.y, has ? at.getBlockZ() : rec.z, flag);
    }

    void save(LegendRecord rec) {
        this.lastPersist.put(rec.uid, this.now());
        this.db.saveLegend(rec.toRow());
    }

    // ------------------------------------------------------------------ hints from other mechanics

    /** A player died with this item on them. Kept until someone picks it up, across restarts and graves. */
    void markDeath(ItemStack item, Player victim, Player killer) {
        LegendRecord rec = this.validRecord(item);
        if (rec == null) {
            return;
        }
        rec.hint = LegendRecord.Hint.DEATH;
        rec.hintFrom = victim.getUniqueId();
        rec.hintActor = killer != null && !killer.getUniqueId().equals(victim.getUniqueId()) ? killer.getUniqueId() : null;
        rec.hintAt = this.now();
        rec.holder = LegendRecord.Holder.GRAVE;
        rec.holderId = victim.getUniqueId();
        Location l = victim.getLocation();
        rec.world = l.getWorld() == null ? rec.world : l.getWorld().getName();
        rec.x = l.getBlockX();
        rec.y = l.getBlockY();
        rec.z = l.getBlockZ();
        this.save(rec);
    }

    /** /refusenote handed the note on; the next taker gets REFUSE_NOTE as the reason. */
    void markRefused(ItemStack note, UUID from) {
        LegendRecord rec = this.validRecord(note);
        if (rec == null) {
            return;
        }
        rec.hint = LegendRecord.Hint.REFUSE_NOTE;
        rec.hintFrom = from;
        rec.hintActor = null;
        rec.hintAt = this.now();
        this.save(rec);
    }

    /** The next unstamped item of this type that this player is seen with within 10 s was crafted by them. */
    void expectCraft(String type, UUID player) {
        this.expectedCrafts.put(type, new ExpectedCraft(player, this.now() + 10_000L));
    }

    LegendRecord validRecord(ItemStack item) {
        UUID uid = this.stamp.uid(item);
        LegendRecord rec = uid == null ? null : this.byUid.get(uid);
        if (rec == null || !rec.live() || rec.gen != this.stamp.gen(item)) {
            return null;
        }
        return rec;
    }

    // ------------------------------------------------------------------ creation, replacement

    /**
     * Registers an item an admin is about to give as the live instance of its type. If a live instance exists
     * it is retired first (its copies become stale and go to quarantine when seen); the caller must have had
     * that confirmed. Returns the stamped item.
     */
    ItemStack registerGiven(ItemStack item, Player target, UUID admin) {
        requireMainThread();
        String type = this.plugin.getId(item);
        List<ChronicleDatabase.LegendRow> rows = new ArrayList<>();
        List<ChronicleDatabase.HistoryRow> hist = new ArrayList<>();
        LegendRecord old = this.liveByType.get(type);
        if (old != null) {
            this.retireInto(old, rows, hist);
        }
        Sighting s = new Sighting(LegendRecord.Holder.PLAYER, target.getUniqueId(), null, target.getLocation());
        LegendRecord rec = this.newRecord(UUID.randomUUID(), type, 1, s);
        this.stamp.stamp(item, rec.uid, rec.gen);
        this.captureSnapshot(rec, item);
        rows.add(rec.toRow());
        hist.add(this.history(rec, admin, target.getUniqueId(), TransferReason.ADMIN_GIVE, target.getLocation(), null));
        this.db.commit(rows, hist);
        this.sightedSinceStart.add(rec.uid);
        return item;
    }

    CompletableFuture<Void> retire(LegendRecord rec) {
        requireMainThread();
        List<ChronicleDatabase.LegendRow> rows = new ArrayList<>();
        List<ChronicleDatabase.HistoryRow> hist = new ArrayList<>();
        this.retireInto(rec, rows, hist);
        return this.db.commit(rows, hist);
    }

    private void retireInto(LegendRecord rec, List<ChronicleDatabase.LegendRow> rows, List<ChronicleDatabase.HistoryRow> hist) {
        UUID prevOwner = rec.owner;
        rec.status = LegendRecord.Status.RETIRED;
        rec.gen += 1;
        rec.clearCall();
        rec.clearHint();
        rec.owner = null;
        this.liveByType.remove(rec.type, rec);
        rows.add(rec.toRow());
        hist.add(this.history(rec, prevOwner, null, TransferReason.RETIRED, null, null));
    }

    // ------------------------------------------------------------------ quarantine

    Verdict quarantineDuplicate(ItemStack item, Sighting s) {
        String type = this.plugin.getId(item);
        return this.quarantine(item, type, this.stamp.uid(item), this.stamp.gen(item), "DUPLICATE", s);
    }

    private Verdict quarantine(ItemStack item, String type, UUID uid, int gen, String reason, Sighting s) {
        byte[] bytes = LegendStamp.serialize(item);
        Location l = s.loc();
        boolean has = l != null && l.getWorld() != null;
        ChronicleDatabase.QuarantineRow row = new ChronicleDatabase.QuarantineRow(0, uid == null ? null : uid.toString(),
                type, gen, reason, this.now(), s.player() == null ? null : s.player().toString(), s.kind().name(),
                has ? l.getWorld().getName() : null, has ? l.getBlockX() : 0, has ? l.getBlockY() : 0,
                has ? l.getBlockZ() : 0, bytes, false);
        String where = this.describe(s);
        this.plugin.getLogger().warning("[Chronicle] quarantined " + type + " (" + reason + ", uid=" + uid + ", gen=" + gen + ") at " + where);
        this.db.quarantine(row).whenComplete((id, err) -> {
            boolean failed = err != null || id == null || id < 0;
            if (!this.plugin.isEnabled()) {
                // Shutting down: no scheduler. The fallback file write is safe from this thread.
                if (failed) {
                    this.emergencyKeep(row);
                }
                return;
            }
            this.plugin.getServer().getScheduler().runTask(this.plugin, () -> {
                if (failed) {
                    this.emergencyKeep(row);
                    return;
                }
                this.alert("q:" + id, "Летопись: копия " + this.typeTitle(type) + " (" + reasonText(reason) + ") у " + where
                        + " отправлена на проверку. /legends review → #" + id);
            });
        });
        if (s.player() != null && s.personal()) {
            Player p = Bukkit.getPlayer(s.player());
            if (p != null) {
                p.sendMessage(Component.text("⚠ " + this.typeTitle(type) + " отправлена на проверку администрации: "
                        + reasonText(reason) + ". Если это ошибка — напиши админу, предмет не удалён.", NamedTextColor.GOLD));
            }
        }
        return Verdict.QUARANTINE;
    }

    /** The database refused a quarantine entry: keep the item in a flat file so it is never lost. */
    private void emergencyKeep(ChronicleDatabase.QuarantineRow row) {
        try {
            java.io.File f = new java.io.File(this.plugin.getDataFolder(), "chronicle-quarantine-fallback.yml");
            YamlConfiguration y = YamlConfiguration.loadConfiguration(f);
            String key = "q" + row.foundAt() + "_" + Math.abs(Arrays.hashCode(row.item()));
            y.set(key + ".type", row.type());
            y.set(key + ".reason", row.reason());
            y.set(key + ".uid", row.uid());
            y.set(key + ".item", LegendStamp.deserialize(row.item()));
            y.save(f);
            this.plugin.getLogger().severe("[Chronicle] quarantine database write failed; item kept in " + f.getName() + " under " + key);
        } catch (Exception e) {
            this.plugin.getLogger().log(java.util.logging.Level.SEVERE,
                    "[Chronicle] could not store a quarantined item anywhere; its bytes (base64): "
                            + java.util.Base64.getEncoder().encodeToString(row.item()), e);
        }
    }

    /**
     * Approves a quarantined item: it becomes the live instance of its type and is given to {@code target}.
     * Any other instance of the type is retired and every other copy of this uid becomes stale.
     */
    ItemStack approve(ChronicleDatabase.QuarantineRow q, Player target, UUID admin) {
        requireMainThread();
        ItemStack item = LegendStamp.deserialize(q.item());
        if (item == null) {
            return null;
        }
        String type = this.plugin.getId(item);
        if (type == null || !CHRONICLED.contains(type)) {
            return item;
        }
        List<ChronicleDatabase.LegendRow> rows = new ArrayList<>();
        List<ChronicleDatabase.HistoryRow> hist = new ArrayList<>();
        UUID uid = this.stamp.uid(item);
        LegendRecord rec = uid == null ? null : this.byUid.get(uid);
        LegendRecord live = this.liveByType.get(type);
        if (live != null && live != rec) {
            this.retireInto(live, rows, hist);
        }
        UUID prevOwner;
        Sighting s = new Sighting(LegendRecord.Holder.PLAYER, target.getUniqueId(), null, target.getLocation());
        if (rec != null) {
            prevOwner = rec.owner;
            rec.status = LegendRecord.Status.ACTIVE;
            rec.clearCall();
            rec.clearHint();
            rec.gen += 1;
            this.liveByType.put(type, rec);
            this.place(rec, s);
        } else {
            prevOwner = null;
            rec = this.newRecord(uid != null ? uid : UUID.randomUUID(), type, 1, s);
        }
        rec.owner = target.getUniqueId();
        rec.ownerSince = this.now();
        this.stamp.stamp(item, rec.uid, rec.gen);
        this.captureSnapshot(rec, item);
        rows.add(rec.toRow());
        hist.add(this.history(rec, prevOwner, target.getUniqueId(), TransferReason.ADMIN_APPROVE, target.getLocation(),
                "quarantine#" + q.id() + " by " + this.name(admin)));
        this.db.commit(rows, hist);
        this.sightedSinceStart.add(rec.uid);
        return item;
    }

    // ------------------------------------------------------------------ messages

    void alert(String dedupKey, String message) {
        long now = this.now();
        Long last = this.recentAlerts.get(dedupKey);
        if (last != null && now - last < 600_000L) {
            return;
        }
        this.recentAlerts.put(dedupKey, now);
        this.plugin.getLogger().warning("[Chronicle] " + message);
        Component c = Component.text("⚠ " + message, NamedTextColor.GOLD);
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.hasPermission("legendary.admin")) {
                p.sendMessage(c);
            }
        }
    }

    String describe(Sighting s) {
        String place = switch (s.kind()) {
            case PLAYER -> "в инвентаре " + this.name(s.player());
            case ENDER_CHEST -> "в эндер-сундуке " + this.name(s.player());
            case CONTAINER -> "в контейнере";
            case GROUND -> "на земле";
            case VIRTUAL -> "в меню у " + this.name(s.player());
            case GRAVE -> "в могиле";
            case NONE -> "неизвестно где";
        };
        Location l = s.loc();
        if (l != null && l.getWorld() != null) {
            place += " (" + l.getWorld().getName() + " " + l.getBlockX() + " " + l.getBlockY() + " " + l.getBlockZ() + ")";
        }
        return place;
    }

    static String reasonText(String reason) {
        return switch (reason) {
            case "DUPLICATE" -> "та же легендарка уже есть в другом месте";
            case "STALE_GENERATION" -> "устаревшая копия (легенда ушла на Зов или была заменена)";
            case "FUTURE_GENERATION" -> "поколение новее записи";
            case "RETIRED" -> "экземпляр погашен";
            case "UNKNOWN_UID" -> "неизвестный экземпляр";
            case "UNSTAMPED_COPY" -> "неучтённая копия";
            case "TYPE_MISMATCH" -> "метка не совпадает с типом";
            default -> reason;
        };
    }

    String title(LegendRecord rec) {
        return this.typeTitle(rec.type);
    }

    String typeTitle(String type) {
        if (type == null) {
            return "легендарка";
        }
        return switch (type) {
            case FokendLegendaryWepons.ID_RA_BLADE -> "Клинок Ра";
            case FokendLegendaryWepons.ID_CHAOS_BOOMERANG -> "Бумеранг Хаоса";
            case FokendLegendaryWepons.ID_TWINS_SWORD -> "Меч Близнецов";
            case FokendLegendaryWepons.ID_WARDEN_HELMET -> "Шлем Хранителя";
            case FokendLegendaryWepons.ID_MAGNET -> "Магнитник";
            case FokendLegendaryWepons.ID_TIME_CLOCK -> "Часы «Хроносфера»";
            case FokendLegendaryWepons.ID_ICE_SPEAR -> "Морозное копьё";
            case FokendLegendaryWepons.ID_DEFAULT_MASK -> "Маска Хаоса";
            case FokendLegendaryWepons.ID_SOUL_BOW -> "Лук Душ";
            case "death_note" -> "Тетрадь Смерти";
            case "fate_scales" -> "Весы Судьбы";
            default -> type;
        };
    }

    /** Public name of an owner: hidden for the Death Note unless the viewer is an admin. */
    String publicOwner(String type, UUID owner, boolean admin) {
        if (owner == null) {
            return "—";
        }
        if (SECRET_TYPE.equals(type) && !admin) {
            return "???";
        }
        return this.name(owner);
    }
}
