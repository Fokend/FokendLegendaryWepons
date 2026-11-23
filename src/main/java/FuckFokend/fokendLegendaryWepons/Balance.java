package FuckFokend.fokendLegendaryWepons;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.configuration.Configuration;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Balance numbers from config.yml. Values are read on every call, so {@link #reload()} takes effect
 * immediately. A key missing from the server's config.yml falls back to the copy bundled in the jar.
 */
final class Balance {
    private final JavaPlugin plugin;
    private final Set<String> reportedBadValues = ConcurrentHashMap.newKeySet();

    Balance(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    void reload() {
        this.reportedBadValues.clear();
        this.plugin.saveDefaultConfig();
        this.plugin.reloadConfig();
    }

    long cooldownMs(String key) {
        return Math.round(this.number("cooldowns." + key) * 1000.0D);
    }

    double coreUpgradeChance(String coreId) {
        String path = "core_upgrade_chance." + coreId;
        return this.cfg().contains(path) ? this.cfg().getDouble(path) : 0.0D;
    }

    double getDouble(String path) {
        return this.number(path);
    }

    int getInt(String path) {
        return (int) Math.round(this.number(path));
    }

    long seconds(String path) {
        return Math.round(this.number(path) * 1000.0D);
    }

    long ticks(String path) {
        return Math.round(this.number(path) * 20.0D);
    }

    private double number(String path) {
        Configuration cfg = this.cfg();
        Object value = cfg.get(path);
        if (value instanceof Number n) {
            return n.doubleValue();
        }
        Configuration defaults = cfg.getDefaults();
        Object fallback = defaults != null ? defaults.get(path) : null;
        if (fallback instanceof Number n) {
            if (this.reportedBadValues.add(path)) {
                this.plugin.getLogger().warning("config.yml: '" + path + "' is not a number (" + value
                        + "), using the default " + n);
            }
            return n.doubleValue();
        }
        // The bundled config.yml has no such key: the code asked for something that does not exist.
        throw new IllegalStateException("config.yml has no number at '" + path + "'");
    }

    private Configuration cfg() {
        return this.plugin.getConfig();
    }
}
