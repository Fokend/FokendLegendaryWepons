package FuckFokend.fokendLegendaryWepons;

import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.entity.Snowball;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;

final class MaskThompsonListener implements Listener {
    private final FokendLegendaryWepons plugin;
    MaskThompsonListener(FokendLegendaryWepons plugin) { this.plugin = plugin; }

        @EventHandler
        public void onInteract(PlayerInteractEvent e) {
            if (!plugin.isAbilityClick(e)) return;
            Player p = e.getPlayer();
            ItemStack held = p.getInventory().getItemInMainHand();
            if (!plugin.isLegendary(held, plugin.ID_MASK_TOMPSON)) return;
            if (!plugin.checkAndMarkTick(p, "mask_tompson")) return;
            e.setCancelled(true);

            ItemMeta meta = held.getItemMeta();
            if (meta instanceof Damageable damageable) {
                int maxDurability = held.getType().getMaxDurability();
                int newDamage = damageable.getDamage() + 10;
                if (maxDurability > 0 && newDamage >= maxDurability) {
                    p.getInventory().setItemInMainHand(null);
                    p.getWorld().playSound(p.getLocation(), Sound.ENTITY_ITEM_BREAK, 1.0F, 1.0F);
                    p.getWorld().spawnParticle(Particle.ITEM, p.getEyeLocation(), 15, 0.2D, 0.2D, 0.2D, 0.05D, held);
                } else {
                    damageable.setDamage(newDamage);
                    held.setItemMeta(damageable);
                }
            }
            plugin.fireThompsonShot(p, p.getEyeLocation(), p.getEyeLocation().getDirection(), plugin.META_TOMPSON);
            p.getWorld().playSound(p.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 0.4F, 2.0F);
        }

        @EventHandler
        public void onHit(ProjectileHitEvent e) {
            if (!(e.getEntity() instanceof Snowball s)) return;
            boolean tompson = s.hasMetadata(plugin.META_TOMPSON);
            boolean mask = s.hasMetadata(plugin.META_MASK_EXPLOSIVE);
            if (!tompson && !mask) return;

            Player shooter = (s.getShooter() instanceof Player pl) ? pl : null;
            if (tompson) {
                plugin.explodeSmall(s.getLocation(), shooter, 5.0D, 2.0D);
            } else {
                plugin.explodeSmall(s.getLocation(), shooter, 4.0D, 2.5D);
            }
            plugin.removeTracked(s);
        }
    
}
