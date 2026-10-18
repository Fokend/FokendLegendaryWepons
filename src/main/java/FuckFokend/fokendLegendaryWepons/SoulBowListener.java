package FuckFokend.fokendLegendaryWepons;

import io.papermc.paper.event.player.AsyncChatEvent;
import java.util.Locale;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.metadata.FixedMetadataValue;

final class SoulBowListener implements Listener {
    private final FokendLegendaryWepons plugin;
    SoulBowListener(FokendLegendaryWepons plugin) { this.plugin = plugin; }

        @EventHandler
        public void onInteract(PlayerInteractEvent e) {
            if (e.getHand() != EquipmentSlot.HAND) return;
            Action a = e.getAction();
            if (a != Action.LEFT_CLICK_AIR && a != Action.LEFT_CLICK_BLOCK) return;
            Player p = e.getPlayer();
            if (!p.isSneaking()) return;
            if (!plugin.isLegendary(p.getInventory().getItemInMainHand(), plugin.ID_SOUL_BOW)) return;
            if (!plugin.checkAndMarkTick(p, "soul_bow_consume")) return;
            e.setCancelled(true);

            UUID id = p.getUniqueId();
            int cur = plugin.getSouls(id);
            if (cur <= 0) {
                plugin.sendPriorityActionBar(p, Component.text("Нет душ для поглощения!", NamedTextColor.RED));
                return;
            }
            plugin.souls.put(id, cur - 1);
            plugin.saveSouls();

            AttributeInstance maxAttr = p.getAttribute(Attribute.MAX_HEALTH);
            double currentBonus = 0.0D;
            if (maxAttr != null) {
                NamespacedKey key = new NamespacedKey(plugin, "soul_consume");
                for (AttributeModifier m : maxAttr.getModifiers()) {
                    if (key.equals(m.getKey())) {
                        currentBonus = m.getAmount();
                        break;
                    }
                }
            }
            double nextBonus = Math.min(40.0D, currentBonus + 8.0D);
            plugin.setHealthModifier(p, "soul_consume", nextBonus, true);
            if (maxAttr != null) {
                p.setHealth(Math.min(maxAttr.getValue(), p.getHealth() + 8.0D));
            }
            p.getWorld().spawnParticle(Particle.SOUL, p.getLocation().add(0.0D, 1.0D, 0.0D), 30, 0.4D, 0.6D, 0.4D, 0.04D);
            p.getWorld().playSound(p.getLocation(), Sound.ITEM_TOTEM_USE, 0.7F, 1.5F);
            plugin.sendPriorityActionBar(p, Component.text("Душа поглощена (+4 сердца)! Осталось душ: " + (cur - 1) + "/5", NamedTextColor.DARK_PURPLE));
        }

        @EventHandler
        public void onShoot(EntityShootBowEvent e) {
            if (!(e.getEntity() instanceof Player p)) return;
            if (!plugin.isLegendary(e.getBow(), plugin.ID_SOUL_BOW)) return;
            if (plugin.isSoulless(p.getUniqueId()) || plugin.isLocked(p)) {
                e.setCancelled(true);
                return;
            }
            if (!(e.getProjectile() instanceof AbstractArrow arrow)) return;

            UUID id = p.getUniqueId();
            int cur = plugin.getSouls(id);
            int power = 5 + cur;
            arrow.setDamage(2.0D + 0.5D * power + 0.5D);
            arrow.setMetadata(plugin.META_SOUL_ARROW, new FixedMetadataValue(plugin, id.toString()));
            p.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, p.getEyeLocation(), 15, 0.2D, 0.2D, 0.2D, 0.03D);
            p.getWorld().playSound(p.getLocation(), Sound.PARTICLE_SOUL_ESCAPE, 1.0F, 1.0F);
            if (cur >= plugin.MAX_SOULS) {
                plugin.souls.put(id, cur - 1);
                plugin.saveSouls();
                plugin.sendPriorityActionBar(p, Component.text("Душа потрачена. Душ: " + (cur - 1) + "/5 (сила " + power + ")", NamedTextColor.DARK_PURPLE));
            } else {
                plugin.sendPriorityActionBar(p, Component.text("Душ: " + cur + "/5 (сила " + power + ")", NamedTextColor.DARK_PURPLE));
            }
        }

        @EventHandler
        public void onDeath(PlayerDeathEvent e) {
            Player victim = e.getEntity();
            UUID vid = victim.getUniqueId();
            if (plugin.hasSoulDependents(vid)) {
                plugin.releaseAllFrom(vid);
            }
            EntityDamageEvent last = victim.getLastDamageCause();
            if (last instanceof EntityDamageByEntityEvent be && be.getDamager() instanceof AbstractArrow arrow
                    && arrow.hasMetadata(plugin.META_SOUL_ARROW)) {
                try {
                    UUID ownerId = UUID.fromString(arrow.getMetadata(plugin.META_SOUL_ARROW).get(0).asString());
                    if (!ownerId.equals(vid)) {
                        plugin.takeSoul(ownerId, vid);
                    }
                } catch (IllegalArgumentException ignored) {
                }
            }
        }

        @EventHandler
        public void onRespawn(PlayerRespawnEvent e) {
            final Player p = e.getPlayer();
            if (plugin.isSoulless(p.getUniqueId())) {
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    if (p.isOnline() && plugin.isSoulless(p.getUniqueId())) {
                        plugin.applySoulless(p);
                    }
                });
            }
        }

        @EventHandler
        public void onJoin(PlayerJoinEvent e) {
            Player p = e.getPlayer();
            UUID id = p.getUniqueId();
            if (plugin.pendingNormal.remove(id)) {
                plugin.saveSouls();
                plugin.applyNormal(p);
            }
            if (plugin.isSoulless(id)) {
                plugin.applySoulless(p);
            } else if (plugin.revived.containsKey(id)) {
                plugin.setHealthModifier(p, "soul_cap", -10.0D, true);
            }
        }

        @EventHandler
        public void onChat(AsyncChatEvent e) {
            if (plugin.isSoulless(e.getPlayer().getUniqueId())) {
                e.setCancelled(true);
            }
        }

        @EventHandler
        public void onCommand(PlayerCommandPreprocessEvent e) {
            if (plugin.isSoulless(e.getPlayer().getUniqueId())) {
                String msg = e.getMessage().toLowerCase(Locale.ROOT);
                if (msg.startsWith("/login") || msg.startsWith("/register") || msg.startsWith("/l ")) return;
                e.setCancelled(true);
                e.getPlayer().sendMessage(Component.text("Бездушный не может ничего делать.", NamedTextColor.GRAY));
            }
        }
    
}
