package fokend.legendaryweapons;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/** The /legends GUI: an overview of every legendary, and a paged history per legendary. Read-only. */
final class LegendsMenu implements InventoryHolder {
    static final int[] MAIN_SLOTS = {10, 11, 12, 13, 14, 15, 16, 20, 21, 23, 24};
    static final int PAGE = 45;
    static final int SLOT_PREV = 45;
    static final int SLOT_BACK = 49;
    static final int SLOT_NEXT = 53;

    enum Kind { MAIN, HISTORY }

    final Kind kind;
    final String type;
    final int page;
    final boolean hasNext;
    private Inventory inventory;

    private LegendsMenu(Kind kind, String type, int page, boolean hasNext) {
        this.kind = kind;
        this.type = type;
        this.page = page;
        this.hasNext = hasNext;
    }

    @Override
    public Inventory getInventory() {
        return this.inventory;
    }

    // ------------------------------------------------------------------ building

    static DateTimeFormatter dateFormat(FokendLegendaryWepons plugin, String pattern) {
        ZoneId zone;
        try {
            zone = ZoneId.of(plugin.getConfig().getString("chronicle.timezone", "Asia/Almaty"));
        } catch (RuntimeException e) {
            zone = ZoneId.systemDefault();
        }
        return DateTimeFormatter.ofPattern(pattern).withZone(zone);
    }

    private static Component line(String text, NamedTextColor color) {
        return Component.text(text, color).decoration(TextDecoration.ITALIC, false);
    }

    private static ItemStack named(Material m, String name, NamedTextColor color, List<Component> lore) {
        ItemStack it = new ItemStack(m);
        ItemMeta meta = it.getItemMeta();
        meta.displayName(line(name, color).decoration(TextDecoration.BOLD, true));
        meta.lore(lore);
        it.setItemMeta(meta);
        return it;
    }

    static ItemStack icon(FokendLegendaryWepons plugin, String type) {
        try {
            ItemStack base = switch (type) {
                case FokendLegendaryWepons.ID_RA_BLADE -> plugin.items.raBlade();
                case FokendLegendaryWepons.ID_CHAOS_BOOMERANG -> plugin.items.chaosBoomerang();
                case FokendLegendaryWepons.ID_TWINS_SWORD -> plugin.items.twinsSword();
                case FokendLegendaryWepons.ID_WARDEN_HELMET -> plugin.items.wardenHelmet();
                case FokendLegendaryWepons.ID_MAGNET -> plugin.items.magnet();
                case FokendLegendaryWepons.ID_TIME_CLOCK -> plugin.items.timeClock();
                case FokendLegendaryWepons.ID_ICE_SPEAR -> plugin.items.iceSpear();
                case FokendLegendaryWepons.ID_DEFAULT_MASK -> plugin.items.defaultMask();
                case FokendLegendaryWepons.ID_SOUL_BOW -> plugin.items.soulBow();
                case "death_note" -> DeathNoteListener.create(plugin);
                case "fate_scales" -> ScalesOfFateListener.createScales(plugin);
                default -> new ItemStack(Material.PAPER);
            };
            // An icon must never count as the legendary itself.
            return plugin.visualCopy(base);
        } catch (RuntimeException e) {
            plugin.warnOnce("chronicle icon " + type, e);
            return new ItemStack(Material.PAPER);
        }
    }

    static String status(LegendRecord rec) {
        if (rec.status == LegendRecord.Status.CALLED) {
            return rec.released ? "ушла на Зов, ищет хозяина" : "зовёт нового хозяина";
        }
        return switch (rec.holder) {
            case PLAYER -> "в руках хозяина";
            case ENDER_CHEST -> "в эндер-сундуке";
            case CONTAINER, VIRTUAL -> "в хранилище";
            case GROUND -> "лежит в мире";
            case GRAVE -> "осталась на месте гибели";
            case NONE -> "неизвестно";
        };
    }

    static void openMain(Chronicle chronicle, Player viewer) {
        LegendRegistry reg = chronicle.registry;
        boolean admin = viewer.hasPermission("legendary.admin");
        DateTimeFormatter day = dateFormat(chronicle.plugin, "dd.MM.yyyy");
        LegendsMenu holder = new LegendsMenu(Kind.MAIN, null, 0, false);
        Inventory inv = Bukkit.createInventory(holder, 36, Component.text("📜 Летопись легенд"));
        holder.inventory = inv;
        for (int i = 0; i < LegendRegistry.ORDER.size() && i < MAIN_SLOTS.length; i++) {
            String type = LegendRegistry.ORDER.get(i);
            LegendRecord rec = reg.live(type);
            List<Component> lore = new ArrayList<>();
            ItemStack it;
            if (rec == null) {
                lore.add(line("Ещё не скована — или о ней пока не знает Летопись.", NamedTextColor.DARK_GRAY));
                lore.add(line("ЛКМ — история", NamedTextColor.GRAY));
                it = named(Material.GRAY_DYE, reg.typeTitle(type), NamedTextColor.GRAY, lore);
            } else {
                it = icon(chronicle.plugin, type);
                ItemMeta meta = it.getItemMeta();
                meta.displayName(line(reg.typeTitle(type), NamedTextColor.GOLD).decoration(TextDecoration.BOLD, true));
                if (rec.owner != null) {
                    lore.add(line("Хозяин: " + reg.publicOwner(type, rec.owner, admin), NamedTextColor.YELLOW));
                    lore.add(line("С " + day.format(Instant.ofEpochMilli(rec.ownerSince)), NamedTextColor.GRAY));
                } else {
                    lore.add(line("Хозяина нет", NamedTextColor.YELLOW));
                }
                lore.add(line("Сейчас: " + status(rec), NamedTextColor.GRAY));
                if (rec.status == LegendRecord.Status.CALLED) {
                    int[] p = chronicle.call.shownPoint(rec);
                    lore.add(line("Зов: около X≈" + p[0] + ", Z≈" + p[1] + " (" + LegendCall.worldTitle(rec.world) + ")", NamedTextColor.LIGHT_PURPLE));
                }
                if (admin) {
                    lore.add(line("[админ] " + rec.holder + " " + rec.world + " " + rec.x + " " + rec.y + " " + rec.z, NamedTextColor.DARK_AQUA));
                    lore.add(line("[админ] uid " + rec.uid.toString().substring(0, 8) + " · gen " + rec.gen, NamedTextColor.DARK_AQUA));
                }
                lore.add(Component.empty());
                lore.add(line("ЛКМ — история", NamedTextColor.GRAY));
                meta.lore(lore);
                it.setItemMeta(meta);
            }
            inv.setItem(MAIN_SLOTS[i], it);
        }
        viewer.openInventory(inv);
    }

    static void openHistory(Chronicle chronicle, Player viewer, String type, int page) {
        UUID viewerId = viewer.getUniqueId();
        ChronicleDatabase db = chronicle.registry.db;
        db.historyCount(type).thenCombine(db.history(type, PAGE, page * PAGE), (count, rows) -> {
            Bukkit.getScheduler().runTask(chronicle.plugin, () -> {
                Player p = Bukkit.getPlayer(viewerId);
                if (p != null) {
                    showHistory(chronicle, p, type, page, rows, count);
                }
            });
            return null;
        });
    }

    private static Material materialFor(TransferReason r) {
        return switch (r) {
            case CRAFT -> Material.ANVIL;
            case KILL -> Material.IRON_SWORD;
            case LOOT -> Material.BONE;
            case GIFT -> Material.EMERALD;
            case CONTAINER -> Material.CHEST;
            case LEGEND_CALL, RELEASED -> Material.BELL;
            case REFUSE_NOTE -> Material.WRITABLE_BOOK;
            case ADMIN_GIVE, ADMIN_APPROVE -> Material.COMMAND_BLOCK;
            case RETIRED -> Material.BARRIER;
            default -> Material.PAPER;
        };
    }

    static void showHistory(Chronicle chronicle, Player viewer, String type, int page,
                            List<ChronicleDatabase.HistoryRow> rows, int total) {
        LegendRegistry reg = chronicle.registry;
        boolean admin = viewer.hasPermission("legendary.admin");
        DateTimeFormatter when = dateFormat(chronicle.plugin, "dd.MM.yyyy HH:mm");
        boolean hasNext = (page + 1) * PAGE < total;
        LegendsMenu holder = new LegendsMenu(Kind.HISTORY, type, page, hasNext);
        Inventory inv = Bukkit.createInventory(holder, 54, Component.text("📜 " + reg.typeTitle(type) + " · стр. " + (page + 1)));
        holder.inventory = inv;
        if (rows.isEmpty()) {
            inv.setItem(22, named(Material.PAPER, "Летопись пуста", NamedTextColor.GRAY,
                    List.of(line("Об этой легенде пока нет записей.", NamedTextColor.DARK_GRAY))));
        }
        for (int i = 0; i < rows.size() && i < PAGE; i++) {
            ChronicleDatabase.HistoryRow h = rows.get(i);
            TransferReason r = TransferReason.parse(h.reason());
            List<Component> lore = new ArrayList<>();
            UUID from = h.from() == null ? null : UUID.fromString(h.from());
            UUID to = h.to() == null ? null : UUID.fromString(h.to());
            if (r == TransferReason.ADMIN_GIVE || r == TransferReason.ADMIN_APPROVE) {
                lore.add(line("Кому: " + reg.publicOwner(type, to, admin), NamedTextColor.YELLOW));
            } else {
                if (from != null) {
                    lore.add(line("От: " + reg.publicOwner(type, from, admin), NamedTextColor.GRAY));
                }
                if (to != null) {
                    lore.add(line("Кому: " + reg.publicOwner(type, to, admin), NamedTextColor.YELLOW));
                }
            }
            if (admin) {
                lore.add(line("[админ] " + h.world() + " " + h.x() + " " + h.y() + " " + h.z(), NamedTextColor.DARK_AQUA));
                if (h.flag() != null) {
                    lore.add(line("[админ] отметка: " + h.flag(), NamedTextColor.RED));
                }
            }
            inv.setItem(i, named(materialFor(r), when.format(Instant.ofEpochMilli(h.at())) + " · " + r.label,
                    NamedTextColor.GOLD, lore));
        }
        if (page > 0) {
            inv.setItem(SLOT_PREV, named(Material.ARROW, "← Новее", NamedTextColor.WHITE, List.of()));
        }
        inv.setItem(SLOT_BACK, named(Material.BOOK, "К списку легенд", NamedTextColor.WHITE, List.of()));
        if (hasNext) {
            inv.setItem(SLOT_NEXT, named(Material.ARROW, "Старше →", NamedTextColor.WHITE, List.of()));
        }
        viewer.openInventory(inv);
    }

    // ------------------------------------------------------------------ clicks

    static final class Clicks implements Listener {
        private final Chronicle chronicle;

        Clicks(Chronicle chronicle) {
            this.chronicle = chronicle;
        }

        @EventHandler(priority = EventPriority.LOWEST)
        public void onClick(InventoryClickEvent e) {
            if (!(ChronicleTracker.holder(e.getView().getTopInventory()) instanceof LegendsMenu menu)) {
                return;
            }
            e.setCancelled(true);
            if (!(e.getWhoClicked() instanceof Player p) || e.getClickedInventory() == null
                    || !e.getClickedInventory().equals(e.getView().getTopInventory())) {
                return;
            }
            int slot = e.getSlot();
            if (menu.kind == Kind.MAIN) {
                for (int i = 0; i < MAIN_SLOTS.length && i < LegendRegistry.ORDER.size(); i++) {
                    if (MAIN_SLOTS[i] == slot) {
                        openHistory(this.chronicle, p, LegendRegistry.ORDER.get(i), 0);
                        return;
                    }
                }
            } else if (slot == SLOT_BACK) {
                openMain(this.chronicle, p);
            } else if (slot == SLOT_PREV && menu.page > 0) {
                openHistory(this.chronicle, p, menu.type, menu.page - 1);
            } else if (slot == SLOT_NEXT && menu.hasNext) {
                openHistory(this.chronicle, p, menu.type, menu.page + 1);
            }
        }

        @EventHandler(priority = EventPriority.LOWEST)
        public void onDrag(InventoryDragEvent e) {
            if (ChronicleTracker.holder(e.getView().getTopInventory()) instanceof LegendsMenu) {
                e.setCancelled(true);
            }
        }
    }
}
