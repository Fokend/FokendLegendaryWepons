package FuckFokend.fokendLegendaryWepons;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;
import org.bukkit.event.world.EntitiesLoadEvent;

final class FreezeListener implements Listener {
    private final FokendLegendaryWepons plugin;

    FreezeListener(FokendLegendaryWepons plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onAnyDamage(EntityDamageEvent e) {
        FokendLegendaryWepons.LockState st = plugin.lockStates.get(e.getEntity().getUniqueId());
        if (st != null && st.invulnerable()) {
            e.setCancelled(true);
            return;
        }
        if (e instanceof EntityDamageByEntityEvent be) {
            if (be.getDamager() instanceof Player atk && plugin.lockStates.containsKey(atk.getUniqueId())) {
                e.setCancelled(true);
                return;
            }
            if (be.getDamager() instanceof Projectile proj
                    && proj.getShooter() instanceof Player atk
                    && plugin.lockStates.containsKey(atk.getUniqueId())) {
                e.setCancelled(true);
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent e) {
        if (plugin.timeTeleporting.contains(e.getPlayer().getUniqueId())
                || !plugin.lockStates.containsKey(e.getPlayer().getUniqueId())) return;
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

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent e) {
        if (plugin.timeTeleporting.contains(e.getPlayer().getUniqueId())
                || !plugin.lockStates.containsKey(e.getPlayer().getUniqueId())) return;
        TeleportCause cause = e.getCause();
        if (cause == TeleportCause.ENDER_PEARL || cause == TeleportCause.CHORUS_FRUIT) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInteract(PlayerInteractEvent e) {
        if (plugin.lockStates.containsKey(e.getPlayer().getUniqueId())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onSwapHand(PlayerSwapHandItemsEvent e) {
        if (plugin.lockStates.containsKey(e.getPlayer().getUniqueId())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent e) {
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
