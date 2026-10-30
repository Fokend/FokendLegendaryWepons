package FuckFokend.fokendLegendaryWepons;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

final class ScalesOfFateListener implements Listener {
   static final long CD_JUDGEMENT = 20000L;
   static final long CD_BAD = 15000L;
   static final long CD_GOOD = 25000L;
   static final long LIGHTNING_LIFETIME_MS = 20000L;
   static final long EMPOWER_LIFETIME_MS = 30000L;
   private static final List<PotionEffectType> DEBUFFS;
   private static final List<PotionEffectType> BUFFS;
   private final FokendLegendaryWepons plugin;
   private final NamespacedKey expiryKey;
   private final Map<UUID, Long> empowered = new ConcurrentHashMap();
   private boolean busy;
   private final Set<UUID> damageGuard = ConcurrentHashMap.newKeySet();

   ScalesOfFateListener(final FokendLegendaryWepons plugin) {
      this.plugin = plugin;
      this.expiryKey = new NamespacedKey(plugin, "fate_expiry");
      (new BukkitRunnable() {
         public void run() {
            long now = System.currentTimeMillis();

            for(Player p : plugin.getServer().getOnlinePlayers()) {
               ScalesOfFateListener.this.purgeExpired(p, now);
            }

            ScalesOfFateListener.this.empowered.values().removeIf((t) -> t < now);
         }
      }).runTaskTimer(plugin, 10L, 10L);
   }

   static ItemStack createScales(FokendLegendaryWepons plugin) {
      ItemStack item = plugin.base(Material.NETHERITE_SWORD, 20012, "fate_scales", "<gradient:#f9d423:#8e2de2><bold>⚖ Весы Судьбы</bold></gradient>", true);
      ItemMeta m = item.getItemMeta();
      plugin.setLore(m, "~Говорят... владельца преследуют те, чьи судьбы были предрешены Весами.", "", "#ВЕРХОВНЫЙ СУД (SHIFT+ПКМ, кд 20с)", "Левый мойр: следующий удар наносит 2x урон.", "Правый мойр: Милосердие — отбрасывает противника, слабый урон.", "#ПЛОХАЯ СУДЬБА (ЛКМ по врагу, кд 15с)", "Левый мойр: случайный дебафф случайного уровня.", "Правый мойр: подсвечивает врага и бьёт молнией.", "#ХОРОШАЯ СУДЬБА (ПКМ, кд 25с)", "Левый мойр: баффы владельцу.", "Правый мойр: предмет «Молния» на 20 секунд.");
      item.setItemMeta(m);
      return item;
   }

   static ItemStack createLightning(FokendLegendaryWepons plugin) {
      ItemStack item = plugin.base(Material.BLAZE_ROD, 20013, "fate_lightning", "<gradient:#fffb7d:#ff9a00><bold>⚡ Молния Судьбы</bold></gradient>", true);
      ItemMeta m = item.getItemMeta();
      plugin.setLore(m, "~Дар Хорошей Судьбы. Недолго живёт.", "", "#УДАР", "Поджигает врага и бьёт его молнией.", "Исчезает через 20 секунд.");
      m.getPersistentDataContainer().set(new NamespacedKey(plugin, "fate_expiry"), PersistentDataType.LONG, System.currentTimeMillis() + 20000L);
      item.setItemMeta(m);
      return item;
   }

   private boolean isScales(ItemStack i) {
      return i != null && this.plugin.isLegendary(i, "fate_scales");
   }

   private boolean isLightning(ItemStack i) {
      return i != null && i.hasItemMeta() && this.plugin.isLegendary(i, "fate_lightning");
   }

   private boolean lightningAlive(ItemStack i) {
      Long t = (Long)i.getItemMeta().getPersistentDataContainer().get(this.expiryKey, PersistentDataType.LONG);
      return t != null && t > System.currentTimeMillis();
   }

   private void purgeExpired(Player p, long now) {
      ItemStack[] contents = p.getInventory().getContents();

      for(int i = 0; i < contents.length; ++i) {
         ItemStack it = contents[i];
         if (this.isLightning(it)) {
            Long t = (Long)it.getItemMeta().getPersistentDataContainer().get(this.expiryKey, PersistentDataType.LONG);
            if (t == null || t < now) {
               p.getInventory().setItem(i, (ItemStack)null);
               p.playSound(p.getLocation(), Sound.BLOCK_FIRE_EXTINGUISH, 0.8F, 1.2F);
               p.sendActionBar(Component.text("Молния Судьбы гаснет...", NamedTextColor.GRAY));
            }
         }
      }

   }

   private static boolean coin() {
      return ThreadLocalRandom.current().nextBoolean();
   }

   private static Component moira(boolean left, String text) {
      return ((TextComponent)Component.text(left ? "⚖ Левый мойр: " : "⚖ Правый мойр: ", left ? NamedTextColor.GOLD : NamedTextColor.LIGHT_PURPLE).decorate(TextDecoration.BOLD)).append(Component.text(text, NamedTextColor.WHITE).decoration(TextDecoration.BOLD, false));
   }

   private void weighEffect(Player p) {
      p.getWorld().playSound(p.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.0F, 0.7F);
      p.getWorld().spawnParticle(Particle.ENCHANT, p.getLocation().add((double)0.0F, (double)1.5F, (double)0.0F), 30, (double)0.5F, (double)0.5F, (double)0.5F, (double)0.5F);
   }

   private void hurt(LivingEntity target, double dmg, Player src) {
      if (target != null && !target.isDead() && target.isValid()) {
         if (this.damageGuard.add(target.getUniqueId())) {
            if (src != null && !this.damageGuard.add(src.getUniqueId())) {
               this.damageGuard.remove(target.getUniqueId());
            } else {
               boolean was = this.busy;
               this.busy = true;

               try {
                  target.setNoDamageTicks(0);
                  target.damage(dmg, src);
               } finally {
                  this.busy = was;
                  this.damageGuard.remove(target.getUniqueId());
                  if (src != null) {
                     this.damageGuard.remove(src.getUniqueId());
                  }

               }

            }
         }
      }
   }

   private void lightningOn(LivingEntity target, Player src, double dmg, int fireTicks) {
      target.getWorld().strikeLightningEffect(target.getLocation());
      this.hurt(target, dmg, src);
      if (!target.isDead()) {
         target.setFireTicks(Math.max(target.getFireTicks(), fireTicks));
      }

   }

   @EventHandler(
      priority = EventPriority.HIGH
   )
   public void onInteract(PlayerInteractEvent e) {
      if (e.getHand() == EquipmentSlot.HAND) {
         Action a = e.getAction();
         if (a == Action.RIGHT_CLICK_AIR || a == Action.RIGHT_CLICK_BLOCK) {
            Player p = e.getPlayer();
            if (this.isScales(p.getInventory().getItemInMainHand())) {
               if (this.plugin.isAbilityClick(e) && this.plugin.checkAndMarkTick(p, "fate_rmb")) {
                  if (p.isSneaking()) {
                     this.highJudgement(p);
                  } else {
                     this.goodFate(p);
                  }

               }
            }
         }
      }
   }

   private void highJudgement(Player p) {
      if (!this.plugin.onCooldown(p, "fate_judgement", 20000L)) {
         RayTraceResult r = p.getWorld().rayTraceEntities(p.getEyeLocation(), p.getEyeLocation().getDirection(), (double)20.0F, 0.6, (en) -> this.plugin.isTargetable(en, p));
         if (r != null) {
            Entity var4 = r.getHitEntity();
            if (var4 instanceof LivingEntity) {
               LivingEntity target = (LivingEntity)var4;
               this.plugin.setCooldown(p, "fate_judgement", 20000L, "Верховный Суд");
               this.weighEffect(p);
               boolean left = coin();
               if (left) {
                  this.empowered.put(p.getUniqueId(), System.currentTimeMillis() + 30000L);
                  p.sendMessage(moira(true, "следующий удар нанесёт 2x урон (30 сек)."));
                  p.getWorld().spawnParticle(Particle.CRIT, p.getLocation().add((double)0.0F, (double)1.0F, (double)0.0F), 30, 0.4, 0.6, 0.4, 0.2);
               } else {
                  Vector kb = target.getLocation().toVector().subtract(p.getLocation().toVector());
                  kb.setY(0);
                  if (kb.lengthSquared() < 1.0E-4) {
                     kb = p.getEyeLocation().getDirection().setY(0);
                  }

                  kb.normalize().multiply(1.4).setY(0.45);
                  this.hurt(target, (double)2.0F, p);
                  target.setVelocity(kb);
                  target.getWorld().spawnParticle(Particle.HEART, target.getLocation().add((double)0.0F, (double)1.5F, (double)0.0F), 6, 0.3, 0.3, 0.3);
                  p.sendMessage(moira(false, "Милосердие — Весы откидывают противника."));
               }

               return;
            }
         }

         p.sendActionBar(Component.text("Весам не на кого посмотреть: наведись на противника.", NamedTextColor.GRAY));
      }
   }

   private void goodFate(Player p) {
      if (!this.plugin.onCooldown(p, "fate_good", 25000L)) {
         this.plugin.setCooldown(p, "fate_good", 25000L, "Хорошая судьба");
         this.weighEffect(p);
         boolean left = coin();
         if (left) {
            List<PotionEffectType> pool = new ArrayList(BUFFS);
            Collections.shuffle(pool);
            ThreadLocalRandom rnd = ThreadLocalRandom.current();
            StringBuilder sb = new StringBuilder();

            for(int i = 0; i < 3; ++i) {
               PotionEffectType t = (PotionEffectType)pool.get(i);
               int amp = rnd.nextInt(2);
               int dur = 300 + rnd.nextInt(301);
               p.addPotionEffect(new PotionEffect(t, dur, amp, false, true, true));
               if (i > 0) {
                  sb.append(", ");
               }

               sb.append(t.getKey().getKey()).append(' ').append(amp + 1);
            }

            p.sendMessage(moira(true, "баффы: " + String.valueOf(sb)));
            p.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, p.getLocation().add((double)0.0F, (double)1.0F, (double)0.0F), 25, (double)0.5F, 0.7, (double)0.5F);
         } else {
            for(int i = 0; i < p.getInventory().getSize(); ++i) {
               if (this.isLightning(p.getInventory().getItem(i))) {
                  p.getInventory().setItem(i, (ItemStack)null);
               }
            }

            this.plugin.giveOrDrop(p, createLightning(this.plugin));
            p.sendMessage(moira(false, "«Молния Судьбы» на 20 секунд."));
            p.getWorld().playSound(p.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.4F, 1.8F);
         }

      }
   }

   @EventHandler(
      priority = EventPriority.HIGH,
      ignoreCancelled = true
   )
   public void onHit(EntityDamageByEntityEvent e) {
      if (!this.busy && e.getCause() == DamageCause.ENTITY_ATTACK) {
         Entity var4 = e.getDamager();
         if (var4 instanceof Player) {
            Player p = (Player)var4;
            var4 = e.getEntity();
            if (var4 instanceof LivingEntity) {
               LivingEntity target = (LivingEntity)var4;
               if (!this.damageGuard.contains(p.getUniqueId()) && !this.damageGuard.contains(target.getUniqueId())) {
                  ItemStack hand = p.getInventory().getItemInMainHand();
                  if (this.isLightning(hand)) {
                     if (!this.lightningAlive(hand)) {
                        return;
                     }

                     target.setFireTicks(Math.max(target.getFireTicks(), 100));
                     target.getWorld().strikeLightningEffect(target.getLocation());
                     this.Bukkit_run(() -> {
                        if (target.isValid() && !target.isDead()) {
                           this.hurt(target, (double)4.0F, p);
                        }

                     });
                     return;
                  }

                  if (this.isScales(hand) && this.plugin.isTargetable(target, p)) {
                     Long until = (Long)this.empowered.get(p.getUniqueId());
                     if (until != null) {
                        this.empowered.remove(p.getUniqueId());
                        if (until > System.currentTimeMillis()) {
                           e.setDamage(e.getDamage() * (double)2.0F);
                           p.sendActionBar(Component.text("Верховный Суд: удар 2x!", NamedTextColor.GOLD));
                           target.getWorld().spawnParticle(Particle.CRIT, target.getLocation().add((double)0.0F, (double)1.0F, (double)0.0F), 25, 0.4, (double)0.5F, 0.4, 0.3);
                           target.getWorld().playSound(target.getLocation(), Sound.ENTITY_PLAYER_ATTACK_CRIT, 1.0F, 0.7F);
                        }
                     }

                     if (this.plugin.onCooldown(p, "fate_bad", 15000L)) {
                        return;
                     }

                     this.plugin.setCooldown(p, "fate_bad", 15000L, "Плохая судьба");
                     this.weighEffect(p);
                     if (coin()) {
                        ThreadLocalRandom rnd = ThreadLocalRandom.current();
                        PotionEffectType t = (PotionEffectType)DEBUFFS.get(rnd.nextInt(DEBUFFS.size()));
                        int amp = rnd.nextInt(4);
                        int dur = 100 + rnd.nextInt(151);
                        target.addPotionEffect(new PotionEffect(t, dur, amp, false, true, true));
                        String var10002 = t.getKey().getKey();
                        p.sendMessage(moira(true, var10002 + " " + (amp + 1) + " на " + dur / 20 + " сек."));
                        target.getWorld().spawnParticle(Particle.SMOKE, target.getLocation().add((double)0.0F, (double)1.0F, (double)0.0F), 20, 0.3, (double)0.5F, 0.3, 0.02);
                     } else {
                        target.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 200, 0, false, false, true));
                        p.sendMessage(moira(false, "враг подсвечен, молния ударяет!"));
                        this.Bukkit_run(() -> {
                           if (target.isValid() && !target.isDead()) {
                              this.lightningOn(target, p, (double)5.0F, 60);
                           }

                        });
                     }

                     return;
                  }

                  return;
               }

               return;
            }
         }

      }
   }

   private void Bukkit_run(Runnable r) {
      this.plugin.getServer().getScheduler().runTask(this.plugin, r);
   }

   @EventHandler(
      ignoreCancelled = true
   )
   public void onDrop(PlayerDropItemEvent e) {
      if (this.isLightning(e.getItemDrop().getItemStack())) {
         e.setCancelled(true);
      }

   }

   @EventHandler(
      ignoreCancelled = true
   )
   public void onPlace(BlockPlaceEvent e) {
      if (this.isLightning(e.getItemInHand())) {
         e.setCancelled(true);
      }

   }

   @EventHandler(
      ignoreCancelled = true
   )
   public void onClick(InventoryClickEvent e) {
      if (e.getView().getTopInventory().getType() != InventoryType.CRAFTING) {
         boolean involved = this.isLightning(e.getCurrentItem()) || this.isLightning(e.getCursor());
         if (involved) {
            boolean topClicked = e.getClickedInventory() != null && e.getClickedInventory().equals(e.getView().getTopInventory());
            if (topClicked || e.isShiftClick()) {
               e.setCancelled(true);
            }

         }
      }
   }

   @EventHandler
   public void onDeath(PlayerDeathEvent e) {
      e.getDrops().removeIf(this::isLightning);
      this.empowered.remove(e.getEntity().getUniqueId());
   }

   @EventHandler
   public void onQuit(PlayerQuitEvent e) {
      this.empowered.remove(e.getPlayer().getUniqueId());
   }

   static {
      DEBUFFS = List.of(PotionEffectType.SLOWNESS, PotionEffectType.WEAKNESS, PotionEffectType.POISON, PotionEffectType.WITHER, PotionEffectType.BLINDNESS, PotionEffectType.NAUSEA, PotionEffectType.HUNGER, PotionEffectType.MINING_FATIGUE, PotionEffectType.DARKNESS);
      BUFFS = List.of(PotionEffectType.SPEED, PotionEffectType.STRENGTH, PotionEffectType.RESISTANCE, PotionEffectType.REGENERATION, PotionEffectType.ABSORPTION, PotionEffectType.JUMP_BOOST, PotionEffectType.FIRE_RESISTANCE, PotionEffectType.HASTE);
   }
}
