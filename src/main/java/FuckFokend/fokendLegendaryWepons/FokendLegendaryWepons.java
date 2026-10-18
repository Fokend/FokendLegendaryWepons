package FuckFokend.fokendLegendaryWepons;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.FluidCollisionMode;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.attribute.AttributeModifier.Operation;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Snowball;
import org.bukkit.entity.Tameable;
import org.bukkit.entity.TextDisplay;
import org.bukkit.entity.Warden;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.components.CustomModelDataComponent;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

public final class FokendLegendaryWepons extends JavaPlugin {
    static final String ID_CB_ORANGE = "cb_orange";
    static final String ID_CB_PURPLE = "cb_purple";
    static final String ID_CB_GREEN = "cb_green";

    static final String ID_RA_BLADE = "ra_god_blade";
    static final String ID_CHAOS_BOOMERANG = "chaos_boomerang";
    static final String ID_WARDEN_HELMET = "warden_helmet";
    static final String ID_TWINS_SWORD = "twins_sword";
    static final String ID_MAGNET = "magnet";
    static final String ID_TIME_CLOCK = "time_clock";
    static final String ID_ICE_SPEAR = "ice_spear";
    static final String ID_ICE_SPEAR_ICE = "ice_spear_ice";
    static final String ID_ICE_SPEAR_SPIKE = "ice_spear_spike";
    static final String ID_DEFAULT_MASK = "default_mask";
    static final String ID_HAPPY_MASK = "happy_mask";
    static final String ID_MASK_BLOCK = "mask_block";
    static final String ID_MASK_MACE = "mask_mace";
    static final String ID_MASK_TOMPSON = "mask_tompson";
    static final String ID_SOUL_BOW = "soul_bow";

    static final int CMD_RA_BLADE = 10001;
    static final int CMD_CHAOS_BOOMERANG = 10002;
    static final int CMD_WARDEN_HELMET = 10003;
    static final int CMD_TWINS_SWORD = 10004;
    static final int CMD_MAGNET = 10005;
    static final int CMD_TIME_CLOCK = 20001;
    static final int CMD_ICE_SPEAR = 20002;
    static final int CMD_ICE_SPEAR_ICE = 20003;
    static final int CMD_ICE_SPEAR_SPIKE = 20004;
    static final int CMD_DEFAULT_MASK = 20005;
    static final int CMD_HAPPY_MASK = 20006;
    static final int CMD_MASK_BLOCK = 20007;
    static final int CMD_MASK_MACE = 20008;
    static final int CMD_MASK_TOMPSON = 20009;
    static final int CMD_SOUL_BOW = 20010;

    static final String META_TOMPSON = "mask_explosive";
    static final String META_SOUL_ARROW = "soul_arrow";
    static final String META_RA_DOLL = "ra_doll_owner";
    static final String META_MASK_EXPLOSIVE = "default_mask_explosive";
    static final String META_TWIN = "twins_npc_owner";
    static final String META_DEATH_REASON = "legendary_death_reason";

    static final long MASK_HAMMER_CD = 300000L;
    static final int BOOMERANG_MIN_BOUNCES = 3;
    static final int BOOMERANG_MAX_BOUNCES = 10;
    static final long MASK_RUN_DELAY = 5000L;
    static final long MASK_RUN_LINGER = 10000L;
    static final int MAX_SOULS = 5;
    static final double REVIVE_HP = 5.0D;

    static final boolean HAMMER_HITS_MOBS = false;

    record ThrownBoomerang(UUID owner, ItemStack item) {}
    private record HudCooldown(long durationMs, String name) {}
    record LockState(boolean ai, boolean gravity, boolean invulnerable) {}

    static final class TimeSession {
        final UUID owner;
        final UUID target;
        final List<TimeFrame> frames = new ArrayList<>();
        BukkitTask recordTask;
        BukkitTask timeoutTask;
        boolean frozen = false;
        int index = 0;

        TimeSession(UUID owner, UUID target) {
            this.owner = owner;
            this.target = target;
        }
    }

    record TimeFrame(Location location, double health) {}

    final Object fileLock = new Object();
    final MiniMessage mm = MiniMessage.miniMessage();
    final Map<UUID, UUID> raDolls = new HashMap<>();
    final Map<UUID, List<LivingEntity>> activeTwins = new HashMap<>();
    final Map<String, Long> cooldowns = new HashMap<>();
    final Map<String, HudCooldown> cooldownHud = new HashMap<>();
    final Map<UUID, Long> actionBarPriorityUntil = new HashMap<>();
    final Map<UUID, String> selectedHudCooldown = new HashMap<>();
    static final int COOLDOWN_BAR_LENGTH = 10;
    final Map<UUID, Long> maskSneakStart = new HashMap<>();
    final Map<UUID, Long> maskRunUntil = new HashMap<>();
    final Set<UUID> hovering = new HashSet<>();
    final Set<UUID> fallingFree = new HashSet<>();
    final Map<UUID, Double> lastY = new HashMap<>();
    final Set<Entity> tempEntities = new HashSet<>();
    final Map<UUID, ThrownBoomerang> boomerangs = new HashMap<>();
    final Map<UUID, LockState> lockStates = new HashMap<>();
    final Map<UUID, TimeSession> timeSessions = new HashMap<>();
    final Map<UUID, TimeSession> timeByTarget = new HashMap<>();
    final Map<UUID, Integer> souls = new ConcurrentHashMap<>();
    final Map<UUID, UUID> soulless = new ConcurrentHashMap<>();
    final Map<UUID, UUID> revived = new ConcurrentHashMap<>();
    final Map<UUID, UUID> revivers = new ConcurrentHashMap<>();
    final Set<UUID> pendingNormal = ConcurrentHashMap.newKeySet();
    final Set<String> craftedUniques = ConcurrentHashMap.newKeySet();
    final Map<String, Integer> lastInteractTick = new HashMap<>();

    File soulFile;
    NamespacedKey idKey;
    NamespacedKey frozenKey;
    NamespacedKey runKey;
    static final List<String> RECIPE_KEYS = List.of(
            "cb_orange", "cb_purple", "cb_green", "time_clock", "ice_spear", "default_mask",
            "warden_helmet", "ra_blade", "chaos_boomerang", "twins_sword", "magnet", "happy_mask");

    @Override
    public void onEnable() {
        saveDefaultConfig();
        idKey = new NamespacedKey(this, "legendary_id");
        frozenKey = new NamespacedKey(this, "time_frozen");
        runKey = new NamespacedKey(this, "mask_run");
        loadSouls();

        PluginManager pm = getServer().getPluginManager();
        pm.registerEvents(new RaBladeListener(this), this);
        pm.registerEvents(new WardenHelmetListener(this), this);
        pm.registerEvents(new IceSpearListener(this), this);
        pm.registerEvents(new MaskThompsonListener(this), this);
        pm.registerEvents(new SoulBowListener(this), this);
        pm.registerEvents(new TimeClockListener(this), this);
        pm.registerEvents(new FreezeListener(this), this);
        pm.registerEvents(new ChaosBoomerangListener(this), this);
        pm.registerEvents(new TwinsSwordListener(this), this);
        pm.registerEvents(new MagnetListener(this), this);
        pm.registerEvents(new DefaultMaskListener(this), this);
        pm.registerEvents(new CleanupListener(this), this);
        pm.registerEvents(new CraftProtectionListener(this), this);
        pm.registerEvents(new CoreUpgradeListener(this), this);
        pm.registerEvents(new ItemProtectionListener(this), this);
        pm.registerEvents(new EntityRightClickBridge(this), this);

        PluginCommand cmd = getCommand("legendary");
        if (cmd != null) {
            LegendaryCommand exec = new LegendaryCommand();
            cmd.setExecutor(exec);
            cmd.setTabCompleter(exec);
        }

        final ReviveCommand reviveExec = new ReviveCommand();
        PluginCommand rev = getCommand("revive");
        if (rev != null) {
            rev.setExecutor(reviveExec);
            rev.setTabCompleter(reviveExec);
        } else {
            Command dyn = new Command("revive") {
                @Override
                public boolean execute(@NotNull CommandSender sender, @NotNull String label, @NotNull String[] args) {
                    return reviveExec.onCommand(sender, this, label, args);
                }

                @Override
                public @NotNull List<String> tabComplete(@NotNull CommandSender sender, @NotNull String alias, @NotNull String[] args) {
                    return reviveExec.onTabComplete(sender, this, alias, args);
                }
            };
            dyn.setDescription("Возродить бездушного игрока (цена: 5 сердец)");
            dyn.setUsage("/revive <ник>");
            getServer().getCommandMap().register("fokendlegendary", dyn);
        }

        registerRecipes();
        logRecipes();

        for (World w : getServer().getWorlds()) {
            for (LivingEntity le : w.getLivingEntities()) {
                restoreStaleFrozen(le);
            }
        }

        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player p : getServer().getOnlinePlayers()) {
                    if (isLegendary(p.getInventory().getHelmet(), ID_WARDEN_HELMET)) {
                        p.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 100, 0, false, false, false));
                        p.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 100, 0, false, false, false));
                        for (Entity ent : p.getWorld().getNearbyEntities(p.getLocation(), 15.0D, 15.0D, 15.0D)) {
                            if (ent instanceof LivingEntity le && !(ent instanceof Player) && !(ent instanceof ArmorStand)) {
                                le.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 60, 0, false, false, false));
                            }
                            if (ent instanceof Warden warden && p.equals(warden.getTarget())) {
                                warden.setTarget(null);
                                warden.clearAnger(p);
                            }
                        }
                    }
                    if (isLegendary(p.getInventory().getHelmet(), ID_HAPPY_MASK)) {
                        p.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 100, 0, false, false, false));
                        p.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, 300, 0, false, false, false));
                    }
                }
            }
        }.runTaskTimer(this, 0L, 40L);

        new BukkitRunnable() {
            @Override
            public void run() {
                long now = System.currentTimeMillis();
                for (Player p : getServer().getOnlinePlayers()) {
                    UUID id = p.getUniqueId();
                    if (!isLegendary(p.getInventory().getHelmet(), ID_DEFAULT_MASK)) {
                        maskRunUntil.remove(id);
                        setRunBoost(p, false);
                        continue;
                    }
                    Long untilBefore = maskRunUntil.get(id);
                    boolean wasActive = untilBefore != null && now < untilBefore;
                    if (p.isSneaking()) {
                        Long start = maskSneakStart.get(id);
                        if (start == null) {
                            start = now;
                            maskSneakStart.put(id, now);
                        }
                        long heldMs = now - start;
                        if (heldMs >= MASK_RUN_DELAY) {
                            if (!wasActive) {
                                sendPriorityActionBar(p, Component.text("Бег активирован: +20% скорости!", NamedTextColor.GREEN));
                                p.playSound(p.getLocation(), Sound.ENTITY_BREEZE_WIND_BURST, 1.0F, 1.4F);
                            }
                            maskRunUntil.put(id, now + MASK_RUN_LINGER);
                        } else if (!wasActive) {
                            sendPriorityActionBar(p, Component.text(
                                    String.format(Locale.US, "Заряд бега: %.1f / %d с", heldMs / 1000.0D, 5L),
                                    NamedTextColor.YELLOW));
                        }
                    }
                    Long until = maskRunUntil.get(id);
                    boolean active = until != null && now < until;
                    setRunBoost(p, active);
                    if (active) {
                        p.getWorld().spawnParticle(Particle.CLOUD, p.getLocation().add(0.0D, 0.1D, 0.0D), 2, 0.2D, 0.02D, 0.2D, 0.01D);
                    }
                }
            }
        }.runTaskTimer(this, 0L, 5L);

        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player p : getServer().getOnlinePlayers()) {
                    UUID id = p.getUniqueId();
                    double y = p.getLocation().getY();
                    Double prevY = lastY.put(id, y);
                    boolean falling = prevY != null && y < prevY - 0.05D;
                    boolean masked = isLegendary(p.getInventory().getHelmet(), ID_DEFAULT_MASK);
                    if (masked && !p.isOnGround() && !p.isFlying() && !p.isGliding() && !p.isInWater()
                            && p.getGameMode() != GameMode.SPECTATOR && !lockStates.containsKey(id)) {
                        if (hovering.contains(id)) {
                            if (p.getLocation().getPitch() > 50.0F) {
                                releaseHover(p);
                                fallingFree.add(id);
                                sendPriorityActionBar(p, Component.text("Падение", NamedTextColor.GRAY));
                            } else {
                                p.setFallDistance(0.0F);
                                p.getWorld().spawnParticle(Particle.CLOUD, p.getLocation().add(0.0D, 0.1D, 0.0D), 2, 0.25D, 0.02D, 0.25D, 0.01D);
                                if (p.getTicksLived() % 10 == 0) {
                                    sendPriorityActionBar(p, Component.text("Парение: посмотри вниз, чтобы упасть", NamedTextColor.AQUA));
                                }
                            }
                        } else if (!fallingFree.contains(id) && falling) {
                            RayTraceResult r = p.getWorld().rayTraceBlocks(p.getLocation(), new Vector(0, -1, 0), 21.0D, FluidCollisionMode.NEVER, true);
                            if (r == null) {
                                hovering.add(id);
                                p.setGravity(false);
                                p.setFallDistance(0.0F);
                                p.setVelocity(new Vector(0, 0, 0));
                                p.getWorld().playSound(p.getLocation(), Sound.ENTITY_BREEZE_IDLE_AIR, 1.0F, 1.2F);
                                p.getWorld().spawnParticle(Particle.CLOUD, p.getLocation(), 25, 0.4D, 0.1D, 0.4D, 0.05D);
                            }
                        }
                    } else {
                        releaseHover(p);
                        fallingFree.remove(id);
                    }
                }
            }
        }.runTaskTimer(this, 0L, 1L);

        new BukkitRunnable() {
            @Override
            public void run() {
                long now = System.currentTimeMillis();
                cooldowns.entrySet().removeIf(e -> now - e.getValue() > 600000L);
            }
        }.runTaskTimer(this, 6000L, 6000L);

        new BukkitRunnable() {
            @Override
            public void run() {
                long now = System.currentTimeMillis();
                cooldownHud.entrySet().removeIf(entry -> {
                    Long started = cooldowns.get(entry.getKey());
                    if (started == null) return true;
                    if (now - started < entry.getValue().durationMs()) return false;
                    int separator = entry.getKey().indexOf(':');
                    if (separator > 0) {
                        try {
                            Player player = getServer().getPlayer(UUID.fromString(entry.getKey().substring(0, separator)));
                            if (player != null && player.isOnline()) {
                                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 0.25F, 1.65F);
                            }
                        } catch (IllegalArgumentException ignored) {
                        }
                    }
                    return true;
                });
                for (Player player : getServer().getOnlinePlayers()) {
                    UUID playerId = player.getUniqueId();
                    if (actionBarPriorityUntil.getOrDefault(playerId, 0L) > now) continue;
                    String prefix = playerId + ":";
                    String selected = selectedHudCooldown.get(playerId);
                    if (selected != null && isHudCooldownActive(prefix + selected, now)) {
                        sendCooldownHud(player, selected, now);
                        continue;
                    }
                    selectedHudCooldown.remove(playerId);
                    String next = cooldownHud.keySet().stream()
                            .filter(key -> key.startsWith(prefix) && isHudCooldownActive(key, now))
                            .max((a, b) -> {
                                int byStartTime = Long.compare(cooldowns.getOrDefault(a, 0L), cooldowns.getOrDefault(b, 0L));
                                return byStartTime != 0 ? byStartTime : a.compareTo(b);
                            })
                            .map(key -> key.substring(prefix.length())).orElse(null);
                    if (next != null) {
                        selectedHudCooldown.put(playerId, next);
                        sendCooldownHud(player, next, now);
                    }
                }
            }
        }.runTaskTimer(this, 0L, 3L);
    }

    @Override
    public void onDisable() {
        getServer().getScheduler().cancelTasks(this);
        cooldownHud.clear();
        actionBarPriorityUntil.clear();
        selectedHudCooldown.clear();

        for (UUID id : raDolls.values()) {
            Entity e = getServer().getEntity(id);
            if (e != null) e.remove();
        }
        raDolls.clear();

        for (List<LivingEntity> list : activeTwins.values()) {
            for (LivingEntity ent : list) {
                if (ent != null && ent.isValid()) ent.remove();
            }
        }
        activeTwins.clear();

        for (Map.Entry<UUID, ThrownBoomerang> en : new ArrayList<>(boomerangs.entrySet())) {
            Entity stand = getServer().getEntity(en.getKey());
            ThrownBoomerang tb = en.getValue();
            Player owner = getServer().getPlayer(tb.owner());
            if (owner != null && owner.isOnline() && !owner.isDead()) {
                giveOrDrop(owner, tb.item());
            } else if (stand != null) {
                stand.getWorld().dropItemNaturally(stand.getLocation(), tb.item());
            }
        }
        boomerangs.clear();

        for (TimeSession s : new ArrayList<>(timeSessions.values())) {
            endTimeSession(s, null);
        }
        for (UUID id : new ArrayList<>(lockStates.keySet())) {
            unlockEntity(id);
        }
        for (Entity e : new ArrayList<>(tempEntities)) {
            if (e.isValid()) e.remove();
        }
        tempEntities.clear();

        for (Player p : getServer().getOnlinePlayers()) {
            releaseHover(p);
            setRunBoost(p, false);
        }
        hovering.clear();
        maskSneakStart.clear();
        maskRunUntil.clear();
        saveSouls();
    }

     boolean onCooldown(Player p, String key, long cdMs) {
        return cooldownRemaining(p, key, cdMs) > 0L;
    }

     long cooldownRemaining(Player p, String key, long cdMs) {
        Long last = cooldowns.get(p.getUniqueId() + ":" + key);
        if (last == null) return 0L;
        return Math.max(0L, (last + cdMs) - System.currentTimeMillis());
    }

     void setCooldown(Player p, String key, long durationMs, String displayName) {
        String cooldownKey = p.getUniqueId() + ":" + key;
        cooldowns.put(cooldownKey, System.currentTimeMillis());
        cooldownHud.put(cooldownKey, new HudCooldown(durationMs, displayName));
        selectedHudCooldown.put(p.getUniqueId(), key);
    }

     boolean isHudCooldownActive(String key, long now) {
        HudCooldown hud = cooldownHud.get(key);
        Long started = cooldowns.get(key);
        return hud != null && started != null && now - started < hud.durationMs();
    }

     void sendCooldownHud(Player player, String abilityKey, long now) {
        String key = player.getUniqueId() + ":" + abilityKey;
        HudCooldown hud = cooldownHud.get(key);
        Long started = cooldowns.get(key);
        if (hud == null || started == null) return;
        long remaining = Math.max(0L, hud.durationMs() - (now - started));
        if (remaining == 0L || hud.durationMs() <= 0L) return;
        double progress = Math.max(0.0D, Math.min(1.0D, remaining / (double) hud.durationMs()));
        int filled = (int) Math.round(progress * COOLDOWN_BAR_LENGTH);
        String bar = "▰".repeat(filled) + "▱".repeat(COOLDOWN_BAR_LENGTH - filled);
        Component text = Component.text("⚔ " + hud.name() + "  " + String.format(Locale.US, "%.1fs", remaining / 1000.0D) + "  ", NamedTextColor.GRAY)
                .append(Component.text(bar.substring(0, filled), NamedTextColor.AQUA))
                .append(Component.text(bar.substring(filled), NamedTextColor.DARK_GRAY));
        player.sendActionBar(text);
    }

    // Every existing ActionBar notification receives a two-second window before the HUD resumes.
     void sendPriorityActionBar(Player player, Component message) {
        actionBarPriorityUntil.put(player.getUniqueId(), System.currentTimeMillis() + 2000L);
        player.sendActionBar(message);
    }

     boolean isCraftingCore(String id) {
        return ID_CB_ORANGE.equals(id) || ID_CB_PURPLE.equals(id) || ID_CB_GREEN.equals(id);
    }

     void protectDroppedItem(Item item) {
        if (item == null || getId(item.getItemStack()) == null) return;
        item.setInvulnerable(true);
        item.setUnlimitedLifetime(true);
        item.setGlowing(true);
        item.setWillAge(false);
        ItemMeta droppedMeta = item.getItemStack().getItemMeta();
        if (droppedMeta != null && droppedMeta.hasDisplayName()) {
            item.customName(droppedMeta.displayName());
            item.setCustomNameVisible(true);
        }
    }

     void stampId(ItemStack stack, String id) {
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.getPersistentDataContainer().set(idKey, PersistentDataType.STRING, id);
            stack.setItemMeta(meta);
        }
    }

     String getId(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) return null;
        ItemMeta meta = stack.getItemMeta();
        return meta == null ? null : meta.getPersistentDataContainer().get(idKey, PersistentDataType.STRING);
    }

     boolean isLegendary(ItemStack stack, String id) {
        if (stack == null || !stack.hasItemMeta()) return false;
        return id.equals(getId(stack));
    }

     Attribute attackDamageAttribute() {
        Attribute a = Registry.ATTRIBUTE.get(NamespacedKey.minecraft("attack_damage"));
        return a != null ? a : Registry.ATTRIBUTE.get(NamespacedKey.minecraft("generic.attack_damage"));
    }

     void applyBaseDamage(ItemStack stack, double bonus, String id) {
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return;
        Attribute atk = attackDamageAttribute();
        if (atk == null) return;
        AttributeModifier mod = new AttributeModifier(new NamespacedKey(this, "legendary_dmg_" + id),
                bonus, Operation.ADD_NUMBER, EquipmentSlotGroup.MAINHAND);
        meta.addAttributeModifier(atk, mod);
        stack.setItemMeta(meta);
    }

     void setLore(ItemMeta meta, String... lines) {
        List<Component> l = new ArrayList<>();
        l.add(Component.text("✦ ЛЕГЕНДАРНЫЙ АРТЕФАКТ ✦", NamedTextColor.LIGHT_PURPLE)
                .decorate(TextDecoration.BOLD).decoration(TextDecoration.ITALIC, false));
        l.add(Component.empty());
        for (String s : lines) {
            if (s.isEmpty()) {
                l.add(Component.empty());
            } else if (s.startsWith("~")) {
                l.add(Component.text(s.substring(1)).color(NamedTextColor.DARK_PURPLE).decoration(TextDecoration.ITALIC, true));
            } else if (s.startsWith("#")) {
                l.add(Component.text(s.substring(1)).color(NamedTextColor.LIGHT_PURPLE)
                        .decorate(TextDecoration.BOLD).decoration(TextDecoration.ITALIC, false));
            } else {
                l.add(Component.text(s).color(NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
            }
        }
        meta.lore(l);
    }

    @SuppressWarnings("deprecation")
     void applyCustomModelData(ItemMeta meta, int cmd) {
        try {
            meta.setCustomModelData(cmd);
        } catch (Throwable ignored) {
        }
        try {
            CustomModelDataComponent component = meta.getCustomModelDataComponent();
            component.setFloats(List.of((float) cmd));
            meta.setCustomModelDataComponent(component);
        } catch (Throwable ignored) {
        }
    }

     ItemStack base(Material mat, int cmd, String id, String name, boolean weapon) {
        ItemStack stack = new ItemStack(mat);
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return stack;
        applyCustomModelData(meta, cmd);
        if (name.contains("<")) {
            meta.displayName(mm.deserialize(name).decoration(TextDecoration.ITALIC, false));
        } else {
            String[] colors = switch (id) {
                case ID_RA_BLADE -> new String[]{"#fff3a3", "#ff8c00", "☀"};
                case ID_CHAOS_BOOMERANG -> new String[]{"#ff4d9d", "#8a2be2", "⟲"};
                case ID_WARDEN_HELMET -> new String[]{"#83a4d4", "#b6fbff", "◉"};
                case ID_TWINS_SWORD -> new String[]{"#ff758c", "#7b2cbf", "⚔"};
                case ID_MAGNET -> new String[]{"#00d2ff", "#3a7bd5", "✧"};
                case ID_TIME_CLOCK -> new String[]{"#a18cd1", "#fbc2eb", "⌛"};
                case ID_ICE_SPEAR, ID_ICE_SPEAR_ICE, ID_ICE_SPEAR_SPIKE -> new String[]{"#83a4d4", "#b6fbff", "❄"};
                case ID_DEFAULT_MASK, ID_MASK_BLOCK -> new String[]{"#434343", "#d7d2cc", "☠"};
                case ID_HAPPY_MASK -> new String[]{"#fddb92", "#d1fdff", "☻"};
                case ID_MASK_MACE -> new String[]{"#f7971e", "#ffd200", "✹"};
                case ID_MASK_TOMPSON -> new String[]{"#f953c6", "#b91d73", "✦"};
                case ID_SOUL_BOW -> new String[]{"#8e2de2", "#4a00e0", "♜"};
                default -> new String[]{"#f6d365", "#fda085", "✦"};
            };
            meta.displayName(mm.deserialize("<gradient:" + colors[0] + ":" + colors[1] + "><bold>" + colors[2] + " " + name + "</bold></gradient>")
                    .decoration(TextDecoration.ITALIC, false));
        }
        meta.setEnchantmentGlintOverride(true);
        meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ATTRIBUTES);
        stack.setItemMeta(meta);
        stampId(stack, id);
        if (weapon) applyBaseDamage(stack, 4.0D, id);
        return stack;
    }

     Material getSpearMaterial() {
        for (String candidate : List.of("NETHERITE_SPEAR", "TRIDENT", "NETHERITE_SWORD")) {
            try {
                return Material.valueOf(candidate);
            } catch (IllegalArgumentException ignored) {
            }
        }
        return Material.NETHERITE_SWORD;
    }

     <T extends Entity> T track(T e) {
        tempEntities.add(e);
        return e;
    }

     void removeTracked(Entity e) {
        tempEntities.remove(e);
        if (e != null && e.isValid()) e.remove();
    }

     void giveOrDrop(Player p, ItemStack item) {
        Map<Integer, ItemStack> left = p.getInventory().addItem(item);
        for (ItemStack s : left.values()) {
            p.getWorld().dropItemNaturally(p.getLocation(), s);
        }
    }

     boolean isLocked(Player p) {
        return lockStates.containsKey(p.getUniqueId());
    }

     boolean isAbilityClick(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return false;
        Player p = e.getPlayer();
        if (isLocked(p)) return false;
        Action a = e.getAction();
        if (a != Action.RIGHT_CLICK_AIR && a != Action.RIGHT_CLICK_BLOCK) return false;

        Block b = e.getClickedBlock();
        if (b != null && b.getType().isInteractable()) {
            if (!p.isSneaking() || e.getItem() == null || e.getItem().getType().isAir()) {
                return false;
            }
        }
        return true;
    }

     boolean checkAndMarkTick(Player p, String ability) {
        int tick = getServer().getCurrentTick();
        String key = p.getUniqueId() + ":" + ability;
        Integer prev = lastInteractTick.put(key, tick);
        return prev == null || prev != tick;
    }

     boolean isTargetable(Entity ent, Player self) {
        if (!(ent instanceof LivingEntity le)) return false;
        if (self != null && ent.equals(self)) return false;
        if (ent instanceof ArmorStand || ent.hasMetadata(META_RA_DOLL) || ent.hasMetadata(META_TWIN)) return false;
        if (le.isDead() || !le.isValid()) return false;
        if (le instanceof Player pl && pl.getGameMode() == GameMode.SPECTATOR) return false;
        if (self != null && le instanceof Tameable t && self.getUniqueId().equals(t.getOwnerUniqueId())) return false;
        return true;
    }

     Vector sideVector(Vector dir) {
        Vector side = new Vector(-dir.getZ(), 0.0D, dir.getX());
        return side.lengthSquared() < 1.0E-6D ? new Vector(1, 0, 0) : side.normalize();
    }

     Vector flatDir(float yaw) {
        double r = Math.toRadians(yaw);
        return new Vector(-Math.sin(r), 0.0D, Math.cos(r));
    }

     Vector flatRight(float yaw) {
        double r = Math.toRadians(yaw);
        return new Vector(-Math.cos(r), 0.0D, -Math.sin(r));
    }

     float lerpAngle(float a, float b, float t) {
        float d = ((b - a + 540.0F) % 360.0F) - 180.0F;
        return a + d * t;
    }

     void markCustomDamage(LivingEntity victim, Player killer, String reason, double damage, boolean trueDmg) {
        if (victim.isDead()) return;
        victim.setMetadata(META_DEATH_REASON, new FixedMetadataValue(this, reason + ":" + (killer != null ? killer.getName() : "Игрок")));
        try {
            if (trueDmg) {
                trueDamage(victim, damage, killer);
            } else {
                victim.setNoDamageTicks(0);
                victim.damage(damage, killer);
            }
        } finally {
            if (!victim.isDead()) victim.removeMetadata(META_DEATH_REASON, this);
        }
    }

     void trueDamage(LivingEntity v, double amount, Player src) {
        if (v.isDead()) return;
        if (v instanceof Player pl && (pl.getGameMode() == GameMode.CREATIVE || pl.getGameMode() == GameMode.SPECTATOR)) {
            return;
        }
        LockState st = lockStates.get(v.getUniqueId());
        if (st != null && st.invulnerable()) return;

        double before = v.getHealth();
        v.setLastDamageCause(null);
        v.setNoDamageTicks(0);
        v.damage(0.01D, src);
        v.setNoDamageTicks(0);

        EntityDamageEvent last = v.getLastDamageCause();
        if (v.isDead() || last == null || last.isCancelled()) return;

        AttributeInstance mx = v.getAttribute(Attribute.MAX_HEALTH);
        double cap = mx != null ? mx.getValue() : before;
        double nextHp = before - amount;
        if (nextHp <= 0.0D) {
            v.setNoDamageTicks(0);
            v.setHealth(0.0D);
        } else {
            v.setHealth(Math.min(cap, nextHp));
        }
    }

     void spawnBoom(World w, Location loc) {
        w.spawnParticle(Particle.EXPLOSION, loc.clone().add(0.0D, 1.0D, 0.0D), 1);
        w.spawnParticle(Particle.FLASH, loc.clone().add(0.0D, 1.0D, 0.0D), 1, org.bukkit.Color.ORANGE);
        final TextDisplay boom = w.spawn(loc.clone().add(0.0D, 1.4D, 0.0D), TextDisplay.class, td -> {
            td.text(Component.text("БУМ!", NamedTextColor.RED, TextDecoration.BOLD));
            td.setBillboard(Display.Billboard.CENTER);
            td.setShadowed(false);
            td.setSeeThrough(false);
            td.setDefaultBackground(false);
            td.setPersistent(false);
        });
        track(boom);
        new BukkitRunnable() {
            @Override
            public void run() {
                removeTracked(boom);
            }
        }.runTaskLater(this, 12L);
    }

     ItemDisplay spawnModel(Location loc, ItemStack model, Vector3f translation, Vector3f scale) {
        ItemDisplay d = loc.getWorld().spawn(loc, ItemDisplay.class, disp -> {
            disp.setItemStack(model);
            disp.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
            disp.setPersistent(false);
            disp.setBrightness(new Display.Brightness(15, 15));
            disp.setTransformation(new Transformation(translation, new AxisAngle4f(), scale, new AxisAngle4f()));
        });
        return track(d);
    }

     Location groundBelow(Location l) {
        RayTraceResult r = l.getWorld().rayTraceBlocks(l.clone().add(0.0D, 1.0D, 0.0D), new Vector(0, -1, 0), 12.0D, FluidCollisionMode.NEVER, true);
        Location res = r != null ? r.getHitPosition().toLocation(l.getWorld()) : l.clone();
        res.setYaw((float) (Math.random() * 360.0D));
        res.setPitch(0.0F);
        return res;
    }

     void spawnSpike(Location loc, float scale) {
        Location ground = groundBelow(loc);
        World w = ground.getWorld();
        if (w == null) return;
        ItemDisplay spike = spawnModel(
                ground.clone().subtract(0.0D, 1.2D * scale, 0.0D),
                iceSpearSpike(),
                new Vector3f(0.0F, 0.0F, 0.0F),
                new Vector3f(scale, scale, scale)
        );
        w.spawnParticle(Particle.SNOWFLAKE, ground.clone().add(0.0D, 0.5D, 0.0D), 12, 0.3D, 0.4D, 0.3D, 0.02D);
        new BukkitRunnable() {
            int t = 0;

            @Override
            public void run() {
                t++;
                if (!spike.isValid() || t > 60) {
                    removeTracked(spike);
                    cancel();
                    return;
                }
                if (t <= 6) {
                    double prog = t / 6.0D;
                    spike.teleport(ground.clone().subtract(0.0D, (1.0D - prog) * 1.2D * scale, 0.0D));
                } else if (t >= 50) {
                    double prog = (t - 50) / 10.0D;
                    spike.teleport(ground.clone().subtract(0.0D, prog * 1.2D * scale, 0.0D));
                }
            }
        }.runTaskTimer(this, 0L, 1L);
    }

     void fireThompsonShot(Player p, Location origin, Vector dir, String metaKey) {
        World w = origin.getWorld();
        if (w == null) return;
        Vector velocity = dir.clone().normalize().add(new Vector(
                (Math.random() - 0.5D) * 0.06D,
                (Math.random() - 0.5D) * 0.06D,
                (Math.random() - 0.5D) * 0.06D
        )).normalize().multiply(2.8D);
        Snowball bullet = w.spawn(origin, Snowball.class, s -> {
            s.setShooter(p);
            s.setVelocity(velocity);
            s.setGravity(false);
            s.setMetadata(metaKey, new FixedMetadataValue(this, true));
        });
        track(bullet);
        w.spawnParticle(Particle.SMOKE, origin, 4, 0.05D, 0.05D, 0.05D, 0.01D);
        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (!bullet.isValid() || bullet.isDead() || ticks++ > 60) {
                    removeTracked(bullet);
                    cancel();
                } else {
                    bullet.getWorld().spawnParticle(Particle.CRIT, bullet.getLocation(), 1, 0.0D, 0.0D, 0.0D, 0.0D);
                }
            }
        }.runTaskTimer(this, 1L, 1L);
    }

     void explodeSmall(Location loc, Player shooter, double damage, double radius) {
        World w = loc.getWorld();
        if (w == null) return;
        spawnBoom(w, loc);
        w.playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 0.8F, 1.4F);
        double rSq = radius * radius;
        for (Entity ent : w.getNearbyEntities(loc, radius, radius, radius)) {
            if (!isTargetable(ent, shooter)) continue;
            if (ent.getLocation().distanceSquared(loc) > rSq) continue;
            LivingEntity le = (LivingEntity) ent;
            markCustomDamage(le, shooter, "mask_thompson", damage, false);
        }
    }

     void setRunBoost(Player p, boolean on) {
        AttributeInstance inst = p.getAttribute(Attribute.MOVEMENT_SPEED);
        if (inst == null) return;
        AttributeModifier existing = null;
        for (AttributeModifier m : inst.getModifiers()) {
            if (runKey.equals(m.getKey())) {
                existing = m;
                break;
            }
        }
        if (on && existing == null) {
            inst.addModifier(new AttributeModifier(runKey, 0.2D, Operation.ADD_SCALAR, EquipmentSlotGroup.ANY));
        } else if (!on && existing != null) {
            inst.removeModifier(existing);
        }
    }

     void releaseHover(Player p) {
        if (hovering.remove(p.getUniqueId())) {
            p.setGravity(true);
        }
    }

     boolean lockEntity(LivingEntity le, boolean invulnerable) {
        UUID id = le.getUniqueId();
        if (lockStates.containsKey(id)) return false;
        lockStates.put(id, new LockState(le.hasAI(), le.hasGravity(), invulnerable));
        le.setGravity(false);
        le.setFallDistance(0.0F);
        if (!(le instanceof Player)) {
            le.setAI(false);
            le.getPersistentDataContainer().set(frozenKey, PersistentDataType.BYTE, (byte) 1);
        }
        le.setVelocity(new Vector(0, 0, 0));
        return true;
    }

     void unlockEntity(UUID id) {
        LockState st = lockStates.remove(id);
        if (st == null) return;
        Entity ent = getServer().getEntity(id);
        if (ent instanceof LivingEntity le) {
            le.setGravity(st.gravity());
            le.setFallDistance(0.0F);
            if (!(le instanceof Player)) {
                le.setAI(st.ai());
                le.getPersistentDataContainer().remove(frozenKey);
            }
        }
    }

     void restoreStaleFrozen(Entity ent) {
        if (ent instanceof LivingEntity le && !(ent instanceof Player)) {
            if (!le.getPersistentDataContainer().has(frozenKey, PersistentDataType.BYTE)) return;
            if (lockStates.containsKey(le.getUniqueId())) return;
            le.setAI(true);
            le.setGravity(true);
            le.getPersistentDataContainer().remove(frozenKey);
        }
    }

     void startTimeSession(final Player owner, final LivingEntity target) {
        final TimeSession s = new TimeSession(owner.getUniqueId(), target.getUniqueId());
        timeSessions.put(s.owner, s);
        timeByTarget.put(s.target, s);
        setCooldown(owner, "time_clock", 180000L, "Часы времени");
        sendPriorityActionBar(owner, Component.text("Часы записывают движения цели: 10 секунд...", NamedTextColor.AQUA));
        owner.getWorld().playSound(owner.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 1.0F, 1.8F);
        s.recordTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (target.isValid() && !target.isDead() && owner.isOnline()) {
                    s.frames.add(new TimeFrame(target.getLocation().clone(), target.getHealth()));
                    if (s.frames.size() >= 200) {
                        cancel();
                        beginTimeFreeze(s, owner, target);
                    }
                } else {
                    endTimeSession(s, "Запись прервана: цель потеряна.");
                }
            }
        }.runTaskTimer(this, 0L, 1L);
    }

     void beginTimeFreeze(TimeSession s, Player owner, LivingEntity target) {
        if (!lockEntity(target, true)) {
            endTimeSession(s, "Не удалось остановить цель.");
            return;
        }
        s.frozen = true;
        s.index = s.frames.size() - 1;
        target.getWorld().spawnParticle(Particle.END_ROD, target.getLocation().add(0.0D, 1.0D, 0.0D), 40, 0.4D, 0.8D, 0.4D, 0.05D);
        target.getWorld().playSound(target.getLocation(), Sound.BLOCK_BEACON_DEACTIVATE, 1.0F, 0.5F);
        if (owner.isOnline()) {
            sendPriorityActionBar(owner, Component.text("Время остановлено! Колёсико: листать кадры, ПКМ: вернуть время.", NamedTextColor.AQUA));
        }
        s.timeoutTask = getServer().getScheduler().runTaskLater(this,
                () -> endTimeSession(s, "Время цели возобновилось само."), 3600L);
    }

     void endTimeSession(TimeSession s, String message) {
        if (s.recordTask != null) s.recordTask.cancel();
        if (s.timeoutTask != null) s.timeoutTask.cancel();
        timeSessions.remove(s.owner);
        timeByTarget.remove(s.target);
        if (s.frozen) {
            unlockEntity(s.target);
            Entity t = getServer().getEntity(s.target);
            if (t != null) {
                t.getWorld().spawnParticle(Particle.END_ROD, t.getLocation().add(0.0D, 1.0D, 0.0D), 25, 0.3D, 0.6D, 0.3D, 0.05D);
            }
        }
        s.frozen = false;
        Player o = getServer().getPlayer(s.owner);
        if (o != null && message != null) {
            sendPriorityActionBar(o, Component.text(message, NamedTextColor.YELLOW));
        }
    }

     void scrubTime(TimeSession s, Player owner, int delta) {
        if (s.frames.isEmpty()) return;
        Entity t = getServer().getEntity(s.target);
        if (t == null || !t.isValid()) {
            endTimeSession(s, "Цель потеряна.");
            return;
        }
        s.index = Math.max(0, Math.min(s.frames.size() - 1, s.index + delta));
        TimeFrame frame = s.frames.get(s.index);
        t.teleport(frame.location());
        if (t instanceof LivingEntity living) {
            double maxHealth = living.getAttribute(Attribute.MAX_HEALTH) == null
                    ? living.getHealth() : living.getAttribute(Attribute.MAX_HEALTH).getValue();
            living.setHealth(Math.max(0.0D, Math.min(frame.health(), maxHealth)));
        }
        double back = (s.frames.size() - 1 - s.index) / 20.0D;
        sendPriorityActionBar(owner, Component.text(
                String.format(Locale.US, "Кадр %d/%d (-%.2f с)", s.index + 1, s.frames.size(), back), NamedTextColor.AQUA));
        owner.playSound(owner.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.5F, 1.0F + (float) s.index / (float) s.frames.size());
    }

     int getSouls(UUID id) {
        return souls.getOrDefault(id, 0);
    }

     boolean isSoulless(UUID id) {
        return soulless.containsKey(id);
    }

     void loadSouls() {
        synchronized (fileLock) {
            soulFile = new File(getDataFolder(), "souls.yml");
            YamlConfiguration y = YamlConfiguration.loadConfiguration(soulFile);
            ConfigurationSection sec = y.getConfigurationSection("souls");
            if (sec != null) {
                for (String k : sec.getKeys(false)) {
                    try {
                        souls.put(UUID.fromString(k), sec.getInt(k));
                    } catch (IllegalArgumentException ignored) {
                    }
                }
            }
            sec = y.getConfigurationSection("soulless");
            if (sec != null) {
                for (String k : sec.getKeys(false)) {
                    try {
                        soulless.put(UUID.fromString(k), UUID.fromString(sec.getString(k, "")));
                    } catch (IllegalArgumentException ignored) {
                    }
                }
            }
            sec = y.getConfigurationSection("revived");
            if (sec != null) {
                for (String k : sec.getKeys(false)) {
                    try {
                        revived.put(UUID.fromString(k), UUID.fromString(sec.getString(k, "")));
                    } catch (IllegalArgumentException ignored) {
                    }
                }
            }
            sec = y.getConfigurationSection("revivers");
            if (sec != null) {
                for (String k : sec.getKeys(false)) {
                    try {
                        revivers.put(UUID.fromString(k), UUID.fromString(sec.getString(k, "")));
                    } catch (IllegalArgumentException ignored) {
                    }
                }
            }
            for (String s : y.getStringList("pending")) {
                try {
                    pendingNormal.add(UUID.fromString(s));
                } catch (IllegalArgumentException ignored) {
                }
            }
            craftedUniques.addAll(y.getStringList("crafted_uniques"));
        }
    }

     void saveSouls() {
        synchronized (fileLock) {
            YamlConfiguration y = new YamlConfiguration();
            souls.forEach((k, v) -> y.set("souls." + k, v));
            soulless.forEach((k, v) -> y.set("soulless." + k, v.toString()));
            revived.forEach((k, v) -> y.set("revived." + k, v.toString()));
            revivers.forEach((k, v) -> y.set("revivers." + k, v.toString()));
            List<String> pend = new ArrayList<>();
            for (UUID u : pendingNormal) pend.add(u.toString());
            y.set("pending", pend);
            y.set("crafted_uniques", new ArrayList<>(craftedUniques));
            try {
                getDataFolder().mkdirs();
                y.save(soulFile);
            } catch (IOException ex) {
                getLogger().warning("Не удалось сохранить souls.yml: " + ex.getMessage());
            }
        }
    }

     void setHealthModifier(Player p, String keyName, double amount, boolean on) {
        AttributeInstance inst = p.getAttribute(Attribute.MAX_HEALTH);
        if (inst == null) return;
        NamespacedKey key = new NamespacedKey(this, keyName);
        AttributeModifier existing = null;
        for (AttributeModifier m : inst.getModifiers()) {
            if (key.equals(m.getKey())) {
                existing = m;
                break;
            }
        }
        if (existing != null) {
            inst.removeModifier(existing);
        }
        if (on) {
            inst.addModifier(new AttributeModifier(key, amount, Operation.ADD_NUMBER, EquipmentSlotGroup.ANY));
        }
        if (p.getHealth() > inst.getValue()) {
            p.setHealth(Math.max(1.0D, inst.getValue()));
        }
    }

     void clearHealthModifiersByPrefix(Player p, String prefix) {
        AttributeInstance inst = p.getAttribute(Attribute.MAX_HEALTH);
        if (inst == null) return;
        for (AttributeModifier m : new ArrayList<>(inst.getModifiers())) {
            if (m.getKey().getNamespace().equals(getName().toLowerCase(Locale.ROOT))
                    && m.getKey().getKey().startsWith(prefix)) {
                inst.removeModifier(m);
            }
        }
    }

     void applySoulless(Player p) {
        if (p.getGameMode() != GameMode.SPECTATOR) {
            p.setGameMode(GameMode.SPECTATOR);
        }
        p.sendMessage(Component.text("Ваша душа похищена. Вас может возродить другой игрок командой /revive " + p.getName(), NamedTextColor.DARK_PURPLE));
    }

     void applyNormal(Player p) {
        setHealthModifier(p, "soul_cap", -10.0D, false);
        clearHealthModifiersByPrefix(p, "soul_gift_");
        if (p.getGameMode() == GameMode.SPECTATOR) {
            p.setGameMode(GameMode.SURVIVAL);
            Location loc = p.getRespawnLocation();
            if (loc == null) loc = p.getWorld().getSpawnLocation();
            p.teleport(loc);
            AttributeInstance max = p.getAttribute(Attribute.MAX_HEALTH);
            p.setHealth(max != null ? max.getValue() : 20.0D);
        }
        p.sendMessage(Component.text("Лук душ потерял владельца: ваше состояние восстановлено.", NamedTextColor.GREEN));
    }

     void normalize(UUID id) {
        soulless.remove(id);
        revived.remove(id);
        revivers.remove(id);
        Player p = getServer().getPlayer(id);
        if (p == null) {
            pendingNormal.add(id);
        } else {
            applyNormal(p);
        }
    }

     void releaseAllFrom(UUID owner) {
        souls.remove(owner);
        Player ownerPlayer = getServer().getPlayer(owner);
        if (ownerPlayer != null) {
            setHealthModifier(ownerPlayer, "soul_consume", 0.0D, false);
        }
        for (UUID v : new ArrayList<>(soulless.keySet())) {
            if (owner.equals(soulless.get(v))) normalize(v);
        }
        for (UUID v : new ArrayList<>(revived.keySet())) {
            if (owner.equals(revived.get(v))) normalize(v);
        }
        for (UUID r : new ArrayList<>(revivers.keySet())) {
            if (owner.equals(revivers.get(r))) normalize(r);
        }
        saveSouls();
    }

     boolean hasSoulDependents(UUID owner) {
        if (getSouls(owner) > 0) return true;
        return soulless.containsValue(owner) || revived.containsValue(owner) || revivers.containsValue(owner);
    }

     void takeSoul(UUID owner, UUID victim) {
        revived.remove(victim);
        Player vp = getServer().getPlayer(victim);
        if (vp != null) {
            setHealthModifier(vp, "soul_cap", -10.0D, false);
        }
        soulless.put(victim, owner);
        souls.put(owner, Math.min(MAX_SOULS, getSouls(owner) + 1));
        saveSouls();
        Player op = getServer().getPlayer(owner);
        if (op != null) {
            sendPriorityActionBar(op, Component.text("Душа похищена! Душ: " + getSouls(owner) + "/5", NamedTextColor.DARK_PURPLE));
        }
    }

    ItemStack cbOrange() {
        ItemStack s = base(Material.COMMAND_BLOCK, 30001, ID_CB_ORANGE, "<gradient:#FF8008:#FFC837><bold>✴ Импульсное Ядро Древних</bold></gradient>", false);
        ItemMeta m = s.getItemMeta();
        setLore(m, "~Сгусток первозданной энергии, пульсирующий в такт сердцу мира.", "",
                "#ПРИМЕНЕНИЕ", "Рецепты легендарных артефактов; наковальня:",
                "10% шанс повысить случайное зачарование на уровень.", "При неудаче теряется 80% оставшейся прочности.");
        s.setItemMeta(m);
        return s;
    }

    ItemStack cbPurple() {
        ItemStack s = base(Material.REPEATING_COMMAND_BLOCK, 30002, ID_CB_PURPLE, "<gradient:#8E2DE2:#4A00E0><bold>۞ Ядро Вечного Цикла</bold></gradient>", false);
        ItemMeta m = s.getItemMeta();
        setLore(m, "~Бесконечный вихрь хроно-материи, способный искажать реальность.", "",
                "#ПРИМЕНЕНИЕ", "Рецепты легендарных артефактов; наковальня:",
                "15% шанс повысить случайное зачарование на уровень.", "При неудаче теряется 80% оставшейся прочности.");
        s.setItemMeta(m);
        return s;
    }

    ItemStack cbGreen() {
        ItemStack s = base(Material.CHAIN_COMMAND_BLOCK, 30003, ID_CB_GREEN, "<gradient:#11998e:#38ef7d><bold>❖ Цепное Ядро Сингулярности</bold></gradient>", false);
        ItemMeta m = s.getItemMeta();
        setLore(m, "~Холодный кристалл абсолютного порядка, связывающий стихии воедино.", "",
                "#ПРИМЕНЕНИЕ", "Рецепты легендарных артефактов; наковальня:",
                "17% шанс повысить случайное зачарование на уровень.", "При неудаче теряется 80% оставшейся прочности.");
        s.setItemMeta(m);
        return s;
    }

    ItemStack raBlade() {
        ItemStack s = base(Material.NETHERITE_SWORD, CMD_RA_BLADE, ID_RA_BLADE, "<gradient:#FFD700:#FF4500><bold>☀ Гелиос, Клинок Бога Ра</bold></gradient>", true);
        ItemMeta m = s.getItemMeta();
        setLore(m, "~Ослепительный свет древнего божества заточён в этом лезвии.", "",
                "#АКТИВАЦИЯ", "ПКМ: призвать или отозвать манекен Ра (кд 60с).",
                "Манекен стоит 45 сек. Удар по нему отражает",
                "весь урон на всех врагов вокруг в 10 блоках.");
        s.setItemMeta(m);
        return s;
    }

    ItemStack chaosBoomerang() {
        ItemStack s = base(Material.NETHERITE_SWORD, CMD_CHAOS_BOOMERANG, ID_CHAOS_BOOMERANG, "<gradient:#FF0099:#79095D><bold>🌀 Потрошитель Бездны • Бумеранг Хаоса</bold></gradient>", true);
        ItemMeta m = s.getItemMeta();
        setLore(m, "~Оружие, не знающее покоя. Несёт в себе частицу чистого безумия.", "",
                "#ЗАПУСК", "ПКМ: отправить бумеранг в полёт (кд 8с).",
                "#РИКОШЕТЫ",
                "Отскакивает от блоков и врагов (макс. 10 раз),",
                "совершает минимум 3 и максимум 10 отскоков.",
                "Если рядом нет целей или преград, он",
                "делает воздушные рикошеты, чтобы набрать минимум.",
                "Попадания наносят урон и случайный эффект хаоса.",
                "При отскоках сила удара растёт.",
                "После полёта бумеранг возвращается к владельцу.");
        s.setItemMeta(m);
        return s;
    }

    ItemStack wardenHelmet() {
        ItemStack s = base(Material.CARVED_PUMPKIN, CMD_WARDEN_HELMET, ID_WARDEN_HELMET, "<gradient:#00C9FF:#003366><bold>👁 Корона Глубин • Лик Хранителя</bold></gradient>", false);
        ItemMeta m = s.getItemMeta();
        setLore(m, "~Эхо глубин лишает владельца зрения, но отмечает окружение светом.", "",
                "#ПОКА НАДЕТ", "Слепота владельцу; свечение владельца и существ",
                "рядом (до 15 блоков). Вардены не выбирают владельца целью.",
                "#АКТИВАЦИЯ", "SHIFT+ПКМ: Sonic Boom по направлению взгляда",
                "до 32 блоков (урон 10, отбрасывание; кд 10с).",
                "Луч останавливается о стены; наводиться на моба не нужно.");
        s.setItemMeta(m);
        return s;
    }

    ItemStack twinsSword() {
        ItemStack s = base(Material.NETHERITE_SWORD, CMD_TWINS_SWORD, ID_TWINS_SWORD, "<gradient:#B24592:#F15F79><bold>⚔ Эхо Дуальности • Меч Близнецов</bold></gradient>", true);
        ItemMeta m = s.getItemMeta();
        setLore(m, "~Два лезвия, бьющие как одно целое. От него невозможно защититься.", "",
                "#АКТИВАЦИЯ", "ПКМ: призвать двух близнецов на 30с (кд 30с).",
                "Близнецы следуют за вами и повторяют ваши удары;",
                "каждый активный близнец добавляет 4 урона.",
                "#ПАССИВНО", "25% шанс нанести дополнительный удар.");
        s.setItemMeta(m);
        return s;
    }

    ItemStack magnet() {
        ItemStack s = base(Material.NETHERITE_SWORD, CMD_MAGNET, ID_MAGNET, "<gradient:#FF416C:#3A7BD5><bold>🧲 Полюс Гравитации • Магнитник</bold></gradient>", true);
        ItemMeta m = s.getItemMeta();
        setLore(m, "~Соткан из гравитационных аномалий. Никто не избежит его притяжения.", "",
                "#АКТИВАЦИЯ", "ПКМ: притянуть врагов к точке взгляда перед вами.",
                "SHIFT+ПКМ: оттолкнуть врагов и вернуть стрелы, трезубцы",
                "и фаерболы в сторону их стрелков.",
                "Перезарядка: 5с.");
        s.setItemMeta(m);
        return s;
    }

    ItemStack timeClock() {
        ItemStack s = base(Material.NETHERITE_SWORD, CMD_TIME_CLOCK, ID_TIME_CLOCK, "<gradient:#00F2FE:#4FACFE><bold>⏳ Хроносфера • Часы Вечности</bold></gradient>", true);
        ItemMeta m = s.getItemMeta();
        setLore(m, "~Шестерёнки судьбы тикают внутри, позволяя повелевать самим временем.", "",
                "#ЗАПИСЬ", "ЛКМ по сущности: записать 10 секунд её движений,",
                "затем остановка до 3 минут. Цель неуязвима.",
                "#УПРАВЛЕНИЕ", "Колёсико мыши: листать записанные кадры",
                "(SHIFT: сразу по 10 кадров).",
                "ПКМ: вернуть время с выбранного кадра (кд 3 мин).");
        s.setItemMeta(m);
        return s;
    }

    ItemStack iceSpear() {
        ItemStack s = base(getSpearMaterial(), CMD_ICE_SPEAR, ID_ICE_SPEAR, "<gradient:#83a4d4:#b6fbff><bold>❄ Гласиус • Копьё Вечной Мерзлоты</bold></gradient>", true);
        ItemMeta m = s.getItemMeta();
        setLore(m, "~Сковано в сердце вечной мерзлоты. Его прикосновение останавливает жизнь.", "",
                "#АКТИВАЦИЯ", "1-й слот + SHIFT+ПКМ: вызвать морозные пики (кд 75с).",
                "#ПАССИВНО", "2-й слот: удар замораживает цель на 3с",
                "(перезарядка эффекта 6с).",
                "Зачарование Выпад IV (Lunge IV).");
        if (m != null) {
            Enchantment lunge = Registry.ENCHANTMENT.get(NamespacedKey.minecraft("lunge"));
            if (lunge != null) {
                m.addEnchant(lunge, 4, true);
            } else {
                m.addEnchant(Enchantment.RIPTIDE, 4, true);
            }
        }
        s.setItemMeta(m);
        return s;
    }

    ItemStack iceSpearIce() {
        ItemStack s = base(Material.NETHERITE_SWORD, CMD_ICE_SPEAR_ICE, ID_ICE_SPEAR_ICE, "<aqua>Лёд копья</aqua>", true);
        ItemMeta m = s.getItemMeta();
        setLore(m, "~След вечной мерзлоты, оставленный Морозным копьём.", "",
                "#АРТЕФАКТ", "Ледяной образ из набора Морозного копья.");
        s.setItemMeta(m);
        return s;
    }

    ItemStack iceSpearSpike() {
        ItemStack s = base(Material.NETHERITE_SWORD, CMD_ICE_SPEAR_SPIKE, ID_ICE_SPEAR_SPIKE, "<aqua>Морозные пики</aqua>", true);
        ItemMeta m = s.getItemMeta();
        setLore(m, "~Осколки льда, пробуждённые силой Морозного копья.", "",
                "#АРТЕФАКТ", "Ледяной образ из набора Морозного копья.");
        s.setItemMeta(m);
        return s;
    }

    ItemStack defaultMask() {
        ItemStack s = base(Material.CARVED_PUMPKIN, CMD_DEFAULT_MASK, ID_DEFAULT_MASK, "<gradient:#56ab2f:#a8e063><bold>🎭 Лик Безумия • Маска Хаоса</bold></gradient>", false);
        ItemMeta m = s.getItemMeta();
        setLore(m, "~Безликая маска, скрывающая истинные намерения и дарующая безумную силу.", "",
                "#КОГДА НАДЕТА",
                "В приседе + F или SHIFT+ПКМ: Молот — сокрушающий",
                "удар радиусом 5 блоков, 10 сердец игрокам (кд 5 мин).",
                "Бег: 5 сек в приседе дают ускорение +20% на 10 сек.",
                "Падение: на высоте более 20 блоков паришь",
                "в воздухе, пока не посмотришь вниз.", "",
                "#В РУКЕ",
                "ПКМ: Томпсоны — парный взрывной выстрел",
                "с уроном по области.");
        s.setItemMeta(m);
        return s;
    }

    ItemStack happyMask() {
        ItemStack s = base(Material.CARVED_PUMPKIN, CMD_HAPPY_MASK, ID_HAPPY_MASK, "<gradient:#FFE000:#799F0C><bold>☺ Эйфория • Счастливая Маска</bold></gradient>", false);
        ItemMeta m = s.getItemMeta();
        setLore(m, "~Её улыбка неестественна, но она дарует невиданную жизненную силу.", "",
                "#ПОКА НАДЕТА", "Регенерация и ночное зрение.");
        s.setItemMeta(m);
        return s;
    }

    ItemStack maskBlock() {
        ItemStack s = base(Material.CARVED_PUMPKIN, CMD_MASK_BLOCK, ID_MASK_BLOCK, "<gold>Блок маски</gold>", false);
        ItemMeta m = s.getItemMeta();
        setLore(m, "~Фрагмент легенды о Маске.", "",
                "#АРТЕФАКТ", "Самостоятельной способности нет.");
        s.setItemMeta(m);
        return s;
    }

    ItemStack maskMace() {
        ItemStack s = base(Material.MACE, CMD_MASK_MACE, ID_MASK_MACE, "<gradient:#FF512F:#DD2476><bold>🔨 Крушитель Черепов • Молот Маски</bold></gradient>", true);
        ItemMeta m = s.getItemMeta();
        setLore(m, "~Игрушечный на вид, но сокрушительно смертоносный на деле.", "",
                "#АРТЕФАКТ", "Булава, используемая как образ молота",
                "во время способности Маски. Отдельной способности нет.");
        s.setItemMeta(m);
        return s;
    }

    ItemStack maskThompson() {
        ItemStack s = base(Material.NETHERITE_SWORD, CMD_MASK_TOMPSON, ID_MASK_TOMPSON, "<gradient:#D31027:#EA384D><bold>🔫 Гнев Чикаго • Парные Томпсоны</bold></gradient>", true);
        ItemMeta m = s.getItemMeta();
        setLore(m, "~Свинцовый дождь с привкусом пороха и хаоса.", "",
                "#АКТИВАЦИЯ", "ПКМ: выстрелить взрывной пулей.",
                "Урон взрыва: 5 в радиусе 2 блоков.",
                "#ИЗНОС", "Каждый выстрел расходует 10 прочности.");
        s.setItemMeta(m);
        return s;
    }

    ItemStack soulBow() {
        ItemStack s = base(Material.BOW, CMD_SOUL_BOW, ID_SOUL_BOW, "<gradient:#43cea2:#185a9d><bold>👻 Шёпот Павших • Лук Душ</bold></gradient>", false);
        ItemMeta m = s.getItemMeta();
        setLore(m, "~Тетива сплетена из стонов павших.", "",
                "#ПОХИЩЕНИЕ ДУШ", "Убийство игрока луком забирает его душу:",
                "жертва теряет тело и ничего не может делать.",
                "#СИЛА ВЫСТРЕЛА", "Урон = 5 + число душ (максимум 5 душ).",
                "Выстрел на 5-й душе тратит 1 душу.",
                "#ДУШИ", "SHIFT+ЛКМ: поглотить душу (+4 сердца).",
                "/revive НИК: возродить бездушного за 5 сердец.",
                "Смерть владельца возвращает всех в норму.");
        s.setItemMeta(m);
        return s;
    }

     void logRecipes() {
        int ok = 0;
        for (String k : RECIPE_KEYS) {
            if (getServer().getRecipe(new NamespacedKey(this, k)) != null) {
                ok++;
            } else {
                getLogger().warning("Рецепт НЕ зарегистрирован: " + k);
            }
        }
        getLogger().info("Рецептов зарегистрировано: " + ok + "/" + RECIPE_KEYS.size());
    }

     void registerRecipes() {
        NamespacedKey rOrange = new NamespacedKey(this, "cb_orange");
        getServer().removeRecipe(rOrange);
        ShapedRecipe cOrange = new ShapedRecipe(rOrange, cbOrange());
        cOrange.shape("CSC", "SNS", "CSC");
        cOrange.setIngredient('C', Material.COPPER_BLOCK);
        cOrange.setIngredient('S', Material.ECHO_SHARD);
        cOrange.setIngredient('N', Material.NETHER_STAR);
        getServer().addRecipe(cOrange);

        NamespacedKey rPurple = new NamespacedKey(this, "cb_purple");
        getServer().removeRecipe(rPurple);
        ShapedRecipe cPurple = new ShapedRecipe(rPurple, cbPurple());
        cPurple.shape("ANA", "NHN", "ANA");
        cPurple.setIngredient('A', Material.AMETHYST_BLOCK);
        cPurple.setIngredient('N', Material.NETHERITE_INGOT);
        cPurple.setIngredient('H', Material.HEART_OF_THE_SEA);
        getServer().addRecipe(cPurple);

        NamespacedKey rGreen = new NamespacedKey(this, "cb_green");
        getServer().removeRecipe(rGreen);
        ShapedRecipe cGreen = new ShapedRecipe(rGreen, cbGreen());
        cGreen.shape("ESE", "STS", "ESE");
        cGreen.setIngredient('E', Material.EMERALD_BLOCK);
        cGreen.setIngredient('S', Material.PRISMARINE_SHARD);
        cGreen.setIngredient('T', Material.TOTEM_OF_UNDYING);
        getServer().addRecipe(cGreen);

        RecipeChoice orangeChoice = new RecipeChoice.ExactChoice(cbOrange());
        RecipeChoice purpleChoice = new RecipeChoice.ExactChoice(cbPurple());
        RecipeChoice greenChoice = new RecipeChoice.ExactChoice(cbGreen());

        NamespacedKey rClock = new NamespacedKey(this, "time_clock");
        getServer().removeRecipe(rClock);
        ShapedRecipe sClock = new ShapedRecipe(rClock, timeClock());
        sClock.shape("SNS", "PCP", "GBG");
        sClock.setIngredient('S', Material.NETHER_STAR);
        sClock.setIngredient('N', Material.NETHERITE_BLOCK);
        sClock.setIngredient('P', purpleChoice);
        sClock.setIngredient('C', Material.CLOCK);
        sClock.setIngredient('G', Material.GOLD_BLOCK);
        sClock.setIngredient('B', orangeChoice);
        getServer().addRecipe(sClock);

        NamespacedKey rSpear = new NamespacedKey(this, "ice_spear");
        getServer().removeRecipe(rSpear);
        ShapedRecipe sSpear = new ShapedRecipe(rSpear, iceSpear());
        sSpear.shape("IBI", "GSG", "INI");
        sSpear.setIngredient('I', Material.PACKED_ICE);
        sSpear.setIngredient('B', orangeChoice);
        sSpear.setIngredient('G', greenChoice);
        sSpear.setIngredient('S', new RecipeChoice.MaterialChoice(Material.TRIDENT, Material.NETHERITE_SWORD));
        sSpear.setIngredient('N', Material.NETHERITE_INGOT);
        getServer().addRecipe(sSpear);

        NamespacedKey rMask = new NamespacedKey(this, "default_mask");
        getServer().removeRecipe(rMask);
        ShapedRecipe sMask = new ShapedRecipe(rMask, defaultMask());
        sMask.shape("CHC", "NSN", "CEC");
        sMask.setIngredient('C', orangeChoice);
        sMask.setIngredient('H', new RecipeChoice.MaterialChoice(Material.NETHERITE_HELMET));
        sMask.setIngredient('N', Material.NETHERITE_BLOCK);
        sMask.setIngredient('S', Material.NETHER_STAR);
        sMask.setIngredient('E', Material.EMERALD_BLOCK);
        getServer().addRecipe(sMask);

        NamespacedKey rWarden = new NamespacedKey(this, "warden_helmet");
        getServer().removeRecipe(rWarden);
        ShapedRecipe sWarden = new ShapedRecipe(rWarden, wardenHelmet());
        sWarden.shape("HSH", "SCS", "N N");
        sWarden.setIngredient('H', new RecipeChoice.MaterialChoice(Material.NETHERITE_HELMET));
        sWarden.setIngredient('S', Material.WITHER_SKELETON_SKULL);
        sWarden.setIngredient('C', Material.SCULK_CATALYST);
        sWarden.setIngredient('N', Material.NETHERITE_INGOT);
        getServer().addRecipe(sWarden);

        NamespacedKey rRa = new NamespacedKey(this, "ra_blade");
        getServer().removeRecipe(rRa);
        ShapedRecipe sRa = new ShapedRecipe(rRa, raBlade());
        sRa.shape("ASA", "TMT", "SBS");
        sRa.setIngredient('A', Material.GOLDEN_APPLE);
        sRa.setIngredient('S', new RecipeChoice.MaterialChoice(Material.NETHERITE_SWORD));
        sRa.setIngredient('T', Material.TOTEM_OF_UNDYING);
        sRa.setIngredient('M', Material.MACE);
        sRa.setIngredient('B', Material.BLAZE_ROD);
        getServer().addRecipe(sRa);

        NamespacedKey rBoom = new NamespacedKey(this, "chaos_boomerang");
        getServer().removeRecipe(rBoom);
        ShapedRecipe sBoom = new ShapedRecipe(rBoom, chaosBoomerang());
        sBoom.shape("DSD", "NTN", "NBN");
        sBoom.setIngredient('D', new RecipeChoice.MaterialChoice(Material.DIAMOND_SWORD));
        sBoom.setIngredient('S', new RecipeChoice.MaterialChoice(Material.NETHERITE_SWORD));
        sBoom.setIngredient('T', new RecipeChoice.MaterialChoice(Material.TRIDENT, Material.NETHERITE_SWORD));
        sBoom.setIngredient('N', Material.NETHERITE_INGOT);
        sBoom.setIngredient('B', Material.BOOK);
        getServer().addRecipe(sBoom);

        NamespacedKey rTwins = new NamespacedKey(this, "twins_sword");
        getServer().removeRecipe(rTwins);
        ShapedRecipe sTwins = new ShapedRecipe(rTwins, twinsSword());
        sTwins.shape("S S", "WNW", "RBR");
        sTwins.setIngredient('S', new RecipeChoice.MaterialChoice(Material.NETHERITE_SWORD));
        sTwins.setIngredient('W', Material.WITHER_SKELETON_SKULL);
        sTwins.setIngredient('N', Material.MUSIC_DISC_5);
        sTwins.setIngredient('R', Material.NETHER_STAR);
        sTwins.setIngredient('B', Material.END_ROD);
        getServer().addRecipe(sTwins);

        NamespacedKey rMag = new NamespacedKey(this, "magnet");
        getServer().removeRecipe(rMag);
        ShapedRecipe sMag = new ShapedRecipe(rMag, magnet());
        sMag.shape("RWB", "RMB", "NIN");
        sMag.setIngredient('R', Material.RED_CONCRETE);
        sMag.setIngredient('B', Material.BLUE_CONCRETE);
        sMag.setIngredient('W', Material.WITHER_SKELETON_SKULL);
        sMag.setIngredient('M', Material.MACE);
        sMag.setIngredient('I', Material.IRON_BLOCK);
        sMag.setIngredient('N', Material.NETHERITE_INGOT);
        getServer().addRecipe(sMag);

        NamespacedKey rHappy = new NamespacedKey(this, "happy_mask");
        getServer().removeRecipe(rHappy);
        ShapedRecipe sHappy = new ShapedRecipe(rHappy, happyMask());
        sHappy.shape("CDC", "DSD", "CDC");
        sHappy.setIngredient('C', Material.NETHER_BRICK);
        sHappy.setIngredient('D', Material.NETHERITE_INGOT);
        sHappy.setIngredient('S', orangeChoice);
        getServer().addRecipe(sHappy);
    }

                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                     

                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                   

                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                              

                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                  

                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                       

                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                     

                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                         

                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                           

                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                 

                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                      

                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                           

                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                               

                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                 

                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                      

                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                               

            private class LegendaryCommand implements CommandExecutor, TabCompleter {
                private final List<String> IDS = List.of(
                        "cb_orange", "cb_purple", "cb_green",
                        "ra_blade", "chaos_boomerang", "warden_helmet", "twins_sword", "magnet", "time_clock",
                        "ice_spear", "ice_spear_ice", "ice_spear_spike", "default_mask", "happy_mask", "mask_block",
                        "mask_mace", "mask_tompson", "soul_bow", "fix");

                @Override
                public boolean onCommand(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String label, String[] args) {
                    if (!(sender instanceof Player p)) {
                        sender.sendMessage("Только для игроков.");
                        return true;
                    }
                    if (!p.hasPermission("legendary.admin")) {
                        p.sendMessage(Component.text("Нет прав.", NamedTextColor.RED));
                        return true;
                    }
                    if (args.length < 1) {
                        p.sendMessage("Использование: /legendary <id|fix>");
                        return true;
                    }

                    if (args[0].equalsIgnoreCase("fix")) {
                        UUID id = p.getUniqueId();
                        TimeSession so = timeSessions.get(id);
                        if (so != null) endTimeSession(so, null);
                        TimeSession st = timeByTarget.get(id);
                        if (st != null) endTimeSession(st, null);
                        unlockEntity(id);
                        releaseHover(p);
                        p.setGravity(true);
                        fallingFree.remove(id);
                        setRunBoost(p, false);
                        p.setInvulnerable(false);
                        p.setNoDamageTicks(0);
                        p.setMaximumNoDamageTicks(20);
                        p.removePotionEffect(PotionEffectType.SLOWNESS);
                        p.removePotionEffect(PotionEffectType.JUMP_BOOST);
                        p.sendMessage(Component.text("Состояния (заморозка, парение, бессмертие, ускорение) сброшены.", NamedTextColor.GREEN));
                        return true;
                    }

                    ItemStack item = switch (args[0].toLowerCase(Locale.ROOT)) {
                        case "cb_orange" -> cbOrange();
                        case "cb_purple" -> cbPurple();
                        case "cb_green" -> cbGreen();
                        case "ra_blade" -> raBlade();
                        case "chaos_boomerang" -> chaosBoomerang();
                        case "warden_helmet" -> wardenHelmet();
                        case "twins_sword" -> twinsSword();
                        case "magnet" -> magnet();
                        case "time_clock" -> timeClock();
                        case "ice_spear" -> iceSpear();
                        case "ice_spear_ice" -> iceSpearIce();
                        case "ice_spear_spike" -> iceSpearSpike();
                        case "default_mask" -> defaultMask();
                        case "happy_mask" -> happyMask();
                        case "mask_block" -> maskBlock();
                        case "mask_mace" -> maskMace();
                        case "mask_tompson" -> maskThompson();
                        case "soul_bow" -> soulBow();
                        default -> null;
                    };
                    if (item == null) {
                        p.sendMessage("Неизвестный ID. Доступно: " + String.join(", ", IDS));
                        return true;
                    }
                    giveOrDrop(p, item);
                    return true;
                }

                @Override
                public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String alias, String[] args) {
                    List<String> completions = new ArrayList<>();
                    if (args.length == 1 && sender.hasPermission("legendary.admin")) {
                        for (String s : IDS) {
                            if (s.toLowerCase(Locale.ROOT).startsWith(args[0].toLowerCase(Locale.ROOT))) {
                                completions.add(s);
                            }
                        }
                    }
                    return completions;
                }
            }

            private class ReviveCommand implements CommandExecutor, TabCompleter {
                @Override
                public boolean onCommand(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String label, String[] args) {
                    if (!(sender instanceof Player p)) {
                        sender.sendMessage("Только для игроков.");
                        return true;
                    }
                    if (args.length != 1) {
                        p.sendMessage("Использование: /revive <ник>");
                        return true;
                    }
                    if (isSoulless(p.getUniqueId())) {
                        p.sendMessage(Component.text("Бездушный не может никого возродить.", NamedTextColor.RED));
                        return true;
                    }
                    Player target = getServer().getPlayerExact(args[0]);
                    if (target == null) {
                        p.sendMessage(Component.text("Игрок не в сети.", NamedTextColor.RED));
                        return true;
                    }
                    if (target.equals(p)) {
                        p.sendMessage(Component.text("Нельзя возродить себя.", NamedTextColor.RED));
                        return true;
                    }
                    UUID ownerId = soulless.get(target.getUniqueId());
                    if (ownerId == null) {
                        p.sendMessage(Component.text("У этого игрока душа на месте.", NamedTextColor.RED));
                        return true;
                    }
                    AttributeInstance max = p.getAttribute(Attribute.MAX_HEALTH);
                    if (max == null || max.getValue() < 12.0D) {
                        p.sendMessage(Component.text("Недостаточно сердец: нужно отдать 5, и должно остаться хотя бы одно.", NamedTextColor.RED));
                        return true;
                    }

                    setHealthModifier(p, "soul_gift_" + target.getUniqueId() + "_" + System.currentTimeMillis(), -10.0D, true);
                    soulless.remove(target.getUniqueId());
                    revived.put(target.getUniqueId(), ownerId);
                    revivers.put(p.getUniqueId(), ownerId);
                    saveSouls();
                    setHealthModifier(target, "soul_cap", -10.0D, true);
                    target.teleport(p.getLocation());
                    target.setGameMode(GameMode.SURVIVAL);
                    AttributeInstance tmax = target.getAttribute(Attribute.MAX_HEALTH);
                    target.setHealth(Math.min(REVIVE_HP, tmax != null ? tmax.getValue() : REVIVE_HP));
                    target.getWorld().spawnParticle(Particle.SOUL, target.getLocation().add(0.0D, 1.0D, 0.0D), 40, 0.4D, 0.8D, 0.4D, 0.05D);
                    target.getWorld().playSound(target.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 1.0F, 0.8F);
                    p.sendMessage(Component.text("Вы отдали 5 сердец и возродили " + target.getName() + ".", NamedTextColor.GREEN));
                    target.sendMessage(Component.text("Вас возродил " + p.getName() + ". Вы вернулись с 5 сердцами.", NamedTextColor.GREEN));
                    return true;
                }

                @Override
                public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String alias, String[] args) {
                    List<String> res = new ArrayList<>();
                    if (args.length == 1) {
                        for (Player pl : getServer().getOnlinePlayers()) {
                            if (isSoulless(pl.getUniqueId()) && pl.getName().toLowerCase(Locale.ROOT).startsWith(args[0].toLowerCase(Locale.ROOT))) {
                                res.add(pl.getName());
                            }
                        }
                    }
                    return res;
                }
            }
        }
