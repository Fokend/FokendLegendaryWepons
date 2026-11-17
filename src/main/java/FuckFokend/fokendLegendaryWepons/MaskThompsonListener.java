package FuckFokend.fokendLegendaryWepons;

import org.bukkit.GameMode;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.entity.Snowball;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;

final class MaskThompsonListener implements Listener {
    private final FokendLegendaryWepons plugin;

    MaskThompsonListener(FokendLegendaryWepons plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        if (!this.plugin.isAbilityClick(e)) return;

        Player p = e.getPlayer();
        ItemStack held = p.getInventory().getItemInMainHand();
        if (!this.plugin.isLegendary(held, FokendLegendaryWepons.ID_MASK_TOMPSON)) return;
        if (!this.plugin.checkAndMarkTick(p, "mask_tompson")) return;

        e.setCancelled(true);
        if (this.plugin.onCooldown(p, "mask_tompson_shot", 250L)) return;
        this.plugin.cooldowns.put(p.getUniqueId() + ":mask_tompson_shot", System.currentTimeMillis());

        if (p.getGameMode() != GameMode.CREATIVE) {
            ItemMeta meta = held.getItemMeta();
            if (meta instanceof Damageable damageable) {
                short maxDurability = held.getType().getMaxDurability();
                int nextDamage = damageable.getDamage() + 10;
                if (maxDurability > 0 && nextDamage >= maxDurability) {
                    ItemStack brokenCopy = held.clone();
                    p.getInventory().setItemInMainHand(null);
                    p.getWorld().playSound(p.getLocation(), Sound.ENTITY_ITEM_BREAK, 1.0F, 1.0F);
                    p.getWorld().spawnParticle(
                            Particle.ITEM, p.getEyeLocation(), 15, 0.2D, 0.2D, 0.2D, 0.05D, brokenCopy);
                } else {
                    damageable.setDamage(nextDamage);
                    held.setItemMeta(damageable);
                }
            }
        }

        this.plugin.fireThompsonShot(p, p.getEyeLocation(), p.getEyeLocation().getDirection(), FokendLegendaryWepons.META_TOMPSON);
        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 0.4F, 2.0F);
    }

    @EventHandler
    public void onHit(ProjectileHitEvent e) {
        if (!(e.getEntity() instanceof Snowball sb)) return;
        boolean isThompson = sb.hasMetadata(FokendLegendaryWepons.META_TOMPSON);
        boolean isMaskThompson = sb.hasMetadata(FokendLegendaryWepons.META_MASK_EXPLOSIVE);
        if (!isThompson && !isMaskThompson) return;

        Player shooter = sb.getShooter() instanceof Player pl ? pl : null;
        if (isThompson) {
            this.plugin.explodeSmall(sb.getLocation(), shooter, 5.0D, 2.0D);
        } else {
            this.plugin.explodeSmall(sb.getLocation(), shooter, 4.0D, 2.5D);
        }
        this.plugin.removeTracked(sb);
    }
}
