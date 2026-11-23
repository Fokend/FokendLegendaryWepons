package FuckFokend.fokendLegendaryWepons;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

/**
 * Time Clock ability: records a target's position and health for 10 seconds, then freezes it and lets
 * the owner scrub back through the recording.
 */
final class TimeClockSessions {
    private final FokendLegendaryWepons plugin;
    /** By owner. */
    final Map<UUID, TimeSession> timeSessions = new ConcurrentHashMap<>();
    /** By target. */
    final Map<UUID, TimeSession> timeByTarget = new ConcurrentHashMap<>();
    /** Targets being teleported by the clock right now, so the freeze listener lets the teleport through. */
    final Set<UUID> timeTeleporting = ConcurrentHashMap.newKeySet();

    TimeClockSessions(FokendLegendaryWepons plugin) {
        this.plugin = plugin;
    }

    void startTimeSession(Player owner, LivingEntity target) {
        final UUID ownerId = owner.getUniqueId();
        final UUID targetId = target.getUniqueId();
        final TimeSession session = new TimeSession(ownerId, targetId);
        this.timeSessions.put(session.owner, session);
        this.timeByTarget.put(session.target, session);
        this.plugin.cooldowns.start(owner, "time_clock", "Часы времени");
        this.plugin.cooldowns.sendPriorityActionBar(owner, Component.text("Часы записывают движения цели: 10 секунд...", NamedTextColor.AQUA));
        owner.getWorld().playSound(owner.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 1.0F, 1.8F);

        session.recordTask = new BukkitRunnable() {
            @Override
            public void run() {
                Entity ent = TimeClockSessions.this.plugin.getServer().getEntity(targetId);
                Player pl = TimeClockSessions.this.plugin.getServer().getPlayer(ownerId);
                if (ent instanceof LivingEntity le && le.isValid() && !le.isDead() && pl != null && pl.isOnline()) {
                    session.frames.add(new TimeFrame(le.getLocation().clone(), le.getHealth()));
                    if (session.frames.size() >= 200) {
                        this.cancel();
                        TimeClockSessions.this.beginTimeFreeze(session, pl, le);
                    }
                    return;
                }
                TimeClockSessions.this.endTimeSession(session, "Запись прервана: цель потеряна.");
            }
        }.runTaskTimer(this.plugin, 0L, 1L);
    }

    void beginTimeFreeze(TimeSession session, Player owner, LivingEntity target) {
        if (!this.plugin.lockEntity(target, true)) {
            this.endTimeSession(session, "Не удалось остановить цель.");
        } else {
            session.frozen = true;
            session.index = session.frames.size() - 1;
            target.getWorld().spawnParticle(
                    Particle.END_ROD, target.getLocation().add(0.0D, 1.0D, 0.0D), 40, 0.4D, 0.8D, 0.4D, 0.05D);
            target.getWorld().playSound(target.getLocation(), Sound.BLOCK_BEACON_DEACTIVATE, 1.0F, 0.5F);
            if (owner != null && owner.isOnline()) {
                this.plugin.cooldowns.sendPriorityActionBar(
                        owner,
                        Component.text("Время остановлено! Колёсико: листать кадры, ПКМ: вернуть время.", NamedTextColor.AQUA));
            }
            session.timeoutTask = this.plugin.getServer().getScheduler().runTaskLater(
                    this.plugin, () -> this.endTimeSession(session, "Время цели возобновилось само."), 3600L);
        }
    }

    void applyTimeFrame(Entity target, TimeFrame frame) {
        if (target == null || frame == null || !target.isValid() || target.isDead()) return;
        UUID targetId = target.getUniqueId();
        if (target instanceof Player) {
            this.timeTeleporting.add(targetId);
        }
        boolean teleported;
        try {
            teleported = target.teleport(frame.location());
        } finally {
            this.timeTeleporting.remove(targetId);
        }
        if (teleported && target instanceof LivingEntity le && !le.isDead()) {
            AttributeInstance maxAttr = le.getAttribute(Attribute.MAX_HEALTH);
            double maxHp = maxAttr == null ? le.getHealth() : maxAttr.getValue();
            le.setHealth(Math.max(0.5D, Math.min(frame.health(), maxHp)));
        }
    }

    void endTimeSession(TimeSession session, String message) {
        if (session.recordTask != null) {
            session.recordTask.cancel();
        }
        if (session.timeoutTask != null) {
            session.timeoutTask.cancel();
        }
        this.timeSessions.remove(session.owner);
        this.timeByTarget.remove(session.target);
        if (session.frozen) {
            Entity target = this.plugin.getServer().getEntity(session.target);
            if (target != null && !session.frames.isEmpty()
                    && session.index >= 0 && session.index < session.frames.size()) {
                this.applyTimeFrame(target, session.frames.get(session.index));
            }
            this.plugin.unlockEntity(session.target);
            if (target != null) {
                target.getWorld().spawnParticle(
                        Particle.END_ROD, target.getLocation().add(0.0D, 1.0D, 0.0D), 25, 0.3D, 0.6D, 0.3D, 0.05D);
            }
        }
        session.frozen = false;
        Player owner = this.plugin.getServer().getPlayer(session.owner);
        if (owner != null && message != null) {
            this.plugin.cooldowns.sendPriorityActionBar(owner, Component.text(message, NamedTextColor.YELLOW));
        }
    }

    void scrubTime(TimeSession session, Player owner, int delta) {
        if (!session.frames.isEmpty()) {
            Entity target = this.plugin.getServer().getEntity(session.target);
            if (target != null && target.isValid() && !target.isDead()) {
                session.index = Math.max(0, Math.min(session.frames.size() - 1, session.index + delta));
                TimeFrame frame = session.frames.get(session.index);
                this.applyTimeFrame(target, frame);
                double secondsBack = (session.frames.size() - 1 - session.index) / 20.0D;
                this.plugin.cooldowns.sendPriorityActionBar(
                        owner,
                        Component.text(
                                String.format(
                                        Locale.US,
                                        "Кадр %d/%d (-%.2f с)",
                                        session.index + 1,
                                        session.frames.size(),
                                        secondsBack),
                                NamedTextColor.AQUA));
                owner.playSound(
                        owner.getLocation(),
                        Sound.BLOCK_NOTE_BLOCK_HAT,
                        0.5F,
                        1.0F + (float) session.index / (float) session.frames.size());
            } else {
                this.endTimeSession(session, "Цель потеряна.");
            }
        }
    }

    static final class TimeSession {
        final UUID owner;
        final UUID target;
        final List<TimeFrame> frames = new ArrayList<>();
        BukkitTask recordTask;
        BukkitTask timeoutTask;
        boolean frozen = false;
        int index = 0;

        TimeSession(UUID owner, UUID target) {
            this.owner = owner;
            this.target = target;
        }
    }

    static record TimeFrame(Location location, double health) {
    }
}
