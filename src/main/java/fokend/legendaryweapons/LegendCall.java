package fokend.legendaryweapons;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.util.Vector;

/**
 * Legend Call: a legendary whose owner has been inactive becomes something others can look for.
 *
 * <p>Flow: candidates are picked on the server thread, the owner's activity is read off-thread, and the
 * decision is re-checked on the server thread (the owner may have joined in between) before anything
 * happens. An item that only exists in an offline player's inventory or ender chest cannot be found, so it is
 * released: the database first records a new generation (making the copy the owner still has stale), then a
 * copy is rebuilt from the last snapshot and dropped where the item was last seen. Items already in the world
 * (a container, the ground, a grave) are only announced.
 *
 * <p>The announced point is the real one plus an offset chosen once per call and stored, so repeating the
 * announcement never narrows the search down.
 */
final class LegendCall {
    private final FokendLegendaryWepons plugin;
    private final LegendRegistry registry;
    private final Random random;
    long installedAt;

    LegendCall(FokendLegendaryWepons plugin, LegendRegistry registry, Random random) {
        this.plugin = plugin;
        this.registry = registry;
        this.random = random;
    }

    // ------------------------------------------------------------------ settings

    boolean enabled() {
        return this.plugin.getConfig().getBoolean("chronicle.call.enabled", true);
    }

    long windowMillis() {
        return Math.round(this.plugin.balance.getDouble("chronicle.call.inactive_hours") * 3_600_000D);
    }

    long minActiveMillis() {
        return Math.round(this.plugin.balance.getDouble("chronicle.call.min_active_minutes") * 60_000D);
    }

    int radius() {
        return Math.max(1, this.plugin.balance.getInt("chronicle.call.radius"));
    }

    long repeatMillis() {
        return Math.round(this.plugin.balance.getDouble("chronicle.call.repeat_hours") * 3_600_000D);
    }

    int maxAnnouncements() {
        return Math.max(1, this.plugin.balance.getInt("chronicle.call.max_announcements"));
    }

    // ------------------------------------------------------------------ periodic check

    /** Runs on the server thread every few minutes. */
    void tick() {
        LegendRegistry.requireMainThread();
        long now = this.registry.now();
        for (LegendRecord rec : new ArrayList<>(this.registry.liveByType.values())) {
            if (rec.status != LegendRecord.Status.CALLED) {
                continue;
            }
            if (!rec.released && rec.owner != null && Bukkit.getPlayer(rec.owner) != null) {
                this.cancel(rec);
            } else if (rec.spawnPending) {
                this.spawnReleased(rec);
            } else if (rec.announces < this.maxAnnouncements() && now - rec.lastAnnounce >= this.repeatMillis()) {
                this.announce(rec);
            }
        }
        if (!this.enabled()) {
            return;
        }
        for (LegendRecord rec : new ArrayList<>(this.registry.liveByType.values())) {
            if (rec.status != LegendRecord.Status.ACTIVE || rec.owner == null || Bukkit.getPlayer(rec.owner) != null) {
                continue;
            }
            UUID owner = rec.owner;
            int gen = rec.gen;
            long since = now - this.windowMillis();
            this.registry.db.activity(owner.toString(), since, now).whenComplete((act, err) -> {
                if (err == null && act != null && this.plugin.isEnabled()) {
                    Bukkit.getScheduler().runTask(this.plugin, () -> this.evaluate(rec, owner, gen, act));
                }
            });
        }
    }

    /** True if, by this activity, the owner counts as gone. Pure, for tests. */
    boolean inactive(LegendRecord rec, ChronicleDatabase.Activity act, long now) {
        long window = this.windowMillis();
        // Nothing before the chronicle existed or before they got the item counts against them.
        long lastSeen = Math.max(act.lastSeen(), Math.max(rec.ownerSince, this.installedAt));
        if (lastSeen <= now - window) {
            return true;
        }
        // Brief logins (an alt kept "alive") do not count as activity once the window is fully covered.
        boolean windowCovered = rec.ownerSince <= now - window && this.installedAt <= now - window;
        return windowCovered && act.activeMillis() < this.minActiveMillis();
    }

    void evaluate(LegendRecord rec, UUID owner, int gen, ChronicleDatabase.Activity act) {
        // Re-check everything on the server thread: the owner may have joined, or the item moved, meanwhile.
        if (rec.status != LegendRecord.Status.ACTIVE || !owner.equals(rec.owner) || rec.gen != gen
                || Bukkit.getPlayer(owner) != null || this.registry.liveByType.get(rec.type) != rec) {
            return;
        }
        if (this.inactive(rec, act, this.registry.now())) {
            this.start(rec);
        }
    }

    // ------------------------------------------------------------------ call lifecycle

    static boolean needsRelease(LegendRecord rec) {
        return switch (rec.holder) {
            case PLAYER, ENDER_CHEST, VIRTUAL, NONE -> true;
            case CONTAINER, GROUND, GRAVE -> false;
        };
    }

    /** Starts a call now (also used by /legends call). */
    CompletableFuture<Void> start(LegendRecord rec) {
        LegendRegistry.requireMainThread();
        if (rec.status != LegendRecord.Status.ACTIVE) {
            return CompletableFuture.completedFuture(null);
        }
        long now = this.registry.now();
        int r = this.radius();
        UUID prevOwner = rec.owner;
        boolean release = needsRelease(rec);
        if (release && rec.snapshot == null) {
            this.registry.alert("call:nosnap:" + rec.uid, "Летопись: " + this.registry.title(rec)
                    + " должна уйти на Зов, но у неё нет снимка — нужна ручная проверка (/legends info " + rec.type + ").");
            return CompletableFuture.completedFuture(null);
        }
        rec.status = LegendRecord.Status.CALLED;
        rec.callDx = this.random.nextInt(2 * r + 1) - r;
        rec.callDz = this.random.nextInt(2 * r + 1) - r;
        rec.callStarted = now;
        rec.lastAnnounce = 0;
        rec.announces = 0;
        CompletableFuture<Void> done;
        if (release) {
            rec.gen += 1;
            rec.released = true;
            rec.spawnPending = true;
            rec.owner = null;
            rec.hint = LegendRecord.Hint.CALL;
            rec.hintFrom = prevOwner;
            rec.hintActor = null;
            rec.hintAt = now;
            if (LegendRegistry.SECRET_TYPE.equals(rec.type) && prevOwner != null && this.plugin.deathNote != null) {
                this.plugin.deathNote.forgetOwner(prevOwner);
            }
            List<ChronicleDatabase.HistoryRow> hist = List.of(
                    this.registry.history(rec, prevOwner, null, TransferReason.RELEASED, null, null));
            // The new generation is durable before the copy exists, so a crash can never leave two valid copies.
            done = this.registry.db.commit(List.of(rec.toRow()), hist);
            done.whenComplete((v, err) -> {
                if (err != null) {
                    this.plugin.getLogger().severe("[Chronicle] could not persist the release of " + rec.type
                            + "; it will be retried, the old copy stays valid until then");
                }
                if (this.plugin.isEnabled()) {
                    Bukkit.getScheduler().runTask(this.plugin, () -> {
                        if (err == null) {
                            this.spawnReleased(rec);
                        }
                    });
                }
            });
        } else {
            this.registry.save(rec);
            done = CompletableFuture.completedFuture(null);
        }
        this.announce(rec);
        return done;
    }

    void cancel(LegendRecord rec) {
        rec.status = LegendRecord.Status.ACTIVE;
        rec.clearCall();
        if (rec.hint == LegendRecord.Hint.CALL) {
            rec.clearHint();
        }
        this.registry.save(rec);
        Player owner = rec.owner == null ? null : Bukkit.getPlayer(rec.owner);
        if (owner != null) {
            owner.sendMessage(Component.text("📜 " + this.registry.title(rec) + " перестала звать: хозяин вернулся.", NamedTextColor.GOLD));
        }
    }

    /** The owner joined: a call that has not taken the item out of their hands yet is called off. */
    void onJoin(Player p) {
        for (LegendRecord rec : new ArrayList<>(this.registry.liveByType.values())) {
            if (rec.status == LegendRecord.Status.CALLED && !rec.released && p.getUniqueId().equals(rec.owner)) {
                this.cancel(rec);
            } else if (rec.status == LegendRecord.Status.CALLED && rec.released && p.getUniqueId().equals(rec.hintFrom)) {
                p.sendMessage(Component.text("📜 Пока тебя не было, " + this.registry.title(rec)
                        + " ушла на Зов и ищет нового хозяина. Найти её может любой — и ты тоже.", NamedTextColor.GOLD));
            }
        }
    }

    void spawnReleased(LegendRecord rec) {
        if (!rec.spawnPending || rec.status != LegendRecord.Status.CALLED) {
            return;
        }
        World w = rec.world == null ? null : Bukkit.getWorld(rec.world);
        if (w == null) {
            w = Bukkit.getWorlds().get(0);
            Location spawn = w.getSpawnLocation();
            rec.world = w.getName();
            rec.x = spawn.getBlockX();
            rec.y = spawn.getBlockY();
            rec.z = spawn.getBlockZ();
        }
        final World world = w;
        CompletableFuture<Chunk> chunk;
        try {
            chunk = world.getChunkAtAsync(rec.x >> 4, rec.z >> 4);
        } catch (RuntimeException unsupported) {
            chunk = CompletableFuture.completedFuture(world.getChunkAt(rec.x >> 4, rec.z >> 4));
        }
        chunk.whenComplete((c, err) -> {
            Runnable spawn = () -> {
                try {
                    this.dropReleased(rec, world);
                } catch (RuntimeException e) {
                    this.plugin.getLogger().log(java.util.logging.Level.SEVERE, "[Chronicle] release of " + rec.type + " failed", e);
                }
            };
            if (Bukkit.isPrimaryThread()) {
                spawn.run();
            } else if (this.plugin.isEnabled()) {
                Bukkit.getScheduler().runTask(this.plugin, spawn);
            }
        });
    }

    private void dropReleased(LegendRecord rec, World world) {
        if (!rec.spawnPending || rec.status != LegendRecord.Status.CALLED) {
            return;
        }
        ItemStack item = LegendStamp.deserialize(rec.snapshot);
        if (item == null) {
            this.registry.alert("call:badsnap:" + rec.uid, "Летопись: не удалось восстановить " + this.registry.title(rec)
                    + " из снимка для Зова — нужна ручная проверка.");
            return;
        }
        this.registry.stamp.stamp(item, rec.uid, rec.gen);
        if (LegendRegistry.SECRET_TYPE.equals(rec.type)) {
            ItemMeta meta = item.getItemMeta();
            meta.getPersistentDataContainer().remove(new NamespacedKey(this.plugin, "dn_owner"));
            item.setItemMeta(meta);
        }
        Location spot = safeSpot(world, rec.x, rec.y, rec.z);
        // Before dropping: the spawn event re-enters the registry, which must not think the old holder still has it.
        rec.spawnPending = false;
        rec.holder = LegendRecord.Holder.NONE;
        rec.holderId = null;
        Item dropped;
        try {
            dropped = world.dropItem(spot, item);
        } catch (RuntimeException e) {
            // The entity may or may not exist now. Retry under a new generation, so a half-made copy is stale.
            rec.gen += 1;
            rec.spawnPending = true;
            this.registry.save(rec);
            this.plugin.getLogger().log(java.util.logging.Level.SEVERE, "[Chronicle] could not drop released " + rec.type
                    + "; retrying on the next check", e);
            return;
        }
        rec.holder = LegendRecord.Holder.GROUND;
        rec.holderId = dropped.getUniqueId();
        rec.world = world.getName();
        rec.x = spot.getBlockX();
        rec.y = spot.getBlockY();
        rec.z = spot.getBlockZ();
        this.registry.sightedSinceStart.add(rec.uid);
        this.registry.save(rec);
        try {
            dropped.setVelocity(new Vector(0, 0, 0));
            this.plugin.protectDroppedItem(dropped);
        } catch (RuntimeException e) {
            this.plugin.warnOnce("chronicle protect released item", e);
        }
        this.plugin.getLogger().info("[Chronicle] " + rec.type + " released by Legend Call at "
                + world.getName() + " " + rec.x + " " + rec.y + " " + rec.z);
    }

    static Location safeSpot(World w, int x, int y, int z) {
        try {
            return findSpot(w, x, y, z);
        } catch (RuntimeException e) {
            return new Location(w, x + 0.5, y + 1.0, z + 0.5);
        }
    }

    private static Location findSpot(World w, int x, int y, int z) {
        int min = w.getMinHeight();
        int max = w.getMaxHeight() - 2;
        if (y > min && y < max) {
            Block feet = w.getBlockAt(x, y, z);
            Block head = feet.getRelative(0, 1, 0);
            if (feet.isPassable() && head.isPassable() && !feet.isLiquid()) {
                return new Location(w, x + 0.5, y + 0.2, z + 0.5);
            }
        }
        int top = w.getHighestBlockYAt(x, z);
        if (top <= min) {
            return w.getSpawnLocation().add(0.5, 1, 0.5);
        }
        return new Location(w, x + 0.5, top + 1.2, z + 0.5);
    }

    /** After a restart: a released item that has not shown up although its chunk was loaded is dropped again. */
    void verifyReleased() {
        for (LegendRecord rec : new ArrayList<>(this.registry.liveByType.values())) {
            if (rec.status != LegendRecord.Status.CALLED || !rec.released || rec.spawnPending
                    || this.registry.sightedSinceStart.contains(rec.uid)) {
                continue;
            }
            World w = rec.world == null ? null : Bukkit.getWorld(rec.world);
            if (w == null) {
                continue;
            }
            CompletableFuture<Chunk> chunk;
            try {
                chunk = w.getChunkAtAsync(rec.x >> 4, rec.z >> 4);
            } catch (RuntimeException unsupported) {
                chunk = CompletableFuture.completedFuture(w.getChunkAt(rec.x >> 4, rec.z >> 4));
            }
            int gen = rec.gen;
            chunk.whenComplete((c, err) -> Bukkit.getScheduler().runTaskLater(this.plugin, () -> {
                if (rec.gen != gen || rec.status != LegendRecord.Status.CALLED || this.registry.sightedSinceStart.contains(rec.uid)) {
                    return;
                }
                if (rec.holderId != null && Bukkit.getEntity(rec.holderId) instanceof Item it && it.isValid()) {
                    this.registry.sightedSinceStart.add(rec.uid);
                    return;
                }
                this.plugin.getLogger().warning("[Chronicle] released " + rec.type + " is missing from its chunk; dropping it again");
                rec.gen += 1;
                rec.spawnPending = true;
                this.registry.db.commit(List.of(rec.toRow()), List.of()).whenComplete((v, e2) -> {
                    if (e2 == null && this.plugin.isEnabled()) {
                        Bukkit.getScheduler().runTask(this.plugin, () -> this.spawnReleased(rec));
                    }
                });
            }, 60L));
        }
    }

    // ------------------------------------------------------------------ announcements

    static int round10(int v) {
        return Math.round(v / 10.0F) * 10;
    }

    /** The point shown to players. Within {@code radius + 5} blocks of the real spot on each axis. */
    int[] shownPoint(LegendRecord rec) {
        return new int[]{round10(rec.x + rec.callDx), round10(rec.z + rec.callDz)};
    }

    static String worldTitle(String name) {
        World w = name == null ? null : Bukkit.getWorld(name);
        if (w == null) {
            return name == null ? "?" : name;
        }
        return switch (w.getEnvironment()) {
            case NETHER -> "Незер";
            case THE_END -> "Энд";
            default -> "Верхний мир";
        };
    }

    void announce(LegendRecord rec) {
        int[] p = this.shownPoint(rec);
        UUID former = rec.released ? rec.hintFrom : rec.owner;
        boolean secret = LegendRegistry.SECRET_TYPE.equals(rec.type);
        Component head = Component.text(rec.announces == 0 ? "⚔ Легенда зовёт! " : "⚔ Легенда всё ещё зовёт! ", NamedTextColor.GOLD)
                .decoration(TextDecoration.BOLD, true);
        String who = secret || former == null ? "Прежний хозяин пропал." : "Прежний хозяин, " + this.registry.name(former) + ", пропал.";
        Component body = Component.text(this.registry.title(rec) + " ищет нового хозяина. " + who, NamedTextColor.YELLOW)
                .append(Component.newline())
                .append(Component.text("Ищите около X≈" + p[0] + ", Z≈" + p[1] + " (" + worldTitle(rec.world)
                        + "), в пределах ~" + this.radius() + " блоков.", NamedTextColor.GRAY));
        Component msg = head.append(body.decoration(TextDecoration.BOLD, false));
        for (Player pl : Bukkit.getOnlinePlayers()) {
            pl.sendMessage(msg);
            pl.playSound(pl.getLocation(), Sound.BLOCK_BELL_RESONATE, 0.7F, 0.8F);
        }
        rec.lastAnnounce = this.registry.now();
        rec.announces += 1;
        this.registry.save(rec);
    }
}
