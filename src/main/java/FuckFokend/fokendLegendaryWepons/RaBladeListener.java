package FuckFokend.fokendLegendaryWepons;

import java.util.Locale;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.scheduler.BukkitRunnable;

final class RaBladeListener implements Listener {
    private final FokendLegendaryWepons plugin;
    RaBladeListener(FokendLegendaryWepons plugin) { this.plugin = plugin; }

        private static final long DOLL_LIFETIME_TICKS = 900L;
        private static final long CD = 60000L;
        private boolean reflecting = false;

        @EventHandler
        public void onInteract(PlayerInteractEvent e) {
            if (!plugin.isAbilityClick(e)) return;
            final Player p = e.getPlayer();
            if (!plugin.isLegendary(p.getInventory().getItemInMainHand(), plugin.ID_RA_BLADE)) return;
            if (!plugin.checkAndMarkTick(p, "ra_blade")) return;
            e.setCancelled(true);

            UUID existing = plugin.raDolls.remove(p.getUniqueId());
            if (existing != null) {
                Entity e2 = plugin.getServer().getEntity(existing);
                if (e2 != null) {
                    e2.getWorld().spawnParticle(Particle.POOF, e2.getLocation().add(0.0D, 1.0D, 0.0D), 15, 0.2D, 0.4D, 0.2D, 0.05D);
                    e2.remove();
                }
                plugin.sendPriorityActionBar(p, Component.text("Манекен Ра отозван", NamedTextColor.YELLOW));
                return;
            }
            if (plugin.onCooldown(p, "ra_blade_spawn", CD)) {
                long sec = plugin.cooldownRemaining(p, "ra_blade_spawn", CD) / 1000L + 1L;
                plugin.sendPriorityActionBar(p, Component.text("Призыв манекена на перезарядке: " + sec + "с", NamedTextColor.RED));
                return;
            }
            plugin.setCooldown(p, "ra_blade_spawn", CD, "Призыв манекена Ра");

            Location loc = p.getLocation().add(plugin.flatDir(p.getLocation().getYaw()).multiply(1.8D));
            loc.setYaw(p.getLocation().getYaw() + 180.0F);
            loc.setPitch(0.0F);
            final ArmorStand doll = p.getWorld().spawn(loc, ArmorStand.class, as -> {
                as.setArms(true);
                as.setBasePlate(true);
                as.setGravity(false);
                as.setCanPickupItems(false);
                as.setRemoveWhenFarAway(false);
                as.setPersistent(false);
                as.customName(Component.text("Манекен Бога Ра", NamedTextColor.GOLD, TextDecoration.BOLD));
                as.setCustomNameVisible(true);
                as.setMetadata(plugin.META_RA_DOLL, new FixedMetadataValue(plugin, p.getUniqueId().toString()));
                for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HAND, EquipmentSlot.OFF_HAND,
                        EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
                    try {
                        as.addEquipmentLock(slot, ArmorStand.LockType.REMOVING_OR_CHANGING);
                    } catch (Exception ignored) {
                    }
                }
                if (as.getEquipment() != null) {
                    as.getEquipment().setHelmet(new ItemStack(Material.GOLDEN_HELMET));
                    as.getEquipment().setChestplate(new ItemStack(Material.GOLDEN_CHESTPLATE));
                    as.getEquipment().setLeggings(new ItemStack(Material.GOLDEN_LEGGINGS));
                    as.getEquipment().setBoots(new ItemStack(Material.GOLDEN_BOOTS));
                    as.getEquipment().setItemInMainHand(plugin.raBlade());
                }
            });
            plugin.raDolls.put(p.getUniqueId(), doll.getUniqueId());
            plugin.sendPriorityActionBar(p, Component.text("Манекен Бога Ра призван на 45 сек!", NamedTextColor.GOLD));
            p.getWorld().playSound(loc, Sound.BLOCK_BEACON_ACTIVATE, 1.0F, 1.5F);

            new BukkitRunnable() {
                @Override
                public void run() {
                    UUID activeId = plugin.raDolls.get(p.getUniqueId());
                    if (activeId != null && activeId.equals(doll.getUniqueId())) {
                        plugin.raDolls.remove(p.getUniqueId());
                        if (doll.isValid()) {
                            doll.getWorld().spawnParticle(Particle.POOF, doll.getLocation().add(0.0D, 1.0D, 0.0D), 20, 0.2D, 0.5D, 0.2D, 0.05D);
                            doll.getWorld().playSound(doll.getLocation(), Sound.BLOCK_BEACON_DEACTIVATE, 1.0F, 1.2F);
                            doll.remove();
                        }
                        if (p.isOnline()) {
                            plugin.sendPriorityActionBar(p, Component.text("Время действия манекена Ра истекло!", NamedTextColor.GRAY));
                        }
                    }
                }
            }.runTaskLater(plugin, DOLL_LIFETIME_TICKS);
        }

        @EventHandler
        public void onArmorStandManipulate(PlayerArmorStandManipulateEvent e) {
            if (e.getRightClicked().hasMetadata(plugin.META_RA_DOLL)) {
                e.setCancelled(true);
            }
        }

        @EventHandler(priority = EventPriority.HIGH)
        public void onDollDamage(EntityDamageEvent e) {
            Entity victim = e.getEntity();
            if (!victim.hasMetadata(plugin.META_RA_DOLL)) return;
            e.setCancelled(true);
            if (reflecting) return;
            if (!(e instanceof EntityDamageByEntityEvent be)) return;

            Player attacker = null;
            if (be.getDamager() instanceof Player pl) {
                attacker = pl;
            } else if (be.getDamager() instanceof Projectile pr) {
                ProjectileSource src = pr.getShooter();
                if (src instanceof Player pl2) attacker = pl2;
            }

            UUID ownerId;
            try {
                ownerId = UUID.fromString(victim.getMetadata(plugin.META_RA_DOLL).get(0).asString());
            } catch (Exception ex) {
                return;
            }

            double reflectedDmg = Math.max(e.getDamage(), 1.0D);
            Location center = victim.getLocation();
            World w = center.getWorld();
            w.spawnParticle(Particle.EXPLOSION, center.clone().add(0.0D, 1.0D, 0.0D), 2);
            w.spawnParticle(Particle.FLAME, center.clone().add(0.0D, 1.0D, 0.0D), 35, 0.4D, 0.6D, 0.4D, 0.1D);
            w.playSound(center, Sound.ENTITY_LIGHTNING_BOLT_IMPACT, 1.2F, 1.8F);

            Player owner = plugin.getServer().getPlayer(ownerId);
            int hits = 0;
            reflecting = true;
            try {
                for (Entity target : w.getNearbyEntities(center, 10.0D, 10.0D, 10.0D)) {
                    if (target.getUniqueId().equals(ownerId)) continue;
                    if (!plugin.isTargetable(target, null)) continue;
                    if (target.getLocation().distanceSquared(center) > 100.0D) continue;
                    LivingEntity le = (LivingEntity) target;
                    le.setNoDamageTicks(0);
                    le.damage(reflectedDmg, owner != null ? owner : attacker);
                    le.setFireTicks(80);
                    hits++;
                    le.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, le.getLocation().add(0.0D, 1.0D, 0.0D), 10, 0.2D, 0.4D, 0.2D, 0.05D);
                }
            } finally {
                reflecting = false;
            }

            Player notify = attacker != null ? attacker : owner;
            if (notify != null) {
                plugin.sendPriorityActionBar(notify, Component.text(
                        "Манекен Ра отразил " + String.format(Locale.US, "%.1f", reflectedDmg) + " урона. Целей: " + hits,
                        NamedTextColor.GOLD));
            }
        }
    
}
