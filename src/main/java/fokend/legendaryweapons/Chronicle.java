package fokend.legendaryweapons;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.scheduler.BukkitTask;

/**
 * Legend Chronicle + Legend Call, wired together. If the database cannot be opened the plugin keeps working
 * without them (and says so loudly in the log).
 */
final class Chronicle implements Listener {
    final FokendLegendaryWepons plugin;
    final ChronicleDatabase db;
    final LegendRegistry registry;
    final ActivityTracker activity;
    final ChronicleTracker tracker;
    final LegendCall call;
    final LegendsMenu.Clicks menuClicks;
    private final List<BukkitTask> tasks = new ArrayList<>();

    private Chronicle(FokendLegendaryWepons plugin, ChronicleDatabase db) {
        this.plugin = plugin;
        this.db = db;
        this.registry = new LegendRegistry(plugin, db);
        this.activity = new ActivityTracker(db, this.registry::now);
        this.tracker = new ChronicleTracker(plugin, this.registry, this.activity);
        this.call = new LegendCall(plugin, this.registry, new Random());
        this.menuClicks = new LegendsMenu.Clicks(this);
    }

    static Chronicle start(FokendLegendaryWepons plugin) {
        if (!plugin.getConfig().getBoolean("chronicle.enabled", true)) {
            plugin.getLogger().info("Летопись легенд выключена в config.yml (chronicle.enabled: false).");
            return null;
        }
        ChronicleDatabase db = new ChronicleDatabase(new File(plugin.getDataFolder(), "chronicle.db"), plugin.getLogger());
        try {
            db.open().get(15, TimeUnit.SECONDS);
            Chronicle c = new Chronicle(plugin, db);
            // Startup only: a one-off wait for the data, never during gameplay.
            c.registry.load();
            c.call.installedAt = db.installedAt(c.registry.now()).get(15, TimeUnit.SECONDS);
            c.enable();
            return c;
        } catch (Exception e) {
            plugin.getLogger().log(java.util.logging.Level.SEVERE,
                    "Летопись легенд НЕ запущена (база chronicle.db недоступна). Легендарки работают, но без учёта владельцев и Зова.", e);
            db.close();
            return null;
        }
    }

    private void enable() {
        var pm = this.plugin.getServer().getPluginManager();
        pm.registerEvents(this.tracker, this.plugin);
        pm.registerEvents(this.menuClicks, this.plugin);
        pm.registerEvents(this, this.plugin);

        LegendsCommand exec = new LegendsCommand(this);
        PluginCommand cmd = this.plugin.getCommand("legends");
        if (cmd != null) {
            cmd.setExecutor(exec);
            cmd.setTabCompleter(exec);
        }

        var scheduler = this.plugin.getServer().getScheduler();
        long scanTicks = Math.max(20L, this.plugin.balance.ticks("chronicle.scan_interval_seconds"));
        this.tasks.add(scheduler.runTaskTimer(this.plugin, this.tracker::scanAll, scanTicks, scanTicks));
        this.tasks.add(scheduler.runTaskTimer(this.plugin, this.activity::touchAll, 20L * 60, 20L * 60 * 5));
        long callTicks = Math.max(20L * 60, this.plugin.balance.ticks("chronicle.call.check_interval_minutes") * 60);
        this.tasks.add(scheduler.runTaskTimer(this.plugin, this.call::tick, 20L * 60, callTicks));
        this.tasks.add(scheduler.runTaskLater(this.plugin, this.call::verifyReleased, 20L * 60));

        // /reload or a late enable: players already online.
        for (Player p : this.plugin.getServer().getOnlinePlayers()) {
            this.registry.names.put(p.getUniqueId(), p.getName());
            this.activity.join(p.getUniqueId());
            this.tracker.markDirty(p);
        }
        long live = this.registry.liveByType.size();
        this.plugin.getLogger().info("Летопись легенд: " + live + " живых легендарок в записях, Зов "
                + (this.call.enabled() ? "включён" : "выключен") + ".");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent e) {
        this.call.onJoin(e.getPlayer());
    }

    void close() {
        for (BukkitTask t : this.tasks) {
            t.cancel();
        }
        this.tasks.clear();
        HandlerList.unregisterAll(this.tracker);
        HandlerList.unregisterAll(this.menuClicks);
        HandlerList.unregisterAll(this);
        // Online players: record where they are (Legend Call uses it) and close their sessions.
        for (Player p : this.plugin.getServer().getOnlinePlayers()) {
            try {
                this.tracker.scanPlayer(p);
            } catch (RuntimeException ex) {
                this.plugin.warnOnce("chronicle final scan", ex);
            }
        }
        this.activity.closeAll();
        this.db.close();
    }
}
