package FuckFokend.fokendLegendaryWepons;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.world.EntitiesLoadEvent;

final class FreezeListener implements Listener {
    private final FokendLegendaryWepons plugin;
    FreezeListener(FokendLegendaryWepons plugin) { this.plugin = plugin; }

        @EventHandler(priority = EventPriority.HIGHEST)
        public void onAnyDamage(EntityDamageEvent e) {
            FokendLegendaryWepons.LockState st = plugin.lockStates.get(e.getEntity().getUniqueId());
            if (st != null && st.invulnerable()) {
                e.setCancelled(true);
                return;
            }
            if (e instanceof EntityDamageByEntityEvent be && be.getDamager() instanceof Player atk
                    && plugin.lockStates.containsKey(atk.getUniqueId())) {
                e.setCancelled(true);
            }
        }

        @EventHandler(ignoreCancelled = true)
        public void onMove(PlayerMoveEvent e) {
            if (!plugin.lockStates.containsKey(e.getPlayer().getUniqueId())) return;
            Location from = e.getFrom();
            Location to = e.getTo();
            if (to == null) return;
            if (from.getX() != to.getX() || from.getY() != to.getY() || from.getZ() != to.getZ()) {
                Location nt = from.clone();
                nt.setYaw(to.getYaw());
                nt.setPitch(to.getPitch());
                e.setTo(nt);
            }
        }

        @EventHandler
        public void onInteract(PlayerInteractEvent e) {
            if (plugin.lockStates.containsKey(e.getPlayer().getUniqueId())) {
                e.setCancelled(true);
            }
        }

        @EventHandler
        public void onEntitiesLoad(EntitiesLoadEvent e) {
            for (Entity ent : e.getEntities()) {
                plugin.restoreStaleFrozen(ent);
            }
        }
    
}
