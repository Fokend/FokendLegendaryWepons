package FuckFokend.fokendLegendaryWepons;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;
import org.joml.Vector3f;

final class IceSpearListener implements Listener {
    private final FokendLegendaryWepons plugin;
    IceSpearListener(FokendLegendaryWepons plugin) { this.plugin = plugin; }

        private static final long CD = 75000L;
        private static final long FREEZE_HIT_CD = 6000L;
        private static final double RADIUS = 15.0D;

        @EventHandler
        public void onInteract(PlayerInteractEvent e) {
            if (!plugin.isAbilityClick(e)) return;
            Player p = e.getPlayer();
            if (!p.isSneaking()) return;
            if (!plugin.isLegendary(p.getInventory().getItemInMainHand(), plugin.ID_ICE_SPEAR)) return;
            if (p.getInventory().getHeldItemSlot() != 0) return;
            if (!plugin.checkAndMarkTick(p, "ice_spear")) return;

            if (plugin.onCooldown(p, "ice_spear", CD)) {
                long sec = plugin.cooldownRemaining(p, "ice_spear", CD) / 1000L + 1L;
                plugin.sendPriorityActionBar(p, Component.text("Морозные пики: " + sec + "с", NamedTextColor.RED));
                return;
            }
            plugin.setCooldown(p, "ice_spear", CD, "Морозные пики");
            e.setCancelled(true);

            Location center = p.getLocation();
            World w = p.getWorld();
            for (Entity ent : w.getNearbyEntities(center, RADIUS, 8.0D, RADIUS)) {
                if (!plugin.isTargetable(ent, p)) continue;
                LivingEntity le = (LivingEntity) ent;
                double dx = le.getLocation().getX() - center.getX();
                double dz = le.getLocation().getZ() - center.getZ();
                if (dx * dx + dz * dz > RADIUS * RADIUS) continue;
                plugin.spawnSpike(le.getLocation(), 1.6F);
                le.damage(4.0D, p);
                Vector v = le.getVelocity();
                le.setVelocity(new Vector(v.getX(), 0.8D, v.getZ()));
            }
            for (int i = 0; i < 25; i++) {
                double a = Math.random() * Math.PI * 2.0D;
                double r = 2.0D + Math.random() * 13.0D;
                plugin.spawnSpike(center.clone().add(Math.cos(a) * r, 0.0D, Math.sin(a) * r), 1.2F + (float) Math.random() * 0.8F);
            }
            w.playSound(center, Sound.BLOCK_GLASS_BREAK, 1.2F, 0.6F);
            w.playSound(center, Sound.BLOCK_POWDER_SNOW_BREAK, 1.2F, 0.6F);
        }

        @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
        public void onHit(EntityDamageByEntityEvent e) {
            if (!(e.getDamager() instanceof Player p)) return;
            if (e.getCause() == DamageCause.ENTITY_SWEEP_ATTACK) return;
            if (p.getInventory().getHeldItemSlot() != 1) return;
            if (!plugin.isLegendary(p.getInventory().getItemInMainHand(), plugin.ID_ICE_SPEAR)) return;
            if (!plugin.isTargetable(e.getEntity(), p)) return;

            final LivingEntity victim = (LivingEntity) e.getEntity();
            if (plugin.lockStates.containsKey(victim.getUniqueId())) return;
            if (plugin.onCooldown(p, "ice_spear_freeze", FREEZE_HIT_CD)) return;
            plugin.cooldowns.put(p.getUniqueId() + ":ice_spear_freeze", System.currentTimeMillis());

            if (!plugin.lockEntity(victim, false)) return;
            Location iceLoc = victim.getLocation().clone();
            iceLoc.setYaw(0.0F);
            iceLoc.setPitch(0.0F);
            final ItemDisplay iceModel = plugin.spawnModel(
                    iceLoc.clone().add(0.0D, 1.0D, 0.0D),
                    plugin.iceSpearIce(),
                    new Vector3f(0.0F, 0.0F, 0.0F),
                    new Vector3f(1.5F, 1.5F, 1.5F)
            );
            victim.getWorld().playSound(iceLoc, Sound.BLOCK_GLASS_PLACE, 1.0F, 0.8F);
            victim.getWorld().spawnParticle(Particle.SNOWFLAKE, iceLoc.clone().add(0.0D, 1.0D, 0.0D), 25, 0.4D, 0.6D, 0.4D, 0.03D);

            new BukkitRunnable() {
                int ticks = 0;

                @Override
                public void run() {
                    ticks++;
                    if (!victim.isValid() || victim.isDead() || ticks >= 60) {
                        plugin.unlockEntity(victim.getUniqueId());
                        if (iceModel.isValid()) {
                            iceModel.getWorld().playSound(iceModel.getLocation(), Sound.BLOCK_GLASS_BREAK, 1.0F, 1.2F);
                            iceModel.getWorld().spawnParticle(Particle.SNOWFLAKE, iceModel.getLocation(), 20, 0.4D, 0.5D, 0.4D, 0.05D);
                        }
                        plugin.removeTracked(iceModel);
                        cancel();
                    } else if (iceModel.isValid()) {
                        iceModel.teleport(victim.getLocation().add(0.0D, 1.0D, 0.0D));
                    }
                }
            }.runTaskTimer(plugin, 1L, 1L);
        }
    
}
