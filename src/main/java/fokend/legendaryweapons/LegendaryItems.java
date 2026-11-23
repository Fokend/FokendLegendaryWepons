package fokend.legendaryweapons;

import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.attribute.AttributeModifier.Operation;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.components.CustomModelDataComponent;

/** Builds every legendary item: name, lore, model data, id stamp and base damage. */
final class LegendaryItems {
    private final FokendLegendaryWepons plugin;

    LegendaryItems(FokendLegendaryWepons plugin) {
        this.plugin = plugin;
    }

    Attribute attackDamageAttribute() {
        Attribute attr = Registry.ATTRIBUTE.get(NamespacedKey.minecraft("attack_damage"));
        return attr != null ? attr : Registry.ATTRIBUTE.get(NamespacedKey.minecraft("generic.attack_damage"));
    }

    void applyBaseDamage(ItemStack item, double amount, String id) {
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            Attribute attr = this.attackDamageAttribute();
            if (attr != null) {
                AttributeModifier mod = new AttributeModifier(
                        new NamespacedKey(this.plugin, "legendary_dmg_" + id),
                        amount,
                        Operation.ADD_NUMBER,
                        EquipmentSlotGroup.MAINHAND);
                meta.addAttributeModifier(attr, mod);
                item.setItemMeta(meta);
            }
        }
    }

    void setLore(ItemMeta meta, String... lines) {
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("✦ ЛЕГЕНДАРНЫЙ АРТЕФАКТ ✦", NamedTextColor.LIGHT_PURPLE)
                .decorate(TextDecoration.BOLD)
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.empty());

        for (String line : lines) {
            if (line.isEmpty()) {
                lore.add(Component.empty());
            } else if (line.startsWith("~")) {
                lore.add(Component.text(line.substring(1))
                        .color(NamedTextColor.DARK_PURPLE)
                        .decoration(TextDecoration.ITALIC, true));
            } else if (line.startsWith("#")) {
                lore.add(Component.text(line.substring(1))
                        .color(NamedTextColor.LIGHT_PURPLE)
                        .decorate(TextDecoration.BOLD)
                        .decoration(TextDecoration.ITALIC, false));
            } else {
                lore.add(Component.text(line)
                        .color(NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false));
            }
        }
        meta.lore(lore);
    }

    @SuppressWarnings("deprecation")
    void applyCustomModelData(ItemMeta meta, int cmd) {
        // Both the legacy integer and the 1.21.4+ component, so old and new resource packs match.
        meta.setCustomModelData(cmd);
        CustomModelDataComponent comp = meta.getCustomModelDataComponent();
        comp.setFloats(List.of((float) cmd));
        meta.setCustomModelDataComponent(comp);
    }

    ItemStack base(Material mat, int cmd, String id, String title, boolean applySwordDamage) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }
        this.applyCustomModelData(meta, cmd);
        if (title.contains("<")) {
            meta.displayName(this.plugin.mm.deserialize(title).decoration(TextDecoration.ITALIC, false));
        } else {
            String[] style = switch (id) {
                case "ra_god_blade" -> new String[]{"#fff3a3", "#ff8c00", "☀"};
                case "chaos_boomerang" -> new String[]{"#ff4d9d", "#8a2be2", "⟲"};
                case "warden_helmet" -> new String[]{"#83a4d4", "#b6fbff", "◉"};
                case "twins_sword" -> new String[]{"#ff758c", "#7b2cbf", "⚔"};
                case "magnet" -> new String[]{"#00d2ff", "#3a7bd5", "✧"};
                case "time_clock" -> new String[]{"#a18cd1", "#fbc2eb", "⌛"};
                case "ice_spear", "ice_spear_ice", "ice_spear_spike" -> new String[]{"#83a4d4", "#b6fbff", "❄"};
                case "default_mask", "mask_block" -> new String[]{"#434343", "#d7d2cc", "☠"};
                case "happy_mask" -> new String[]{"#fddb92", "#d1fdff", "☻"};
                case "mask_mace" -> new String[]{"#f7971e", "#ffd200", "✹"};
                case "mask_tompson" -> new String[]{"#f953c6", "#b91d73", "✦"};
                case "soul_bow" -> new String[]{"#8e2de2", "#4a00e0", "♜"};
                default -> new String[]{"#f6d365", "#fda085", "✦"};
            };
            meta.displayName(this.plugin.mm.deserialize(
                            "<gradient:" + style[0] + ":" + style[1] + "><bold>" + style[2] + " " + title + "</bold></gradient>")
                    .decoration(TextDecoration.ITALIC, false));
        }

        meta.setEnchantmentGlintOverride(Boolean.TRUE);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        item.setItemMeta(meta);
        this.plugin.stampId(item, id);
        if (applySwordDamage) {
            this.applyBaseDamage(item, 4.0D, id);
        }
        return item;
    }

    Material getSpearMaterial() {
        for (String name : List.of("NETHERITE_SPEAR", "TRIDENT", "NETHERITE_SWORD")) {
            try {
                return Material.valueOf(name);
            } catch (IllegalArgumentException ignored) {
            }
        }
        return Material.NETHERITE_SWORD;
    }

    ItemStack cbOrange() {
        ItemStack item = this.base(
                Material.COMMAND_BLOCK,
                FokendLegendaryWepons.CMD_CB_ORANGE,
                FokendLegendaryWepons.ID_CB_ORANGE,
                "<gradient:#FF8008:#FFC837><bold>✴ Импульсное Ядро Древних</bold></gradient>",
                false);
        ItemMeta meta = item.getItemMeta();
        this.setLore(
                meta,
                "~Сгусток первозданной энергии, пульсирующий в такт сердцу мира.",
                "",
                "#ПРИМЕНЕНИЕ",
                "Рецепты легендарных артефактов; наковальня:",
                "10% шанс повысить случайное зачарование на уровень.",
                "При неудаче теряется 80% оставшейся прочности.");
        item.setItemMeta(meta);
        return item;
    }

    ItemStack cbPurple() {
        ItemStack item = this.base(
                Material.REPEATING_COMMAND_BLOCK,
                FokendLegendaryWepons.CMD_CB_PURPLE,
                FokendLegendaryWepons.ID_CB_PURPLE,
                "<gradient:#8E2DE2:#4A00E0><bold>۞ Ядро Вечного Цикла</bold></gradient>",
                false);
        ItemMeta meta = item.getItemMeta();
        this.setLore(
                meta,
                "~Бесконечный вихрь хроно-материи, способный искажать реальность.",
                "",
                "#ПРИМЕНЕНИЕ",
                "Рецепты легендарных артефактов; наковальня:",
                "15% шанс повысить случайное зачарование на уровень.",
                "При неудаче теряется 80% оставшейся прочности.");
        item.setItemMeta(meta);
        return item;
    }

    ItemStack cbGreen() {
        ItemStack item = this.base(
                Material.CHAIN_COMMAND_BLOCK,
                FokendLegendaryWepons.CMD_CB_GREEN,
                FokendLegendaryWepons.ID_CB_GREEN,
                "<gradient:#11998e:#38ef7d><bold>❖ Цепное Ядро Сингулярности</bold></gradient>",
                false);
        ItemMeta meta = item.getItemMeta();
        this.setLore(
                meta,
                "~Холодный кристалл абсолютного порядка, связывающий стихии воедино.",
                "",
                "#ПРИМЕНЕНИЕ",
                "Рецепты легендарных артефактов; наковальня:",
                "17% шанс повысить случайное зачарование на уровень.",
                "При неудаче теряется 80% оставшейся прочности.");
        item.setItemMeta(meta);
        return item;
    }

    ItemStack raBlade() {
        ItemStack item = this.base(
                Material.NETHERITE_SWORD,
                FokendLegendaryWepons.CMD_RA_BLADE,
                FokendLegendaryWepons.ID_RA_BLADE,
                "<gradient:#FFD700:#FF4500><bold>☀ Гелиос, Клинок Бога Ра</bold></gradient>",
                true);
        ItemMeta meta = item.getItemMeta();
        this.setLore(
                meta,
                "~Ослепительный свет древнего божества заточён в этом лезвии.",
                "",
                "#АКТИВАЦИЯ",
                "ПКМ: призвать или отозвать манекен Ра (кд 60с).",
                "Манекен стоит 45 сек. Удар по нему отражает",
                "весь урон на всех врагов вокруг в 10 блоках.");
        item.setItemMeta(meta);
        return item;
    }

    ItemStack chaosBoomerang() {
        ItemStack item = this.base(
                Material.NETHERITE_SWORD,
                FokendLegendaryWepons.CMD_CHAOS_BOOMERANG,
                FokendLegendaryWepons.ID_CHAOS_BOOMERANG,
                "<gradient:#FF0099:#79095D><bold>🌀 Потрошитель Бездны • Бумеранг Хаоса</bold></gradient>",
                true);
        ItemMeta meta = item.getItemMeta();
        this.setLore(
                meta,
                "~Оружие, не знающее покоя. Несёт в себе частицу чистого безумия.",
                "",
                "#ЗАПУСК",
                "ПКМ: отправить бумеранг в полёт (кд 8с).",
                "#РИКОШЕТЫ",
                "Отскакивает от блоков и врагов (макс. 10 раз),",
                "совершает минимум 3 и максимум 10 отскоков.",
                "При отскоках сила удара растёт.");
        item.setItemMeta(meta);
        return item;
    }

    ItemStack wardenHelmet() {
        ItemStack item = this.base(
                Material.CARVED_PUMPKIN,
                FokendLegendaryWepons.CMD_WARDEN_HELMET,
                FokendLegendaryWepons.ID_WARDEN_HELMET,
                "<gradient:#00C9FF:#003366><bold>👁 Корона Глубин • Лик Хранителя</bold></gradient>",
                false);
        ItemMeta meta = item.getItemMeta();
        this.setLore(
                meta,
                "~Эхо глубин лишает владельца зрения, но отмечает окружение светом.",
                "",
                "#ПОКА НАДЕТ",
                "Слепота владельцу; свечение существ",
                "рядом (до 15 блоков). Вардены не выбирают владельца целью.",
                "#АКТИВАЦИЯ",
                "Пустой рукой ПКМ / SHIFT+F: Sonic Boom",
                "Выстрел летит по направлению взгляда",
                "до 32 блоков (урон 10, отбрасывание; кд 10с).",
                "Луч останавливается о стены; наводиться на моба не нужно.");
        item.setItemMeta(meta);
        return item;
    }

    ItemStack twinsSword() {
        ItemStack item = this.base(
                Material.NETHERITE_SWORD,
                FokendLegendaryWepons.CMD_TWINS_SWORD,
                FokendLegendaryWepons.ID_TWINS_SWORD,
                "<gradient:#B24592:#F15F79><bold>⚔ Эхо Дуальности • Меч Близнецов</bold></gradient>",
                true);
        ItemMeta meta = item.getItemMeta();
        this.setLore(
                meta,
                "~Клинок, отзывающийся эхом в одном верном союзнике.",
                "",
                "#АКТИВАЦИЯ",
                "ПКМ: призвать близнеца на 30с (кд 30с).",
                "Близнец следует за вами и повторяет ваши удары;",
                "активный близнец добавляет 4 урона.",
                "#ПАССИВНО",
                "25% шанс нанести дополнительный удар.");
        item.setItemMeta(meta);
        return item;
    }

    ItemStack magnet() {
        ItemStack item = this.base(
                Material.NETHERITE_SWORD,
                FokendLegendaryWepons.CMD_MAGNET,
                FokendLegendaryWepons.ID_MAGNET,
                "<gradient:#FF416C:#3A7BD5><bold>🧲 Полюс Гравитации • Магнитник</bold></gradient>",
                true);
        ItemMeta meta = item.getItemMeta();
        this.setLore(
                meta,
                "~Соткан из гравитационных аномалий. Никто не избежит его притяжения.",
                "",
                "#АКТИВАЦИЯ",
                "ПКМ: притянуть врагов к точке взгляда перед вами.",
                "SHIFT+ПКМ: оттолкнуть врагов и отразить снаряды.",
                "Перезарядка: 5с.");
        item.setItemMeta(meta);
        return item;
    }

    ItemStack timeClock() {
        ItemStack item = this.base(
                Material.NETHERITE_SWORD,
                FokendLegendaryWepons.CMD_TIME_CLOCK,
                FokendLegendaryWepons.ID_TIME_CLOCK,
                "<gradient:#00F2FE:#4FACFE><bold>⏳ Хроносфера • Часы Вечности</bold></gradient>",
                true);
        ItemMeta meta = item.getItemMeta();
        this.setLore(
                meta,
                "~Шестерёнки судьбы тикают внутри, позволяя повелевать самим временем.",
                "",
                "#ЗАПИСЬ",
                "ЛКМ по сущности: записать 10 секунд её движений,",
                "затем остановка до 3 минут. Цель неуязвима.",
                "#УПРАВЛЕНИЕ",
                "Колёсико мыши: листать записанные кадры",
                "(SHIFT: сразу по 10 кадров).",
                "ПКМ: вернуть время с выбранного кадра (кд 3 мин).");
        item.setItemMeta(meta);
        return item;
    }

    ItemStack iceSpear() {
        ItemStack item = this.base(
                this.getSpearMaterial(),
                FokendLegendaryWepons.CMD_ICE_SPEAR,
                FokendLegendaryWepons.ID_ICE_SPEAR,
                "<gradient:#83a4d4:#b6fbff><bold>❄ Гласиус • Копьё Вечной Мерзлоты</bold></gradient>",
                true);
        ItemMeta meta = item.getItemMeta();
        this.setLore(
                meta,
                "~Сковано в сердце вечной мерзлоты. Его прикосновение останавливает жизнь.",
                "",
                "#АКТИВАЦИЯ",
                "1-й слот + SHIFT+ПКМ: вызвать морозные пики (кд 75с).",
                "#ПАССИВНО",
                "2-й слот: удар замораживает цель на 3с",
                "(перезарядка эффекта 6с).");
        if (meta != null) {
            Enchantment lunge = Registry.ENCHANTMENT.get(NamespacedKey.minecraft("lunge"));
            if (lunge != null) {
                meta.addEnchant(lunge, 4, true);
            } else {
                meta.addEnchant(Enchantment.RIPTIDE, 4, true);
            }
        }
        item.setItemMeta(meta);
        return item;
    }

    ItemStack iceSpearIce() {
        ItemStack item = this.base(Material.NETHERITE_SWORD, FokendLegendaryWepons.CMD_ICE_SPEAR_ICE, FokendLegendaryWepons.ID_ICE_SPEAR_ICE, "<aqua>Лёд копья</aqua>", true);
        ItemMeta meta = item.getItemMeta();
        this.setLore(meta, "~След вечной мерзлоты, оставленный Морозным копьём.", "", "#АРТЕФАКТ", "Ледяной образ из набора Морозного копья.");
        item.setItemMeta(meta);
        return item;
    }

    ItemStack iceSpearSpike() {
        ItemStack item = this.base(Material.NETHERITE_SWORD, FokendLegendaryWepons.CMD_ICE_SPEAR_SPIKE, FokendLegendaryWepons.ID_ICE_SPEAR_SPIKE, "<aqua>Морозные пики</aqua>", true);
        ItemMeta meta = item.getItemMeta();
        this.setLore(meta, "~Осколки льда, пробуждённые силой Морозного копья.", "", "#АРТЕФАКТ", "Ледяной образ из набора Морозного копья.");
        item.setItemMeta(meta);
        return item;
    }

    ItemStack defaultMask() {
        ItemStack item = this.base(
                Material.CARVED_PUMPKIN,
                FokendLegendaryWepons.CMD_DEFAULT_MASK,
                FokendLegendaryWepons.ID_DEFAULT_MASK,
                "<gradient:#56ab2f:#a8e063><bold>🎭 Лик Безумия • Маска Хаоса</bold></gradient>",
                false);
        ItemMeta meta = item.getItemMeta();
        this.setLore(
                meta,
                "~Безликая маска, скрывающая истинные намерения и дарующая безумную силу.",
                "",
                "#КОГДА НАДЕТА",
                "Бег и парение при падении.",
                "В приседе + F или SHIFT+ПКМ: Молот — сокрушающий",
                "удар радиусом 5 блоков, 10 сердец игрокам (кд 5 мин).",
                "Бег: 5 сек в приседе дают ускорение +20% на 10 сек.",
                "Падение: на высоте более 20 блоков паришь",
                "в воздухе, пока не посмотришь вниз.",
                "",
                "#В РУКЕ",
                "ПКМ: Томпсоны — парный взрывной выстрел");
        item.setItemMeta(meta);
        return item;
    }

    ItemStack happyMask() {
        ItemStack item = this.base(
                Material.CARVED_PUMPKIN,
                FokendLegendaryWepons.CMD_HAPPY_MASK,
                FokendLegendaryWepons.ID_HAPPY_MASK,
                "<gradient:#FFE000:#799F0C><bold>☺ Эйфория • Счастливая Маска</bold></gradient>",
                false);
        ItemMeta meta = item.getItemMeta();
        this.setLore(
                meta,
                "~Её улыбка неестественна, но она дарует невиданную жизненную силу.",
                "",
                "#ПОКА НАДЕТА",
                "Регенерация и ночное зрение.");
        item.setItemMeta(meta);
        return item;
    }

    ItemStack maskBlock() {
        ItemStack item = this.base(Material.CARVED_PUMPKIN, FokendLegendaryWepons.CMD_MASK_BLOCK, FokendLegendaryWepons.ID_MASK_BLOCK, "<gold>Блок маски</gold>", false);
        ItemMeta meta = item.getItemMeta();
        this.setLore(meta, "~Фрагмент легенды о Маске.", "", "#АРТЕФАКТ", "Самостоятельной способности нет.");
        item.setItemMeta(meta);
        return item;
    }

    ItemStack maskMace() {
        ItemStack item = this.base(
                Material.MACE,
                FokendLegendaryWepons.CMD_MASK_MACE,
                FokendLegendaryWepons.ID_MASK_MACE,
                "<gradient:#FF512F:#DD2476><bold>🔨 Крушитель Черепов • Молот Маски</bold></gradient>",
                true);
        ItemMeta meta = item.getItemMeta();
        this.setLore(
                meta,
                "~Игрушечный на вид, но сокрушительно смертоносный на деле.",
                "",
                "#АРТЕФАКТ",
                "Булава, используемая как образ молота",
                "во время способности Маски. Отдельной способности нет.");
        item.setItemMeta(meta);
        return item;
    }

    ItemStack maskThompson() {
        ItemStack item = this.base(
                Material.NETHERITE_SWORD,
                FokendLegendaryWepons.CMD_MASK_TOMPSON,
                FokendLegendaryWepons.ID_MASK_TOMPSON,
                "<gradient:#D31027:#EA384D><bold>🔫 Гнев Чикаго • Парные Томпсоны</bold></gradient>",
                true);
        ItemMeta meta = item.getItemMeta();
        this.setLore(
                meta,
                "~Свинцовый дождь с привкусом пороха и хаоса.",
                "",
                "#АКТИВАЦИЯ",
                "ПКМ: выстрелить взрывной пулей.",
                "Урон взрыва: 5 в радиусе 2 блоков.",
                "#ИЗНОС",
                "Каждый выстрел расходует 10 прочности.");
        item.setItemMeta(meta);
        return item;
    }

    ItemStack soulBow() {
        ItemStack item = this.base(
                Material.BOW,
                FokendLegendaryWepons.CMD_SOUL_BOW,
                FokendLegendaryWepons.ID_SOUL_BOW,
                "<gradient:#43cea2:#185a9d><bold>👻 Шёпот Павших • Лук Душ</bold></gradient>",
                false);
        ItemMeta meta = item.getItemMeta();
        this.setLore(
                meta,
                "~Тетива сплетена из стонов павших.",
                "",
                "#ПОХИЩЕНИЕ ДУШ",
                "Убийство игрока луком забирает его душу:",
                "жертва теряет тело и ничего не может делать.",
                "#СИЛА ВЫСТРЕЛА",
                "Урон = 5 + число душ (максимум 5 душ).",
                "Выстрел на 5-й душе тратит 1 душу.",
                "#ДУШИ",
                "SHIFT+ЛКМ: поглотить душу (+4 сердца).",
                "/revive НИК: возродить бездушного за 5 сердец.",
                "Смерть владельца возвращает всех в норму.");
        item.setItemMeta(meta);
        return item;
    }
}
