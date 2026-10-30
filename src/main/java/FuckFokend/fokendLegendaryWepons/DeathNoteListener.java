package FuckFokend.fokendLegendaryWepons;

import io.papermc.paper.chat.ChatRenderer;
import io.papermc.paper.event.player.AsyncChatEvent;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.ComponentLike;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.attribute.AttributeModifier.Operation;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerEditBookEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

final class DeathNoteListener implements Listener {
    static final int TOTAL_PAGES = 55;
    static final int RULE_PAGES = 5;
    static final int WRITE_PAGES = 50; // 50 writable pages + 5 rule pages = 55 pages total
    static final int MAX_USES = 50;
    static final double KILL_RADIUS = 30.0D;
    static final String META_DEATH_NOTE_EXECUTING = "death_note_executing";

    private final FokendLegendaryWepons plugin;
    private final NamespacedKey ownerKey;
    private final NamespacedKey usesKey;
    private final NamespacedKey doneKey;
    private final NamespacedKey godEyeKey;
    private final File dataFile;

    private final Set<UUID> owners = ConcurrentHashMap.newKeySet();
    private final Set<UUID> adventureLocked = ConcurrentHashMap.newKeySet();
    private final Set<UUID> scrambled = ConcurrentHashMap.newKeySet();
    private final Map<UUID, String> deathNoteKills = new ConcurrentHashMap<>();

    DeathNoteListener(FokendLegendaryWepons plugin) {
        this.plugin = plugin;
        this.ownerKey = new NamespacedKey(plugin, "dn_owner");
        this.usesKey = new NamespacedKey(plugin, "dn_uses");
        this.doneKey = new NamespacedKey(plugin, "dn_done");
        this.godEyeKey = new NamespacedKey(plugin, "godeye");
        this.dataFile = new File(plugin.getDataFolder(), "deathnote.yml");
        this.load();
        this.registerCommands();
        this.startTasks();
    }

    static ItemStack create(FokendLegendaryWepons plugin) {
        ItemStack item = plugin.base(Material.WRITABLE_BOOK, 20011, "death_note",
                "<gradient:#434343:#c0392b><bold>☠ Тетрадь Смерти</bold></gradient>", false);
        ItemMeta m = item.getItemMeta();
        plugin.setLore(m,
                "~Существо, чьё имя записано в эту тетрадь, умрёт.",
                "",
                "#ЗАПИСЬ",
                "Впиши до 50 записей (1 запись = 1 прочность). Имя/Моб [тики]:",
                "с временем — умрёт в срок, без — мгновенно.",
                "Работает на игроков и мобов (англ. имя или бирка).",
                "Радиус: 30 блоков. Без кулдауна.",
                "#КОМАНДЫ",
                "/godeye, /refusenote",
                "",
                "Осталось записей: 50 / 50");
        BookMeta bm = (BookMeta) m;
        List<String> pages = new ArrayList<>();
        for (int i = 0; i < WRITE_PAGES; ++i) {
            pages.add("");
        }
        pages.addAll(rulePages());
        bm.setPages(pages);
        PersistentDataContainer pdc = bm.getPersistentDataContainer();
        pdc.set(new NamespacedKey(plugin, "dn_uses"), PersistentDataType.INTEGER, 50);
        item.setItemMeta(bm);
        return item;
    }

    static List<String> rulePages() {
        return List.of(
                "§4§lТЕТРАДЬ СМЕРТИ\n§0\nСущество, имя которого записано в эту тетрадь, умрёт.\n\nПравила — на следующих страницах.",
                "§0§lПРАВИЛО 1\n§0Впиши имя на страницах 1-50, по одному на строку.\n• Игрок: точный ник\n• Моб: имя с бирки или название на английском (cow, zombie, skeleton...)\nВремя: «Имя 200» (тики). Без времени — мгновенно.",
                "§0§lПРАВИЛО 2\n§0Всего 50 записей: каждое имя забирает 1 единицу прочности.\nУбить можно только в радиусе 30 блоков от жертвы.\nКулдаун отсутствует.",
                "§0§lПРАВИЛО 3\n§0Ники невидимок шифруются в табе и чате — по ним убить нельзя.\n/godeye — отдать 5 сердец (половину жизни), чтобы видеть невидимых.",
                "§0§lПРАВИЛО 4\n§0/refusenote — отказаться от тетради: здоровье восстановится, тетрадь перейдёт ближайшему игроку.\nЕсли кто-то угадает владельца через /check ник — владелец умрёт и навсегда получит режим приключений. Ошибочный донос карается так же.");
    }

    private void load() {
        if (this.dataFile.exists()) {
            YamlConfiguration y = YamlConfiguration.loadConfiguration(this.dataFile);
            for (String s : y.getStringList("owners")) {
                try {
                    this.owners.add(UUID.fromString(s));
                } catch (IllegalArgumentException ignored) {
                }
            }
            for (String s : y.getStringList("locked")) {
                try {
                    this.adventureLocked.add(UUID.fromString(s));
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
    }

    private synchronized void save() {
        YamlConfiguration y = new YamlConfiguration();
        List<String> o = new ArrayList<>();
        for (UUID u : this.owners) {
            o.add(u.toString());
        }
        List<String> l = new ArrayList<>();
        for (UUID u : this.adventureLocked) {
            l.add(u.toString());
        }
        y.set("owners", o);
        y.set("locked", l);
        try {
            this.plugin.getDataFolder().mkdirs();
            y.save(this.dataFile);
        } catch (IOException ex) {
            this.plugin.getLogger().warning("DeathNote: save failed: " + ex.getMessage());
        }
    }

    private void registerCommands() {
        this.bind("godeye", (s, c, l, a) -> this.godEye(s));
        this.bind("refusenote", (s, c, l, a) -> this.refuse(s));
        this.bind("check", (s, c, l, a) -> this.check(s, a));
        this.bind("dnunlock", (s, c, l, a) -> this.unlock(s, a));
    }

    private void bind(String name, CommandExecutor ex) {
        PluginCommand pc = this.plugin.getCommand(name);
        if (pc == null) {
            this.plugin.getLogger().warning("Command not in plugin.yml: " + name);
        } else {
            pc.setExecutor(ex);
            if (name.equals("check") || name.equals("dnunlock")) {
                TabCompleter tc = (s, c, l, a) -> {
                    List<String> out = new ArrayList<>();
                    if (a.length == 1) {
                        for (Player p : this.plugin.getServer().getOnlinePlayers()) {
                            if (p.getName().toLowerCase(Locale.ROOT).startsWith(a[0].toLowerCase(Locale.ROOT))) {
                                out.add(p.getName());
                            }
                        }
                    }
                    return out;
                };
                pc.setTabCompleter(tc);
            }
        }
    }

    private static Component red(String s) {
        return Component.text(s, NamedTextColor.RED);
    }

    private boolean godEye(CommandSender sender) {
        if (sender instanceof Player p) {
            if (!this.owners.contains(p.getUniqueId())) {
                p.sendMessage(red("Ты не владелец Тетради Смерти."));
                return true;
            }
            AttributeInstance max = p.getAttribute(Attribute.MAX_HEALTH);
            if (max == null) {
                return true;
            }
            if (this.hasGodEye(p)) {
                p.sendMessage(red("Глаза бога уже открыты."));
                return true;
            }
            if (max.getValue() < 12.0D) {
                p.sendMessage(red("Слишком мало здоровья, чтобы отдать 5 сердец."));
                return true;
            }
            max.addModifier(new AttributeModifier(this.godEyeKey, -10.0D, Operation.ADD_NUMBER, EquipmentSlotGroup.ANY));
            if (p.getHealth() > max.getValue()) {
                p.setHealth(max.getValue());
            }
            p.getWorld().playSound(p.getLocation(), Sound.ENTITY_WARDEN_HEARTBEAT, 1.0F, 0.6F);
            p.sendMessage(Component.text("Ты отдал 5 сердец. Теперь ты видишь невидимых (глаза бога снимутся с отказом от тетради).", NamedTextColor.DARK_RED));
            return true;
        } else {
            sender.sendMessage("Только для игроков.");
            return true;
        }
    }

    private boolean hasGodEye(Player p) {
        AttributeInstance max = p.getAttribute(Attribute.MAX_HEALTH);
        if (max == null) return false;
        for (AttributeModifier mod : max.getModifiers()) {
            if (mod.getKey().equals(this.godEyeKey)) {
                return true;
            }
        }
        return false;
    }

    private void removeGodEye(Player p) {
        AttributeInstance max = p.getAttribute(Attribute.MAX_HEALTH);
        if (max != null) {
            for (AttributeModifier mod : new ArrayList<>(max.getModifiers())) {
                if (mod.getKey().equals(this.godEyeKey)) {
                    max.removeModifier(mod);
                }
            }
        }
    }

    private boolean refuse(CommandSender sender) {
        if (sender instanceof Player p) {
            if (!this.owners.contains(p.getUniqueId())) {
                p.sendMessage(red("Ты не владелец Тетради Смерти."));
                return true;
            }
            ItemStack note = null;
            PlayerInventory inv = p.getInventory();
            for (int i = 0; i < inv.getSize(); ++i) {
                ItemStack it = inv.getItem(i);
                if (it != null && this.plugin.isLegendary(it, "death_note")) {
                    if (note == null) {
                        note = it.clone();
                    }
                    inv.setItem(i, null);
                }
            }
            this.releaseOwner(p.getUniqueId());
            p.sendMessage(Component.text("Ты отказался от Тетради Смерти. Здоровье восстановлено.", NamedTextColor.GRAY));
            if (note == null) {
                return true;
            }
            Player nearest = null;
            double best = Double.MAX_VALUE;
            for (Player o : p.getWorld().getPlayers()) {
                if (!o.equals(p) && o.getGameMode() != GameMode.SPECTATOR) {
                    double d = o.getLocation().distanceSquared(p.getLocation());
                    if (d < best) {
                        best = d;
                        nearest = o;
                    }
                }
            }
            if (nearest == null) {
                ItemMeta m = note.getItemMeta();
                m.getPersistentDataContainer().remove(this.ownerKey);
                note.setItemMeta(m);
                this.plugin.protectDroppedItem(p.getWorld().dropItemNaturally(p.getLocation(), note));
            } else {
                ItemMeta m = note.getItemMeta();
                m.getPersistentDataContainer().set(this.ownerKey, PersistentDataType.STRING, nearest.getUniqueId().toString());
                note.setItemMeta(m);
                this.owners.add(nearest.getUniqueId());
                this.save();
                this.plugin.giveOrDrop(nearest, note);
                nearest.sendMessage(Component.text("Тетрадь Смерти перешла к тебе. Прочитай правила на последних страницах.", NamedTextColor.DARK_RED));
            }
            return true;
        } else {
            sender.sendMessage("Только для игроков.");
            return true;
        }
    }

    private void releaseOwner(UUID id) {
        this.owners.remove(id);
        this.save();
        Player p = this.plugin.getServer().getPlayer(id);
        if (p != null) {
            this.removeGodEye(p);
        }
    }

    private boolean check(CommandSender sender, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("Только для игроков.");
            return true;
        }
        if (args.length < 1) {
            p.sendMessage(red("Использование: /check <ник>"));
            return true;
        }
        if (this.owners.contains(p.getUniqueId()) && args[0].equalsIgnoreCase(p.getName())) {
            p.sendMessage(red("Ты не можешь донести на самого себя."));
            return true;
        }
        UUID hit = null;
        for (UUID o : this.owners) {
            OfflinePlayer op = this.plugin.getServer().getOfflinePlayer(o);
            if (op.getName() != null && op.getName().equalsIgnoreCase(args[0])) {
                hit = o;
                break;
            }
        }
        if (hit != null) {
            this.owners.remove(hit);
            this.save();
            p.sendMessage(Component.text("Ты угадал. Владелец Тетради Смерти разоблачён.", NamedTextColor.GREEN));
            this.lockAndKill(hit, "был разоблачён как владелец Тетради Смерти");
        } else {
            p.sendMessage(Component.text("Ошибочный донос!", NamedTextColor.DARK_RED));
            this.lockAndKill(p.getUniqueId(), "понёс наказание за ложный донос");
        }
        return true;
    }

    private void lockAndKill(UUID id, String reason) {
        this.adventureLocked.add(id);
        this.save();
        Player v = this.plugin.getServer().getPlayer(id);
        if (v != null && v.isOnline() && !v.isDead() && v.isValid()) {
            v.setGameMode(GameMode.ADVENTURE);
            this.killPlayer(v, reason);
        }
    }

    private boolean unlock(CommandSender sender, String[] args) {
        if (!sender.hasPermission("legendary.admin")) {
            sender.sendMessage(red("Нет прав."));
            return true;
        }
        if (args.length < 1) {
            sender.sendMessage("/dnunlock <ник>");
            return true;
        }
        OfflinePlayer op = this.plugin.getServer().getOfflinePlayer(args[0]);
        if (this.adventureLocked.remove(op.getUniqueId())) {
            this.save();
            sender.sendMessage("Режим приключений снят с " + args[0]);
        } else {
            sender.sendMessage("У игрока нет блокировки.");
        }
        return true;
    }

    private void killPlayer(Player victim, String reason) {
        if (victim != null && victim.isOnline() && !victim.isDead() && victim.isValid()) {
            this.deathNoteKills.put(victim.getUniqueId(), reason);

            // Bypasses Ash (Пепел) completely:
            victim.setMetadata(META_DEATH_NOTE_EXECUTING, new FixedMetadataValue(this.plugin, true));
            victim.setMetadata(this.plugin.META_DEATH_REASON, new FixedMetadataValue(this.plugin, "умер от сердечного приступа"));

            // Clear any lingering Ash tags from victim
            for (String ns : List.of("fuckenchantsfokend", "fokendenchants")) {
                victim.getPersistentDataContainer().remove(new NamespacedKey(ns, "ash_active"));
                victim.getPersistentDataContainer().remove(new NamespacedKey(ns, "ash_expires"));
                victim.getPersistentDataContainer().remove(new NamespacedKey(ns, "ash_pending"));
            }

            victim.getWorld().spawnParticle(Particle.SOUL, victim.getLocation().add(0.0D, 1.0D, 0.0D), 40, 0.4, 0.8, 0.4, 0.03);
            victim.getWorld().playSound(victim.getLocation(), Sound.ENTITY_WITHER_DEATH, 0.6F, 1.6F);

            try {
                victim.setHealth(0.0D);
            } catch (IllegalStateException | IllegalArgumentException ignored) {
            }
        }
    }

    private void killMob(LivingEntity mob, Player owner, String mobName) {
        if (mob != null && mob.isValid() && !mob.isDead()) {
            mob.getWorld().spawnParticle(Particle.SOUL, mob.getLocation().add(0.0D, 0.8D, 0.0D), 30, 0.3, 0.6, 0.3, 0.03);
            mob.getWorld().playSound(mob.getLocation(), Sound.ENTITY_WITHER_DEATH, 0.5F, 1.7F);
            try {
                mob.setHealth(0.0D);
            } catch (Exception ignored) {
                mob.damage(999999.0D);
            }
            if (owner != null && owner.isOnline()) {
                owner.sendMessage(Component.text("«" + mobName + "» умер от сердечного приступа.", NamedTextColor.DARK_RED));
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDeath(PlayerDeathEvent e) {
        Player p = e.getEntity();
        String reason = this.deathNoteKills.remove(p.getUniqueId());
        if (reason != null) {
            e.deathMessage(Component.text(p.getName() + " " + reason, NamedTextColor.DARK_RED));
        }
        p.removeMetadata(META_DEATH_NOTE_EXECUTING, this.plugin);
    }

    private int getUses(ItemMeta m) {
        Integer v = m.getPersistentDataContainer().get(this.usesKey, PersistentDataType.INTEGER);
        return v == null ? 50 : v;
    }

    private void applyState(ItemMeta m, UUID owner, int uses, List<String> done) {
        PersistentDataContainer pdc = m.getPersistentDataContainer();
        pdc.set(this.ownerKey, PersistentDataType.STRING, owner.toString());
        pdc.set(this.usesKey, PersistentDataType.INTEGER, uses);
        pdc.set(this.doneKey, PersistentDataType.STRING, String.join("\u0001", done));
        List<Component> lore = m.lore();
        if (lore != null && !lore.isEmpty()) {
            lore = new ArrayList<>(lore);
            lore.set(lore.size() - 1, Component.text("Осталось записей: " + uses + " / 50", NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false));
            m.lore(lore);
        }
    }

    private List<String> getDone(ItemMeta m) {
        String s = m.getPersistentDataContainer().get(this.doneKey, PersistentDataType.STRING);
        List<String> out = new ArrayList<>();
        if (s != null && !s.isEmpty()) {
            for (String x : s.split("\u0001")) {
                out.add(x);
            }
        }
        return out;
    }

    // Resolves mob by nametag or English EntityType in radius 30
    private record ResolvedTarget(boolean isPlayer, Player player, LivingEntity mob, String displayName) {}

    private ResolvedTarget resolveTarget(Player owner, String query) {
        // 1. Try Player first
        Player exactPlayer = this.plugin.getServer().getPlayerExact(query);
        if (exactPlayer != null && exactPlayer.isOnline() && exactPlayer.isValid()) {
            return new ResolvedTarget(true, exactPlayer, null, exactPlayer.getName());
        }

        // 2. Try nearby mob with matching nametag (бирка) within 30 blocks
        LivingEntity nearestNamedMob = null;
        double bestDistSq = KILL_RADIUS * KILL_RADIUS;
        Location ownerLoc = owner.getLocation();

        for (Entity ent : owner.getWorld().getNearbyEntities(ownerLoc, KILL_RADIUS, KILL_RADIUS, KILL_RADIUS)) {
            if (!(ent instanceof LivingEntity le) || ent instanceof Player || !le.isValid() || le.isDead()) {
                continue;
            }
            String customName = getEntityCustomName(le);
            if (customName != null && customName.equalsIgnoreCase(query)) {
                double d = ent.getLocation().distanceSquared(ownerLoc);
                if (d < bestDistSq) {
                    bestDistSq = d;
                    nearestNamedMob = le;
                }
            }
        }
        if (nearestNamedMob != null) {
            return new ResolvedTarget(false, null, nearestNamedMob, getEntityCustomName(nearestNamedMob));
        }

        // 3. Try English EntityType within 30 blocks
        EntityType parsedType = parseEntityType(query);
        if (parsedType != null) {
            LivingEntity nearestTypedMob = null;
            bestDistSq = KILL_RADIUS * KILL_RADIUS;
            for (Entity ent : owner.getWorld().getNearbyEntities(ownerLoc, KILL_RADIUS, KILL_RADIUS, KILL_RADIUS)) {
                if (!(ent instanceof LivingEntity le) || ent instanceof Player || !le.isValid() || le.isDead()) {
                    continue;
                }
                if (ent.getType() == parsedType) {
                    double d = ent.getLocation().distanceSquared(ownerLoc);
                    if (d < bestDistSq) {
                        bestDistSq = d;
                        nearestTypedMob = le;
                    }
                }
            }
            if (nearestTypedMob != null) {
                String name = parsedType.getKey().getKey();
                return new ResolvedTarget(false, null, nearestTypedMob, name);
            }
        }

        return null;
    }

    private static String getEntityCustomName(LivingEntity le) {
        if (le.getCustomName() != null && !le.getCustomName().isBlank()) {
            return le.getCustomName();
        }
        Component c = le.customName();
        if (c != null) {
            String plain = PlainTextComponentSerializer.plainText().serialize(c).trim();
            if (!plain.isBlank()) return plain;
        }
        return null;
    }

    private static EntityType parseEntityType(String raw) {
        String clean = raw.trim().toLowerCase(Locale.ROOT).replace(" ", "_").replace("-", "_");
        for (EntityType type : EntityType.values()) {
            if (type.isAlive() && type != EntityType.PLAYER) {
                if (type.name().equalsIgnoreCase(clean) || type.getKey().getKey().equalsIgnoreCase(clean)) {
                    return type;
                }
            }
        }
        return null;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEditBook(PlayerEditBookEvent e) {
        final Player p = e.getPlayer();
        final int slot = e.getSlot();
        ItemStack held = slot == -1 ? p.getInventory().getItemInOffHand() : p.getInventory().getItem(slot);
        if (held == null || !this.plugin.isLegendary(held, "death_note")) {
            return;
        }

        if (e.isSigning()) {
            e.setCancelled(true);
            p.sendMessage(red("Тетрадь Смерти нельзя подписать — только писать в неё."));
            return;
        }

        ItemMeta oldMeta = held.getItemMeta();
        BookMeta newMeta = e.getNewBookMeta();
        List<String> done = this.getDone(oldMeta);
        int uses = this.getUses(oldMeta);
        UUID prevOwner = null;
        String prev = oldMeta.getPersistentDataContainer().get(this.ownerKey, PersistentDataType.STRING);
        if (prev != null) {
            try {
                prevOwner = UUID.fromString(prev);
            } catch (IllegalArgumentException ignored) {
            }
        }

        if (prevOwner != null && !prevOwner.equals(p.getUniqueId())) {
            this.releaseOwner(prevOwner);
            Player po = this.plugin.getServer().getPlayer(prevOwner);
            if (po != null) {
                po.sendMessage(Component.text("Тетрадь Смерти больше не твоя.", NamedTextColor.GRAY));
            }
        }

        if (this.owners.add(p.getUniqueId())) {
            this.save();
        }

        List<String> pages = new ArrayList<>(newMeta.getPages());
        final List<String> fresh = new ArrayList<>(done);
        record PendingDeath(ResolvedTarget target, String rawQuery, long ticks) {}
        List<PendingDeath> toRun = new ArrayList<>();
        int allowed = uses;
        boolean changed = false;
        List<String> usedKeys = new ArrayList<>(done);

        for (int pi = 0; pi < pages.size() && pi < WRITE_PAGES; ++pi) {
            String page = pages.get(pi);
            if (page == null || page.isBlank()) continue;

            String[] lines = page.split("\n", -1);
            List<String> kept = new ArrayList<>();

            for (String raw : lines) {
                String line = raw.replace("§", "").trim();
                if (line.isEmpty()) {
                    kept.add(raw);
                    continue;
                }

                int idx = usedKeys.indexOf(line.toLowerCase(Locale.ROOT));
                if (idx >= 0) {
                    kept.add(raw);
                    continue;
                }

                String reject = null;
                String[] parts = line.split("[\\s:]+");
                String query = parts[0];
                long ticks = 0L;
                if (parts.length > 1) {
                    try {
                        ticks = Math.max(1L, Long.parseLong(parts[parts.length - 1]));
                        // if parts.length > 2, the query might have spaces (e.g. "Iron Golem 100")
                        StringBuilder sb = new StringBuilder();
                        for (int k = 0; k < parts.length - 1; k++) {
                            if (k > 0) sb.append(" ");
                            sb.append(parts[k]);
                        }
                        query = sb.toString();
                    } catch (NumberFormatException ignored) {
                        // All parts are the query
                        query = line;
                    }
                }

                ResolvedTarget resolved = resolveTarget(p, query);
                if (allowed <= 0) {
                    reject = "нет прочности";
                } else if (resolved == null) {
                    reject = "жертва не найдена в радиусе 30 блоков";
                } else if (resolved.isPlayer()) {
                    Player victim = resolved.player();
                    if (victim.hasPotionEffect(PotionEffectType.INVISIBILITY)) {
                        reject = "ник невидимки зашифрован";
                    } else if (ticks == 0L && !this.inRange(p, victim)) {
                        reject = "жертва дальше 30 блоков";
                    }
                }

                if (reject != null) {
                    p.sendMessage(red("«" + query + "»: " + reject + "."));
                    changed = true;
                } else {
                    allowed--;
                    usedKeys.add(line.toLowerCase(Locale.ROOT));
                    fresh.add(line.toLowerCase(Locale.ROOT));
                    toRun.add(new PendingDeath(resolved, query, ticks));
                    kept.add(raw);
                }
            }

            if (changed) {
                pages.set(pi, String.join("\n", kept));
            }
        }

        if (changed) {
            newMeta.setPages(pages);
        }

        this.applyState(newMeta, p.getUniqueId(), allowed, fresh);
        e.setNewBookMeta(newMeta);

        if (!toRun.isEmpty()) {
            final UUID ownerId = p.getUniqueId();
            for (PendingDeath pd : toRun) {
                final ResolvedTarget target = pd.target();
                final String query = pd.rawQuery();
                final long ticks = pd.ticks();

                if (ticks == 0L) {
                    this.execute(ownerId, target, query);
                } else {
                    p.sendMessage(Component.text("«" + target.displayName() + "» умрёт через " + ticks + " тиков.", NamedTextColor.DARK_RED));
                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            DeathNoteListener.this.execute(ownerId, target, query);
                        }
                    }.runTaskLater(this.plugin, ticks);
                }
            }
        }

        final int finalAllowed = allowed;
        new BukkitRunnable() {
            @Override
            public void run() {
                ItemStack cur = slot == -1 ? p.getInventory().getItemInOffHand() : p.getInventory().getItem(slot);
                if (cur != null && DeathNoteListener.this.plugin.isLegendary(cur, "death_note")) {
                    ItemMeta cm = cur.getItemMeta();
                    DeathNoteListener.this.applyState(cm, p.getUniqueId(), finalAllowed, fresh);
                    cur.setItemMeta(cm);
                }
            }
        }.runTask(this.plugin);
    }

    private boolean inRange(Player owner, Entity target) {
        return owner != null && target != null
                && owner.getWorld().equals(target.getWorld())
                && owner.getLocation().distanceSquared(target.getLocation()) <= (KILL_RADIUS * KILL_RADIUS);
    }

    private void execute(UUID ownerId, ResolvedTarget target, String rawQuery) {
        Player owner = this.plugin.getServer().getPlayer(ownerId);

        if (target.isPlayer()) {
            Player victim = target.player();
            if (victim != null && victim.isOnline() && !victim.isDead() && victim.isValid()) {
                if (victim.hasPotionEffect(PotionEffectType.INVISIBILITY)) {
                    if (owner != null) {
                        owner.sendMessage(red("«" + victim.getName() + "» скрыт невидимостью — имя зашифровано."));
                    }
                } else if (owner != null && this.inRange(owner, victim)) {
                    this.killPlayer(victim, "умер от сердечного приступа");
                } else {
                    if (owner != null) {
                        owner.sendMessage(red("«" + victim.getName() + "» вне радиуса 30 блоков — запись не сработала."));
                    }
                }
            }
        } else {
            // Mob target
            LivingEntity mob = target.mob();
            if (mob == null || !mob.isValid() || mob.isDead() || (owner != null && !this.inRange(owner, mob))) {
                // If the specific mob instance died or moved away, re-check nearest matching mob
                if (owner != null) {
                    ResolvedTarget freshTarget = resolveTarget(owner, rawQuery);
                    if (freshTarget != null && !freshTarget.isPlayer() && this.inRange(owner, freshTarget.mob())) {
                        mob = freshTarget.mob();
                    }
                }
            }

            if (mob != null && mob.isValid() && !mob.isDead() && owner != null && this.inRange(owner, mob)) {
                this.killMob(mob, owner, target.displayName());
            } else if (owner != null) {
                owner.sendMessage(red("«" + rawQuery + "» вне радиуса 30 блоков — запись не сработала."));
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onGameMode(PlayerGameModeChangeEvent e) {
        if (this.adventureLocked.contains(e.getPlayer().getUniqueId()) && e.getNewGameMode() != GameMode.ADVENTURE) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        if (this.adventureLocked.contains(p.getUniqueId())) {
            p.setGameMode(GameMode.ADVENTURE);
        }
        if (!this.owners.contains(p.getUniqueId())) {
            this.removeGodEye(p);
        }
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent e) {
        Player p = e.getPlayer();
        if (this.adventureLocked.contains(p.getUniqueId())) {
            this.plugin.getServer().getScheduler().runTask(this.plugin, () -> p.setGameMode(GameMode.ADVENTURE));
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        this.scrambled.remove(e.getPlayer().getUniqueId());
        e.getPlayer().playerListName(null);
    }

    private static Component cipher(String name) {
        return Component.text(name).decorate(TextDecoration.OBFUSCATED);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onChat(AsyncChatEvent e) {
        Player src = e.getPlayer();
        if (src.hasPotionEffect(PotionEffectType.INVISIBILITY)) {
            e.renderer(new ChatRenderer() {
                @Override
                public Component render(Player source, Component sourceDisplayName, Component message, Audience viewer) {
                    Component name = sourceDisplayName;
                    boolean viewerSeesAll = viewer instanceof Player vp && DeathNoteListener.this.hasGodEye(vp);
                    if (!viewerSeesAll) {
                        name = DeathNoteListener.cipher(source.getName());
                    }
                    return Component.translatable("chat.type.text", name, message);
                }
            });
        }
    }

    private void startTasks() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player p : DeathNoteListener.this.plugin.getServer().getOnlinePlayers()) {
                    boolean inv = p.hasPotionEffect(PotionEffectType.INVISIBILITY);
                    boolean has = DeathNoteListener.this.scrambled.contains(p.getUniqueId());
                    if (inv && !has) {
                        p.playerListName(DeathNoteListener.cipher(p.getName()));
                        DeathNoteListener.this.scrambled.add(p.getUniqueId());
                    } else if (!inv && has) {
                        p.playerListName(null);
                        DeathNoteListener.this.scrambled.remove(p.getUniqueId());
                    }
                    if (DeathNoteListener.this.adventureLocked.contains(p.getUniqueId()) && p.getGameMode() != GameMode.ADVENTURE) {
                        p.setGameMode(GameMode.ADVENTURE);
                    }
                }
            }
        }.runTaskTimer(this.plugin, 20L, 10L);

        new BukkitRunnable() {
            int tick = 0;
            @Override
            public void run() {
                ++this.tick;
                for (Player p : DeathNoteListener.this.plugin.getServer().getOnlinePlayers()) {
                    if (DeathNoteListener.this.owners.contains(p.getUniqueId()) && DeathNoteListener.this.hasGodEye(p)) {
                        List<String> names = new ArrayList<>();
                        for (Player o : p.getWorld().getPlayers()) {
                            if (!o.equals(p) && o.hasPotionEffect(PotionEffectType.INVISIBILITY)
                                    && o.getLocation().distanceSquared(p.getLocation()) <= 4096.0D) {
                                names.add(o.getName());
                                Location l = o.getLocation();
                                for (double y = 0.1; y < 1.9; y += 0.45) {
                                    p.spawnParticle(Particle.DUST, l.clone().add(0.0D, y, 0.0D), 3,
                                            0.25D, 0.1D, 0.25D, 0.0D,
                                            new Particle.DustOptions(Color.fromRGB(255, 40, 40), 1.2F));
                                }
                            }
                        }
                        if (this.tick % 20 == 0 && !names.isEmpty()) {
                            p.sendActionBar(Component.text("Глаза бога: " + String.join(", ", names), NamedTextColor.DARK_RED));
                        }
                    }
                }
            }
        }.runTaskTimer(this.plugin, 20L, 5L);
    }
}
