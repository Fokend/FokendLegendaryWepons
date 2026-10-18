package FuckFokend.fokendLegendaryWepons;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Warden;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.util.Vector;
import org.bukkit.util.RayTraceResult;

final class WardenHelmetListener implements Listener {
    private final FokendLegendaryWepons plugin;
    WardenHelmetListener(FokendLegendaryWepons plugin) { this.plugin = plugin; }

        private static final long CD = 10000L;

        @EventHandler
        public void onTarget(EntityTargetLivingEntityEvent e) {
            if (!(e.getEntity() instanceof Warden)) return;
            if (e.getTarget() instanceof Player p && plugin.isLegendary(p.getInventory().getHelmet(), plugin.ID_WARDEN_HELMET)) {
                e.setCancelled(true);
            }
        }

        @EventHandler
        public void onInteract(PlayerInteractEvent e) {
            if (!plugin.isAbilityClick(e)) return;
            Player p = e.getPlayer();
            if (!p.isSneaking()) return;
            if (!plugin.isLegendary(p.getInventory().getHelmet(), plugin.ID_WARDEN_HELMET)) return;
            if (!plugin.checkAndMarkTick(p, "warden_boom")) return;

            if (plugin.onCooldown(p, "warden_boom", CD)) {
                long sec = plugin.cooldownRemaining(p, "warden_boom", CD) / 1000L + 1L;
                plugin.sendPriorityActionBar(p, Component.text("Sonic Boom: " + sec + "с", NamedTextColor.RED));
                return;
            }
            plugin.setCooldown(p, "warden_boom", CD, "Sonic Boom");
            e.setCancelled(true);

            Location eye = p.getEyeLocation();
            World w = p.getWorld();
            Vector dir = eye.getDirection().normalize();
            double range = 32.0D;
            RayTraceResult wall = w.rayTraceBlocks(eye, dir, range, FluidCollisionMode.NEVER, true);
            if (wall != null) range = Math.max(0.0D, wall.getHitPosition().distance(eye.toVector()) - 0.5D);
            for (double d = 1.0D; d <= range; d += 1.0D) {
                w.spawnParticle(Particle.SONIC_BOOM, eye.clone().add(dir.clone().multiply(d)), 1, 0.0D, 0.0D, 0.0D, 0.0D);
            }
            Set<UUID> hitTargets = new HashSet<>();
            for (double d = 1.0D; d <= range; d += 1.0D) {
                Location point = eye.clone().add(dir.clone().multiply(d));
                for (Entity ent : w.getNearbyEntities(point, 1.5D, 1.5D, 1.5D)) {
                    if (!plugin.isTargetable(ent, p) || !hitTargets.add(ent.getUniqueId())) continue;
                    LivingEntity le = (LivingEntity) ent;
                    plugin.trueDamage(le, 10.0D, p);
                    if (!plugin.lockStates.containsKey(le.getUniqueId())) {
                        le.setVelocity(dir.clone().multiply(2.2D).setY(0.5D));
                    }
                }
            }
            w.playSound(eye, Sound.ENTITY_WARDEN_SONIC_BOOM, 1.5F, 1.0F);
        }
    
}
