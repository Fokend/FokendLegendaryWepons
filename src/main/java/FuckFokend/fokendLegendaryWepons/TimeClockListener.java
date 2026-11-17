package FuckFokend.fokendLegendaryWepons;

import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.EquipmentSlot;

final class TimeClockListener implements Listener {
    private final FokendLegendaryWepons plugin;

    TimeClockListener(FokendLegendaryWepons plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof Player p)) return;
        if (!this.plugin.isLegendary(p.getInventory().getItemInMainHand(), FokendLegendaryWepons.ID_TIME_CLOCK)) return;

        e.setCancelled(true);
        if (e.getCause() == DamageCause.ENTITY_SWEEP_ATTACK) return;
        if (this.plugin.isLocked(p)) return;
        if (!this.plugin.isTargetable(e.getEntity(), p)) return;

        LivingEntity target = (LivingEntity) e.getEntity();
        if (this.plugin.timeSessions.containsKey(p.getUniqueId())) {
            this.plugin.sendPriorityActionBar(
                    p, Component.text("Часы уже заняты. Верните время (ПКМ).", NamedTextColor.RED));
        } else if (this.plugin.timeByTarget.containsKey(target.getUniqueId())
                || this.plugin.lockStates.containsKey(target.getUniqueId())) {
            this.plugin.sendPriorityActionBar(
                    p, Component.text("Эта цель уже остановлена.", NamedTextColor.RED));
        } else if (this.plugin.onCooldown(p, "time_clock", 180000L)) {
            long sec = this.plugin.cooldownRemaining(p, "time_clock", 180000L) / 1000L + 1L;
            this.plugin.sendPriorityActionBar(
                    p, Component.text("Часы на перезарядке: " + sec + "с", NamedTextColor.RED));
        } else {
            this.plugin.startTimeSession(p, target);
        }
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        if (!this.plugin.isAbilityClick(e)) return;

        Player p = e.getPlayer();
        if (!this.plugin.isLegendary(p.getInventory().getItemInMainHand(), FokendLegendaryWepons.ID_TIME_CLOCK)) return;
        if (!this.plugin.checkAndMarkTick(p, "time_clock")) return;

        e.setCancelled(true);
        FokendLegendaryWepons.TimeSession session = this.plugin.timeSessions.get(p.getUniqueId());
        if (session == null) {
            this.plugin.sendPriorityActionBar(
                    p, Component.text("ЛКМ по сущности, чтобы остановить её во времени.", NamedTextColor.GRAY));
        } else if (!session.frozen) {
            this.plugin.sendPriorityActionBar(
                    p, Component.text("Идёт запись движений цели...", NamedTextColor.YELLOW));
        } else {
            int frameNum = session.index + 1;
            this.plugin.endTimeSession(
                    session, "Время запущено (кадр " + frameNum + "/" + session.frames.size() + ").");
            p.getWorld().playSound(p.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 1.0F, 1.4F);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onHeld(PlayerItemHeldEvent e) {
        Player p = e.getPlayer();
        FokendLegendaryWepons.TimeSession session = this.plugin.timeSessions.get(p.getUniqueId());
        if (session != null && session.frozen) {
            if (this.plugin.isLegendary(
                    p.getInventory().getItem(e.getPreviousSlot()), FokendLegendaryWepons.ID_TIME_CLOCK)) {
                e.setCancelled(true);
                int diff = e.getNewSlot() - e.getPreviousSlot();
                if (diff > 4) {
                    diff -= 9;
                } else if (diff < -4) {
                    diff += 9;
                }
                if (diff != 0) {
                    int step = p.isSneaking() ? 10 : 1;
                    this.plugin.scrubTime(session, p, diff * step);
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFrozenRegen(EntityRegainHealthEvent e) {
        FokendLegendaryWepons.TimeSession session = this.plugin.timeByTarget.get(e.getEntity().getUniqueId());
        if (session != null && session.frozen) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFrozenMove(PlayerMoveEvent e) {
        UUID id = e.getPlayer().getUniqueId();
        if (!this.plugin.timeTeleporting.contains(id)) {
            FokendLegendaryWepons.TimeSession session = this.plugin.timeByTarget.get(id);
            if (session != null && session.frozen) {
                Location from = e.getFrom();
                Location to = e.getTo();
                if (to != null && (from.getX() != to.getX() || from.getY() != to.getY() || from.getZ() != to.getZ())) {
                    Location locked = from.clone();
                    locked.setYaw(to.getYaw());
                    locked.setPitch(to.getPitch());
                    e.setTo(locked);
                }
            }
        }
    }
}
