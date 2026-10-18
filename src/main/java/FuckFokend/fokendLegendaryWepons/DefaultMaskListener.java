package FuckFokend.fokendLegendaryWepons;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

final class DefaultMaskListener implements Listener {
    private final FokendLegendaryWepons plugin;
    DefaultMaskListener(FokendLegendaryWepons plugin) {
        this.plugin = plugin;
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : plugin.getServer().getOnlinePlayers()) {
                    applyMaskAppearance(player.getInventory().getHelmet(), true);
                    for (int slot = 0; slot < 36; slot++) {
                        applyMaskAppearance(player.getInventory().getItem(slot), false);
                    }
                    applyMaskAppearance(player.getInventory().getItemInOffHand(), false);
                    applyMaskAppearance(player.getItemOnCursor(), false);
                }
            }
        }.runTaskTimer(plugin, 1L, 10L);
    }

    private void applyMaskAppearance(ItemStack item, boolean worn) {
        if (!plugin.isLegendary(item, plugin.ID_DEFAULT_MASK)) return;
        int cmd = worn ? plugin.CMD_HAPPY_MASK : plugin.CMD_DEFAULT_MASK;
        String title = worn
                ? "<gradient:#FFE000:#799F0C><bold>☺ Эйфория • Счастливая Маска</bold></gradient>"
                : "<gradient:#56ab2f:#a8e063><bold>🎭 Лик Безумия • Маска Хаоса</bold></gradient>";
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;
        boolean sameModel = meta.hasCustomModelData() && meta.getCustomModelData() == cmd;
        if (sameModel && meta.hasDisplayName()
                && meta.displayName().equals(plugin.mm.deserialize(title).decoration(TextDecoration.ITALIC, false))) return;
        plugin.applyCustomModelData(meta, cmd);
        meta.displayName(plugin.mm.deserialize(title).decoration(TextDecoration.ITALIC, false));
        item.setItemMeta(meta);
    }

                private final Map<UUID, List<ItemDisplay>> gunDisplays = new HashMap<>();
                private final Map<UUID, Long> gunUntil = new HashMap<>();

                @EventHandler
                public void onSwap(PlayerSwapHandItemsEvent e) {
                    Player p = e.getPlayer();
                    if (p.isSneaking() && plugin.isLegendary(p.getInventory().getHelmet(), plugin.ID_DEFAULT_MASK)) {
                        e.setCancelled(true);
                        if (!plugin.checkAndMarkTick(p, "mask_hammer")) return;
                        triggerHammer(p);
                    }
                }

                @EventHandler
                public void onInteract(PlayerInteractEvent e) {
                    if (!plugin.isAbilityClick(e)) return;
                    Player p = e.getPlayer();
                    if (p.isSneaking() && plugin.isLegendary(p.getInventory().getHelmet(), plugin.ID_DEFAULT_MASK)) {
                        e.setCancelled(true);
                        if (!plugin.checkAndMarkTick(p, "mask_hammer")) return;
                        triggerHammer(p);
                        return;
                    }
                    ItemStack held = p.getInventory().getItemInMainHand();
                    boolean holding = plugin.isLegendary(held, plugin.ID_DEFAULT_MASK);

                    if (holding && !p.isSneaking()) {
                        e.setCancelled(true);
                        if (!plugin.checkAndMarkTick(p, "mask_tompson")) return;
                        triggerThompson(p);
                    }
                }

                @EventHandler
                public void onSneak(PlayerToggleSneakEvent e) {
                    if (e.isSneaking()) {
                        plugin.maskSneakStart.put(e.getPlayer().getUniqueId(), System.currentTimeMillis());
                    } else {
                        plugin.maskSneakStart.remove(e.getPlayer().getUniqueId());
                    }
                }

                private void triggerHammer(Player p) {
                    if (plugin.onCooldown(p, "mask_hammer", plugin.MASK_HAMMER_CD)) {
                        long s = plugin.cooldownRemaining(p, "mask_hammer", plugin.MASK_HAMMER_CD) / 1000L + 1L;
                        plugin.sendPriorityActionBar(p, Component.text("Молот маски: " + s + "с", NamedTextColor.RED));
                        return;
                    }
                    plugin.setCooldown(p, "mask_hammer", plugin.MASK_HAMMER_CD, "Молот маски");
                    float yaw = p.getLocation().getYaw();
                    Location c = p.getLocation().add(plugin.flatDir(yaw).multiply(3));
                    World w = c.getWorld();
                    Location above = c.clone().add(0.0D, 4.0D, 0.0D);
                    above.setYaw(yaw);
                    above.setPitch(0.0F);

                    ItemDisplay hammer = plugin.track(w.spawn(above, ItemDisplay.class, d -> {
                        d.setItemStack(plugin.maskMace());
                        d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
                        d.setPersistent(false);
                        d.setBrightness(new Display.Brightness(15, 15));
                        d.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f((float) Math.PI, 0.0F, 0.0F, 1.0F), new Vector3f(5.0F), new AxisAngle4f()));
                    }));
                    w.playSound(above, Sound.ITEM_MACE_SMASH_AIR, 1.5F, 0.7F);

                    new BukkitRunnable() {
                        int tick = 0;

                        @Override
                        public void run() {
                            tick++;
                            double prog = Math.min(1.0D, (double) tick / 8.0D);
                            if (hammer.isValid()) {
                                Location loc = above.clone().subtract(0.0D, 3.0D * prog * prog, 0.0D);
                                hammer.teleport(loc);
                                w.spawnParticle(Particle.CLOUD, loc, 3, 0.2D, 0.1D, 0.2D, 0.01D);
                            }
                            if (tick >= 8) {
                                plugin.removeTracked(hammer);
                                cancel();
                                w.playSound(c, Sound.ENTITY_GENERIC_EXPLODE, 1.8F, 0.6F);
                                w.playSound(c, Sound.ITEM_MACE_SMASH_GROUND_HEAVY, 1.5F, 0.8F);
                                w.spawnParticle(Particle.EXPLOSION_EMITTER, c, 2);
                                w.spawnParticle(Particle.CLOUD, c, 40, 2.0D, 0.3D, 2.0D, 0.05D);
                                for (int i = 0; i < 36; i++) {
                                    double a = i * Math.PI / 18.0D;
                                    for (double r = 1.0D; r <= 5.0D; r += 2.0D) {
                                        w.spawnParticle(Particle.CLOUD, c.clone().add(Math.cos(a) * r, 0.2D, Math.sin(a) * r), 1, 0.0D, 0.0D, 0.0D, 0.03D);
                                    }
                                }
                                int hits = 0;
                                for (Entity ent : w.getNearbyEntities(c, 5.0D, 5.0D, 5.0D)) {
                                    if (plugin.isTargetable(ent, p)) {
                                        if (!plugin.HAMMER_HITS_MOBS && !(ent instanceof Player)) continue;
                                        LivingEntity le = (LivingEntity) ent;
                                        Location mid = le.getLocation().add(0.0D, le.getHeight() / 2.0D, 0.0D);
                                        if (mid.distanceSquared(c) <= 25.0D) {
                                            plugin.markCustomDamage(le, p, "mask_hammer", 20.0D, true);
                                            if (!plugin.lockStates.containsKey(le.getUniqueId())) {
                                                Vector away = le.getLocation().toVector().subtract(c.toVector()).setY(0);
                                                if (away.lengthSquared() > 1.0E-4D) away.normalize().multiply(0.6D);
                                                le.setVelocity(away.setY(0.5D));
                                            }
                                            hits++;
                                        }
                                    }
                                }
                                if (p.isOnline()) {
                                    plugin.sendPriorityActionBar(p, Component.text("Молот: поражено целей — " + hits, NamedTextColor.GOLD));
                                }
                            }
                        }
                    }.runTaskTimer(plugin, 0L, 1L);
                }

                private void triggerThompson(Player p) {
                    if (plugin.onCooldown(p, "mask_tompson_shot", 250L)) return;
                    plugin.cooldowns.put(p.getUniqueId() + ":mask_tompson_shot", System.currentTimeMillis());
                    Vector dir = p.getEyeLocation().getDirection();
                    Vector side = plugin.sideVector(dir).multiply(0.3D);
                    for (Location loc : List.of(p.getEyeLocation().subtract(side), p.getEyeLocation().add(side))) {
                        plugin.fireThompsonShot(p, loc, dir, plugin.META_MASK_EXPLOSIVE);
                    }
                    showGuns(p);
                    p.getWorld().playSound(p.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 0.35F, 2.0F);
                    p.getWorld().playSound(p.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 0.35F, 1.8F);
                }

                private void showGuns(Player p) {
                    UUID id = p.getUniqueId();
                    gunUntil.put(id, System.currentTimeMillis() + 1200L);
                    if (gunDisplays.containsKey(id)) return;

                    List<ItemDisplay> guns = new ArrayList<>();
                    for (int i = 0; i < 2; i++) {
                        ItemDisplay d = plugin.track(p.getWorld().spawn(p.getEyeLocation(), ItemDisplay.class, disp -> {
                            disp.setItemStack(plugin.maskThompson());
                            disp.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
                            disp.setPersistent(false);
                            disp.setBrightness(new Display.Brightness(15, 15));
                            disp.setTeleportDuration(1);
                            disp.setTransformation(new Transformation(new Vector3f(),
                                    new AxisAngle4f((float) (Math.PI / 2.0D), 0.0F, 0.0F, 1.0F),
                                    new Vector3f(1.0F), new AxisAngle4f()));
                        }));
                        guns.add(d);
                    }
                    gunDisplays.put(id, guns);

                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            Long until = gunUntil.get(id);
                            if (p.isOnline() && until != null && System.currentTimeMillis() <= until
                                    && plugin.isLegendary(p.getInventory().getItemInMainHand(), plugin.ID_DEFAULT_MASK)) {
                                Location eye = p.getEyeLocation();
                                Vector dir = eye.getDirection();
                                Vector side = plugin.sideVector(dir);
                                for (int i = 0; i < guns.size(); i++) {
                                    int sgn = i == 0 ? -1 : 1;
                                    Location l = eye.clone().add(dir.clone().multiply(0.7D)).add(side.clone().multiply(0.5D * sgn)).subtract(0.0D, 0.3D, 0.0D);
                                    l.setYaw(eye.getYaw());
                                    l.setPitch(eye.getPitch());
                                    if (guns.get(i).isValid()) guns.get(i).teleport(l);
                                }
                            } else {
                                for (ItemDisplay d : guns) plugin.removeTracked(d);
                                gunDisplays.remove(id);
                                gunUntil.remove(id);
                                cancel();
                            }
                        }
                    }.runTaskTimer(plugin, 0L, 1L);
                }
            
}
