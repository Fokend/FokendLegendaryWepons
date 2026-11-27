package fokend.legendaryweapons;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * /legends — the chronicle GUI for everyone.
 * Admin: review | approve &lt;id&gt; [player] | discard &lt;id&gt; | info &lt;type&gt; | call &lt;type&gt; | retire &lt;type&gt; confirm
 */
final class LegendsCommand implements CommandExecutor, TabCompleter {
    private static final List<String> ADMIN = List.of("review", "approve", "discard", "info", "call", "retire");
    private final Chronicle chronicle;

    LegendsCommand(Chronicle chronicle) {
        this.chronicle = chronicle;
    }

    static String parseType(String raw) {
        String s = raw.toLowerCase(Locale.ROOT);
        return switch (s) {
            case "ra", "ra_blade", "ra_god_blade" -> FokendLegendaryWepons.ID_RA_BLADE;
            case "chaos", "boomerang", "chaos_boomerang" -> FokendLegendaryWepons.ID_CHAOS_BOOMERANG;
            case "twins", "twins_sword" -> FokendLegendaryWepons.ID_TWINS_SWORD;
            case "warden", "warden_helmet" -> FokendLegendaryWepons.ID_WARDEN_HELMET;
            case "magnet" -> FokendLegendaryWepons.ID_MAGNET;
            case "clock", "time_clock" -> FokendLegendaryWepons.ID_TIME_CLOCK;
            case "spear", "ice_spear" -> FokendLegendaryWepons.ID_ICE_SPEAR;
            case "mask", "default_mask" -> FokendLegendaryWepons.ID_DEFAULT_MASK;
            case "bow", "soul_bow" -> FokendLegendaryWepons.ID_SOUL_BOW;
            case "deathnote", "death_note" -> "death_note";
            case "scales", "fate_scales" -> "fate_scales";
            default -> null;
        };
    }

    private static Component red(String s) {
        return Component.text(s, NamedTextColor.RED);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length == 0) {
            if (sender instanceof Player p) {
                LegendsMenu.openMain(this.chronicle, p);
            } else {
                sender.sendMessage("Только для игроков. Админ-команды: /legends " + String.join("|", ADMIN));
            }
            return true;
        }
        if (!sender.hasPermission("legendary.admin")) {
            sender.sendMessage(red("Нет прав. Просто /legends — открыть летопись."));
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "review" -> this.review(sender);
            case "approve" -> this.approve(sender, args);
            case "discard" -> this.discard(sender, args);
            case "info", "call", "retire" -> {
                if (args.length < 2 || parseType(args[1]) == null) {
                    sender.sendMessage(red("Укажи легендарку: ra, chaos, twins, warden, magnet, clock, spear, mask, bow, deathnote, scales"));
                    return true;
                }
                String type = parseType(args[1]);
                switch (sub) {
                    case "info" -> this.info(sender, type);
                    case "call" -> this.forceCall(sender, type);
                    default -> this.retire(sender, type, args.length >= 3 && args[2].equalsIgnoreCase("confirm"));
                }
            }
            default -> sender.sendMessage(red("Неизвестная подкоманда. /legends " + String.join("|", ADMIN)));
        }
        return true;
    }

    private void review(CommandSender sender) {
        DateTimeFormatter f = LegendsMenu.dateFormat(this.chronicle.plugin, "dd.MM HH:mm");
        LegendRegistry reg = this.chronicle.registry;
        this.chronicle.db.openQuarantine().thenAccept(rows -> Bukkit.getScheduler().runTask(this.chronicle.plugin, () -> {
            if (rows.isEmpty()) {
                sender.sendMessage(Component.text("На проверке ничего нет.", NamedTextColor.GREEN));
                return;
            }
            sender.sendMessage(Component.text("На проверке (" + rows.size() + "):", NamedTextColor.GOLD));
            for (ChronicleDatabase.QuarantineRow q : rows) {
                String who = q.foundBy() == null ? "—" : reg.name(UUID.fromString(q.foundBy()));
                sender.sendMessage(Component.text("#" + q.id() + " " + reg.typeTitle(q.type()) + " · "
                                + LegendRegistry.reasonText(q.reason()) + " · " + f.format(Instant.ofEpochMilli(q.foundAt()))
                                + " · " + q.holder() + " " + who + " · " + q.world() + " " + q.x() + " " + q.y() + " " + q.z() + "  ",
                        NamedTextColor.YELLOW)
                        .append(Component.text("[вернуть мне]", NamedTextColor.GREEN)
                                .clickEvent(ClickEvent.suggestCommand("/legends approve " + q.id())))
                        .append(Component.text(" [отклонить]", NamedTextColor.RED)
                                .clickEvent(ClickEvent.suggestCommand("/legends discard " + q.id()))));
            }
            sender.sendMessage(Component.text("approve делает этот предмет единственным живым экземпляром (прежний гаснет). "
                    + "discard убирает запись, предмет остаётся только в базе.", NamedTextColor.GRAY));
        }));
    }

    private long parseId(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(red("Укажи номер из /legends review."));
            return -1;
        }
        try {
            return Long.parseLong(args[1].replace("#", ""));
        } catch (NumberFormatException e) {
            sender.sendMessage(red("Номер должен быть числом."));
            return -1;
        }
    }

    private void approve(CommandSender sender, String[] args) {
        long id = this.parseId(sender, args);
        if (id < 0) {
            return;
        }
        Player target = args.length >= 3 ? Bukkit.getPlayerExact(args[2]) : (sender instanceof Player p ? p : null);
        if (target == null) {
            sender.sendMessage(red("Кому отдать? Игрок должен быть онлайн: /legends approve " + id + " <ник>"));
            return;
        }
        UUID targetId = target.getUniqueId();
        UUID admin = sender instanceof Player p ? p.getUniqueId() : null;
        ChronicleDatabase db = this.chronicle.db;
        // Resolve first: only one admin can act on an entry, even if two click at once.
        db.quarantineEntry(id).thenCompose(q -> {
            if (q == null || q.resolved()) {
                return java.util.concurrent.CompletableFuture.completedFuture((ChronicleDatabase.QuarantineRow) null);
            }
            return db.resolveQuarantine(id).thenApply(ok -> ok ? q : null);
        }).thenAccept(q -> Bukkit.getScheduler().runTask(this.chronicle.plugin, () -> {
            if (q == null) {
                sender.sendMessage(red("Запись #" + id + " не найдена или уже решена."));
                return;
            }
            Player t = Bukkit.getPlayer(targetId);
            if (t == null) {
                db.reopenQuarantine(id);
                sender.sendMessage(red("Игрок вышел — запись #" + id + " осталась на проверке."));
                return;
            }
            ItemStack item = this.chronicle.registry.approve(q, t, admin);
            if (item == null) {
                db.reopenQuarantine(id);
                sender.sendMessage(red("Не удалось восстановить предмет из записи #" + id + "."));
                return;
            }
            this.chronicle.plugin.giveOrDrop(t, item);
            this.chronicle.tracker.markDirty(t);
            this.chronicle.plugin.getLogger().warning("[Chronicle] " + sender.getName() + " approved quarantine #" + id + " to " + t.getName());
            sender.sendMessage(Component.text("#" + id + " подтверждён и выдан " + t.getName() + ". Он теперь единственный живой экземпляр.", NamedTextColor.GREEN));
        }));
    }

    private void discard(CommandSender sender, String[] args) {
        long id = this.parseId(sender, args);
        if (id < 0) {
            return;
        }
        this.chronicle.db.resolveQuarantine(id).thenAccept(ok -> Bukkit.getScheduler().runTask(this.chronicle.plugin, () -> {
            this.chronicle.plugin.getLogger().warning("[Chronicle] " + sender.getName() + " discarded quarantine #" + id);
            sender.sendMessage(ok ? Component.text("#" + id + " отклонён. Байты предмета остаются в базе (таблица quarantine).", NamedTextColor.YELLOW)
                    : red("Запись #" + id + " не найдена или уже решена."));
        }));
    }

    private void info(CommandSender sender, String type) {
        LegendRegistry reg = this.chronicle.registry;
        LegendRecord rec = reg.live(type);
        if (rec == null) {
            sender.sendMessage(Component.text(reg.typeTitle(type) + ": живого экземпляра нет.", NamedTextColor.GRAY));
            return;
        }
        DateTimeFormatter f = LegendsMenu.dateFormat(this.chronicle.plugin, "dd.MM.yyyy HH:mm");
        sender.sendMessage(Component.text(reg.typeTitle(type) + " — uid " + rec.uid + ", gen " + rec.gen + ", " + rec.status, NamedTextColor.GOLD));
        sender.sendMessage(Component.text("Хозяин: " + reg.name(rec.owner) + (rec.owner != null ? " с " + f.format(Instant.ofEpochMilli(rec.ownerSince)) : ""), NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("Где: " + rec.holder + " " + (rec.holderId == null ? "" : reg.name(rec.holderId) + " ")
                + rec.world + " " + rec.x + " " + rec.y + " " + rec.z + ", видели " + f.format(Instant.ofEpochMilli(rec.lastSeen)), NamedTextColor.YELLOW));
        if (rec.hint != null) {
            sender.sendMessage(Component.text("Отметка: " + rec.hint + " от " + reg.name(rec.hintFrom)
                    + (rec.hintActor != null ? ", участник " + reg.name(rec.hintActor) : ""), NamedTextColor.GRAY));
        }
        if (rec.status == LegendRecord.Status.CALLED) {
            int[] p = this.chronicle.call.shownPoint(rec);
            sender.sendMessage(Component.text("Зов: объявлено " + rec.announces + " раз, показана точка " + p[0] + " " + p[1]
                    + (rec.released ? ", выпущена из рук" : ""), NamedTextColor.LIGHT_PURPLE));
        }
    }

    private void forceCall(CommandSender sender, String type) {
        LegendRecord rec = this.chronicle.registry.live(type);
        if (rec == null || rec.status != LegendRecord.Status.ACTIVE) {
            sender.sendMessage(red("Нечего звать: нет живого экземпляра или Зов уже идёт."));
            return;
        }
        if (rec.owner != null && Bukkit.getPlayer(rec.owner) != null) {
            sender.sendMessage(red("Хозяин онлайн — Зов не начинается, пока он в игре."));
            return;
        }
        this.chronicle.plugin.getLogger().warning("[Chronicle] " + sender.getName() + " forced Legend Call for " + type);
        this.chronicle.call.start(rec);
        sender.sendMessage(Component.text("Зов начат.", NamedTextColor.GREEN));
    }

    private void retire(CommandSender sender, String type, boolean confirmed) {
        LegendRegistry reg = this.chronicle.registry;
        LegendRecord rec = reg.live(type);
        if (rec == null) {
            sender.sendMessage(red("У " + reg.typeTitle(type) + " нет живого экземпляра."));
            return;
        }
        if (!confirmed) {
            sender.sendMessage(Component.text("Погасить " + reg.typeTitle(type) + " (хозяин " + reg.name(rec.owner)
                    + ")? Её копии при обнаружении уйдут на проверку, а тип снова можно будет создать. "
                    + "Подтверди: /legends retire " + type + " confirm", NamedTextColor.GOLD));
            return;
        }
        this.chronicle.plugin.getLogger().warning("[Chronicle] " + sender.getName() + " retired " + type + " " + rec.uid);
        reg.retire(rec);
        sender.sendMessage(Component.text(reg.typeTitle(type) + " погашена.", NamedTextColor.YELLOW));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (!sender.hasPermission("legendary.admin")) {
            return out;
        }
        if (args.length == 1) {
            for (String s : ADMIN) {
                if (s.startsWith(args[0].toLowerCase(Locale.ROOT))) {
                    out.add(s);
                }
            }
        } else if (args.length == 2 && List.of("info", "call", "retire").contains(args[0].toLowerCase(Locale.ROOT))) {
            for (String s : List.of("ra", "chaos", "twins", "warden", "magnet", "clock", "spear", "mask", "bow", "deathnote", "scales")) {
                if (s.startsWith(args[1].toLowerCase(Locale.ROOT))) {
                    out.add(s);
                }
            }
        } else if (args.length == 3 && args[0].equalsIgnoreCase("approve")) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                out.add(p.getName());
            }
        }
        return out;
    }
}
