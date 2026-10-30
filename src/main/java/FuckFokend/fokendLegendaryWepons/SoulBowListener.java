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
import org.bukkit.event.EventPriority;
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
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.metadata.FixedMetadataValue;

final class SoulBowListener implements Listener {
    private final FokendLegendaryWepons plugin;

    SoulBowListener(FokendLegendaryWepons plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        Action action = e.getAction();
        if (action != Action.LEFT_CLICK_AIR && action != Action.LEFT_CLICK_BLOCK) return;

        Player p = e.getPlayer();
        if (!p.isSneaking()) return;
        if (!this.plugin.isLegendary(p.getInventory().getItemInMainHand(), FokendLegendaryWepons.ID_SOUL_BOW)) return;
        if (!this.plugin.checkAndMarkTick(p, "soul_bow_consume")) return;

        e.setCancelled(true);
        UUID id = p.getUniqueId();
        int curSouls = this.plugin.getSouls(id);
        if (curSouls <= 0) {
            this.plugin.sendPriorityActionBar(p, Component.text("Нет душ для поглощения!", NamedTextColor.RED));
            return;
        }

        this.plugin.souls.put(id, curSouls - 1);
        this.plugin.saveSouls();

        AttributeInstance maxAttr = p.getAttribute(Attribute.MAX_HEALTH);
        double curBonus = 0.0D;
        if (maxAttr != null) {
            NamespacedKey key = new NamespacedKey(this.plugin, "soul_consume");
            for (AttributeModifier mod : maxAttr.getModifiers()) {
                if (key.equals(mod.getKey())) {
                    curBonus = mod.getAmount();
                    break;
                }
            }
        }

        double nextBonus = Math.min(40.0D, curBonus + 8.0D);
        this.plugin.setHealthModifier(p, "soul_consume", nextBonus, true);
        if (maxAttr != null) {
            p.setHealth(Math.min(maxAttr.getValue(), p.getHealth() + 8.0D));
        }

        p.getWorld().spawnParticle(
                Particle.SOUL, p.getLocation().add(0.0D, 1.0D, 0.0D), 30, 0.4D, 0.6D, 0.4D, 0.04D);
        p.getWorld().playSound(p.getLocation(), Sound.ITEM_TOTEM_USE, 0.7F, 1.5F);
        this.plugin.sendPriorityActionBar(
                p,
                Component.text(
                        "Поглощена душа! +4 ❤ (осталось душ: " + (curSouls - 1) + "/5)",
                        NamedTextColor.DARK_PURPLE));
    }

    @EventHandler
    public void onShoot(EntityShootBowEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;
        if (!this.plugin.isLegendary(e.getBow(), FokendLegendaryWepons.ID_SOUL_BOW)) return;

        if (this.plugin.isSoulless(p.getUniqueId()) || this.plugin.isLocked(p)) {
            e.setCancelled(true);
            return;
        }

        if (!(e.getProjectile() instanceof AbstractArrow arrow)) return;

        UUID id = p.getUniqueId();
        int curSouls = this.plugin.getSouls(id);
        int power = 5 + curSouls;
        double force = Math.max(0.2D, e.getForce());
        arrow.setDamage((2.0D + 0.5D * power + 0.5D) * force);
        arrow.setMetadata(FokendLegendaryWepons.META_SOUL_ARROW, new FixedMetadataValue(this.plugin, id.toString()));

        p.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, p.getEyeLocation(), 15, 0.2D, 0.2D, 0.2D, 0.03D);
        p.getWorld().playSound(p.getLocation(), Sound.PARTICLE_SOUL_ESCAPE, 1.0F, 1.0F);

        if (curSouls >= FokendLegendaryWepons.MAX_SOULS && e.getForce() >= 0.25F) {
            this.plugin.souls.put(id, curSouls - 1);
            this.plugin.saveSouls();
            this.plugin.sendPriorityActionBar(
                    p,
                    Component.text(
                            "Максимум душ! 1 душа сгорела при выстреле (" + (curSouls - 1) + "/5) • +" + power + "❤ урона",
                            NamedTextColor.DARK_PURPLE));
        } else {
            this.plugin.sendPriorityActionBar(
                    p,
                    Component.text(
                            "Выстрел Лука Душ (" + curSouls + "/5) • +" + power + "❤ урона",
                            NamedTextColor.DARK_PURPLE));
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDeath(PlayerDeathEvent e) {
        Player victim = e.getEntity();
        UUID victimId = victim.getUniqueId();
        if (this.plugin.hasSoulDependents(victimId)) {
            this.plugin.releaseAllFrom(victimId);
        }

        EntityDamageEvent lastCause = victim.getLastDamageCause();
        if (lastCause instanceof EntityDamageByEntityEvent be
                && be.getDamager() instanceof AbstractArrow arrow
                && arrow.hasMetadata(FokendLegendaryWepons.META_SOUL_ARROW)) {
            try {
                UUID bowOwnerId = UUID.fromString(
                        arrow.getMetadata(FokendLegendaryWepons.META_SOUL_ARROW).get(0).asString());
                if (!bowOwnerId.equals(victimId)) {
                    this.plugin.takeSoul(bowOwnerId, victimId);
                }
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent e) {
        UUID id = e.getPlayer().getUniqueId();
        if (this.plugin.isSoulless(id)) {
            this.plugin.getServer().getScheduler().runTask(this.plugin, () -> {
                Player online = this.plugin.getServer().getPlayer(id);
                if (online != null && online.isOnline() && this.plugin.isSoulless(id)) {
                    this.plugin.applySoulless(online);
                }
            });
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        UUID id = p.getUniqueId();
        if (this.plugin.pendingNormal.remove(id)) {
            this.plugin.saveSouls();
            this.plugin.applyNormal(p);
        }

        if (this.plugin.isSoulless(id)) {
            this.plugin.applySoulless(p);
        } else if (this.plugin.revived.containsKey(id)) {
            this.plugin.setHealthModifier(p, "soul_cap", -10.0D, true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onSpectateTeleport(PlayerTeleportEvent e) {
        if (e.getCause() == TeleportCause.SPECTATE
                && this.plugin.isSoulless(e.getPlayer().getUniqueId())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncChatEvent e) {
        if (this.plugin.isSoulless(e.getPlayer().getUniqueId())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onCommand(PlayerCommandPreprocessEvent e) {
        if (this.plugin.isSoulless(e.getPlayer().getUniqueId())) {
            String msg = e.getMessage().toLowerCase(Locale.ROOT);
            if (msg.startsWith("/login") || msg.startsWith("/register") || msg.startsWith("/l ")) {
                return;
            }
            e.setCancelled(true);
            e.getPlayer().sendMessage(Component.text("Бездушный не может ничего делать.", NamedTextColor.GRAY));
        }
    }
}
