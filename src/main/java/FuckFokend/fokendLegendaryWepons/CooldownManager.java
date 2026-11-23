package FuckFokend.fokendLegendaryWepons;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

/**
 * Per-player ability cooldowns and the action-bar cooldown HUD.
 *
 * <p>Cooldowns are keyed by {@code "<uuid>:<ability>"}. Each cooldown stores only its start time;
 * the duration is passed in by the caller on every check, so a cooldown can be checked against
 * a different duration than it was set with (this is how the plugin has always behaved).
 */
final class CooldownManager {

    static final int BAR_LENGTH = 10;
    /** Entries older than this are dropped by the periodic cleanup, whatever their duration. */
    private static final long STALE_AFTER_MS = 600_000L;
    private static final long PRIORITY_MESSAGE_MS = 2_000L;

    private final Plugin plugin;
    private final Map<String, Long> startedAt = new ConcurrentHashMap<>();
    private final Map<String, HudEntry> hud = new ConcurrentHashMap<>();
    private final Map<UUID, String> selected = new ConcurrentHashMap<>();
    private final Map<UUID, Long> priorityUntil = new ConcurrentHashMap<>();

    private BukkitTask cleanupTask;
    private BukkitTask hudTask;

    CooldownManager(Plugin plugin) {
        this.plugin = plugin;
    }

    void startTasks() {
        this.cleanupTask = this.plugin.getServer().getScheduler()
                .runTaskTimer(this.plugin, this::purgeStale, 6000L, 6000L);
        this.hudTask = this.plugin.getServer().getScheduler()
                .runTaskTimer(this.plugin, this::tickHud, 0L, 3L);
    }

    void shutdown() {
        if (this.cleanupTask != null) this.cleanupTask.cancel();
        if (this.hudTask != null) this.hudTask.cancel();
        this.hud.clear();
        this.selected.clear();
        this.priorityUntil.clear();
    }

    // ---- cooldowns ----

    boolean isOnCooldown(Player p, String key, long durationMs) {
        return this.remaining(p, key, durationMs) > 0L;
    }

    long remaining(Player p, String key, long durationMs) {
        Long started = this.startedAt.get(fullKey(p.getUniqueId(), key));
        return started == null ? 0L : Math.max(0L, started + durationMs - System.currentTimeMillis());
    }

    /** Remaining time rounded up to whole seconds, for "wait N s" messages. */
    long remainingSeconds(Player p, String key, long durationMs) {
        return this.remaining(p, key, durationMs) / 1000L + 1L;
    }

    /** Starts a cooldown and shows it on the action-bar HUD. */
    void start(Player p, String key, long durationMs, String displayName) {
        String fullKey = fullKey(p.getUniqueId(), key);
        this.startedAt.put(fullKey, System.currentTimeMillis());
        this.hud.put(fullKey, new HudEntry(durationMs, displayName));
        this.selected.put(p.getUniqueId(), key);
    }

    /** Starts a cooldown without showing it on the HUD (e.g. fire-rate limits). */
    void startSilent(Player p, String key) {
        this.startedAt.put(fullKey(p.getUniqueId(), key), System.currentTimeMillis());
    }

    /** Forgets everything about a player (on quit). */
    void clear(UUID playerId) {
        String prefix = playerId + ":";
        this.startedAt.keySet().removeIf(k -> k.startsWith(prefix));
        this.hud.keySet().removeIf(k -> k.startsWith(prefix));
        this.selected.remove(playerId);
        this.priorityUntil.remove(playerId);
    }

    // ---- action bar ----

    /** Shows a message on the action bar and holds the cooldown HUD off it for a moment. */
    void sendPriorityActionBar(Player p, Component msg) {
        this.priorityUntil.put(p.getUniqueId(), System.currentTimeMillis() + PRIORITY_MESSAGE_MS);
        p.sendActionBar(msg);
    }

    private void purgeStale() {
        long now = System.currentTimeMillis();
        this.startedAt.entrySet().removeIf(e -> now - e.getValue() > STALE_AFTER_MS);
    }

    private void tickHud() {
        long now = System.currentTimeMillis();
        this.hud.entrySet().removeIf(entry -> {
            Long started = this.startedAt.get(entry.getKey());
            if (started == null) return true;
            if (now - started < entry.getValue().durationMs()) return false;
            this.playReadySound(entry.getKey());
            return true;
        });

        for (Player p : this.plugin.getServer().getOnlinePlayers()) {
            UUID id = p.getUniqueId();
            if (this.priorityUntil.getOrDefault(id, 0L) > now) continue;

            String prefix = id + ":";
            String current = this.selected.get(id);
            if (current != null && this.isHudActive(prefix + current, now)) {
                this.sendHud(p, current, now);
                continue;
            }
            this.selected.remove(id);
            String next = this.hud.keySet().stream()
                    .filter(k -> k.startsWith(prefix) && this.isHudActive(k, now))
                    .max((a, b) -> {
                        int cmp = Long.compare(this.startedAt.getOrDefault(a, 0L), this.startedAt.getOrDefault(b, 0L));
                        return cmp != 0 ? cmp : a.compareTo(b);
                    })
                    .map(k -> k.substring(prefix.length()))
                    .orElse(null);
            if (next != null) {
                this.selected.put(id, next);
                this.sendHud(p, next, now);
            }
        }
    }

    private void playReadySound(String fullKey) {
        int sep = fullKey.indexOf(':');
        if (sep <= 0) return;
        UUID id;
        try {
            id = UUID.fromString(fullKey.substring(0, sep));
        } catch (IllegalArgumentException e) {
            this.plugin.getLogger().warning("Malformed cooldown key: " + fullKey);
            return;
        }
        Player p = this.plugin.getServer().getPlayer(id);
        if (p != null && p.isOnline()) {
            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 0.25F, 1.65F);
        }
    }

    private boolean isHudActive(String fullKey, long now) {
        HudEntry entry = this.hud.get(fullKey);
        Long started = this.startedAt.get(fullKey);
        return entry != null && started != null && now - started < entry.durationMs();
    }

    private void sendHud(Player p, String key, long now) {
        String fullKey = fullKey(p.getUniqueId(), key);
        HudEntry entry = this.hud.get(fullKey);
        Long started = this.startedAt.get(fullKey);
        if (entry == null || started == null || entry.durationMs() <= 0L) return;
        long rem = Math.max(0L, entry.durationMs() - (now - started));
        if (rem == 0L) return;

        double ratio = Math.max(0.0D, Math.min(1.0D, (double) rem / (double) entry.durationMs()));
        int filled = (int) Math.round(ratio * BAR_LENGTH);
        Component msg = Component.text(
                        "⚔ " + entry.name() + "  " + String.format(Locale.US, "%.1fs ", rem / 1000.0D),
                        NamedTextColor.GRAY)
                .append(Component.text("▰".repeat(filled), NamedTextColor.AQUA))
                .append(Component.text("▱".repeat(BAR_LENGTH - filled), NamedTextColor.DARK_GRAY));
        p.sendActionBar(msg);
    }

    private static String fullKey(UUID playerId, String key) {
        return playerId + ":" + key;
    }

    private record HudEntry(long durationMs, String name) {
    }
}
