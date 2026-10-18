package FuckFokend.fokendLegendaryWepons;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;

final class TimeClockListener implements Listener {
    private final FokendLegendaryWepons plugin;
    TimeClockListener(FokendLegendaryWepons plugin) { this.plugin = plugin; }

        @EventHandler(priority = EventPriority.HIGH)
        public void onHit(EntityDamageByEntityEvent e) {
            if (!(e.getDamager() instanceof Player p)) return;
            if (!plugin.isLegendary(p.getInventory().getItemInMainHand(), plugin.ID_TIME_CLOCK)) return;
            e.setCancelled(true);
            if (e.getCause() == DamageCause.ENTITY_SWEEP_ATTACK) return;
            if (plugin.isLocked(p)) return;
            if (!plugin.isTargetable(e.getEntity(), p)) return;

            LivingEntity target = (LivingEntity) e.getEntity();
            if (plugin.timeSessions.containsKey(p.getUniqueId())) {
                plugin.sendPriorityActionBar(p, Component.text("Часы уже заняты. Верните время (ПКМ).", NamedTextColor.RED));
            } else if (plugin.timeByTarget.containsKey(target.getUniqueId()) || plugin.lockStates.containsKey(target.getUniqueId())) {
                plugin.sendPriorityActionBar(p, Component.text("Эта цель уже остановлена.", NamedTextColor.RED));
            } else if (plugin.onCooldown(p, "time_clock", 180000L)) {
                long sec = plugin.cooldownRemaining(p, "time_clock", 180000L) / 1000L + 1L;
                plugin.sendPriorityActionBar(p, Component.text("Перезарядка часов: " + sec + "с", NamedTextColor.RED));
            } else {
                plugin.startTimeSession(p, target);
            }
        }

        @EventHandler
        public void onInteract(PlayerInteractEvent e) {
            if (!plugin.isAbilityClick(e)) return;
            Player p = e.getPlayer();
            if (!plugin.isLegendary(p.getInventory().getItemInMainHand(), plugin.ID_TIME_CLOCK)) return;
            if (!plugin.checkAndMarkTick(p, "time_clock")) return;
            e.setCancelled(true);

            FokendLegendaryWepons.TimeSession s = plugin.timeSessions.get(p.getUniqueId());
            if (s == null) {
                plugin.sendPriorityActionBar(p, Component.text("ЛКМ по сущности, чтобы остановить её во времени.", NamedTextColor.GRAY));
            } else if (!s.frozen) {
                plugin.sendPriorityActionBar(p, Component.text("Идёт запись движений цели...", NamedTextColor.YELLOW));
            } else {
                plugin.endTimeSession(s, "Время возвращено на кадр " + (s.index + 1) + "/" + s.frames.size());
                p.getWorld().playSound(p.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 1.0F, 1.4F);
            }
        }

        @EventHandler
        public void onHeld(PlayerItemHeldEvent e) {
            Player p = e.getPlayer();
            FokendLegendaryWepons.TimeSession s = plugin.timeSessions.get(p.getUniqueId());
            if (s == null || !s.frozen) return;
            if (!plugin.isLegendary(p.getInventory().getItem(e.getPreviousSlot()), plugin.ID_TIME_CLOCK)) return;
            e.setCancelled(true);

            int diff = e.getNewSlot() - e.getPreviousSlot();
            if (diff > 4) {
                diff -= 9;
            } else if (diff < -4) {
                diff += 9;
            }
            if (diff != 0) {
                int step = p.isSneaking() ? 10 : 1;
                plugin.scrubTime(s, p, diff * step);
            }
        }
    
}
