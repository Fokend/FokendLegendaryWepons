package FuckFokend.fokendLegendaryWepons;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffectType;

/** /legendary: admin give, state reset, soul and craft resets, ability kill switch, config reload. */
final class LegendaryCommand implements CommandExecutor, TabCompleter {
    private final FokendLegendaryWepons plugin;

    private final List<String> IDS = List.of(
            "cb_orange", "cb_purple", "cb_green", "ra_blade", "chaos_boomerang",
            "warden_helmet", "twins_sword", "magnet", "time_clock", "ice_spear",
            "ice_spear_ice", "ice_spear_spike", "default_mask", "happy_mask",
            "mask_block", "mask_mace", "mask_tompson", "soul_bow",
            "death_note", "fate_scales", "fate_lightning",
            "fix", "clearsouls", "resetcrafts", "disable", "enable", "disabled", "reload"
    );

    LegendaryCommand(FokendLegendaryWepons plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length == 1 && args[0].equalsIgnoreCase("reload") && sender.hasPermission("legendary.admin")) {
            this.plugin.balance.reload();
            sender.sendMessage(Component.text("config.yml перезагружен.", NamedTextColor.GREEN));
            return true;
        }
        if (!(sender instanceof Player p)) {
            sender.sendMessage("Только для игроков.");
            return true;
        }
        if (!p.hasPermission("legendary.admin")) {
            p.sendMessage(Component.text("Нет прав.", NamedTextColor.RED));
            return true;
        }
        if (args.length < 1) {
            p.sendMessage("Использование: /legendary <id|give|fix|clearsouls|resetcrafts|disable|enable|disabled|reload>");
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("fix")) {
            UUID id = p.getUniqueId();
            FokendLegendaryWepons.TimeSession sOwner = this.plugin.timeSessions.get(id);
            if (sOwner != null) {
                this.plugin.endTimeSession(sOwner, null);
            }
            FokendLegendaryWepons.TimeSession sTarget = this.plugin.timeByTarget.get(id);
            if (sTarget != null) {
                this.plugin.endTimeSession(sTarget, null);
            }
            this.plugin.unlockEntity(id);
            this.plugin.releaseHover(p);
            p.setGravity(true);
            this.plugin.fallingFree.remove(id);
            this.plugin.setRunBoost(p, false);
            p.setInvulnerable(false);
            p.setNoDamageTicks(0);
            p.setMaximumNoDamageTicks(20);
            p.removePotionEffect(PotionEffectType.SLOWNESS);
            p.removePotionEffect(PotionEffectType.JUMP_BOOST);
            p.sendMessage(Component.text("Состояния (заморозка, парение, бессмертие, ускорение) сброшены.", NamedTextColor.GREEN));
            return true;
        }
        if (sub.equals("clearsouls")) {
            Set<UUID> allAffected = new HashSet<>();
            allAffected.addAll(this.plugin.souls.keySet());
            allAffected.addAll(this.plugin.soulless.keySet());
            allAffected.addAll(this.plugin.revived.keySet());
            allAffected.addAll(this.plugin.revivers.keySet());
            this.plugin.souls.clear();
            for (UUID affected : allAffected) {
                Player online = this.plugin.getServer().getPlayer(affected);
                if (online != null) {
                    this.plugin.setHealthModifier(online, "soul_consume", 0.0D, false);
                }
                this.plugin.normalize(affected);
            }
            this.plugin.saveSouls();
            p.sendMessage(Component.text("Все состояния Лука Душ сброшены.", NamedTextColor.GREEN));
            return true;
        }
        if (sub.equals("resetcrafts")) {
            this.plugin.craftedUniques.clear();
            this.plugin.saveSouls();
            for (Player online : this.plugin.getServer().getOnlinePlayers()) {
                for (String rKey : FokendLegendaryWepons.RECIPE_KEYS) {
                    online.discoverRecipe(new NamespacedKey(this.plugin, rKey));
                }
            }
            p.sendMessage(Component.text("Лимиты крафта легендарных предметов сброшены.", NamedTextColor.GREEN));
            return true;
        }

        if (sub.equals("disable") || sub.equals("enable")) {
            java.util.TreeSet<String> names = new java.util.TreeSet<>(FokendLegendaryWepons.ABILITY_SWITCHES.keySet());
            if (args.length < 2) {
                p.sendMessage("Использование: /legendary " + sub + " <" + String.join("|", names) + ">");
                return true;
            }
            String name = args[1].toLowerCase(Locale.ROOT);
            String abilityId = FokendLegendaryWepons.ABILITY_SWITCHES.get(name);
            if (abilityId == null) {
                p.sendMessage(Component.text("Неизвестная способность: " + name + ". Доступно: " + String.join(", ", names), NamedTextColor.RED));
                return true;
            }
            boolean off = sub.equals("disable");
            boolean changed = off ? this.plugin.disabledAbilities.add(abilityId)
                    : this.plugin.disabledAbilities.remove(abilityId);
            this.plugin.saveDisabledAbilities();
            if (!changed) {
                p.sendMessage(Component.text("Способность " + name + " уже " + (off ? "отключена." : "включена."), NamedTextColor.YELLOW));
                return true;
            }
            this.plugin.getLogger().warning(p.getName() + (off ? " отключил" : " включил") + " способность " + name);
            p.sendMessage(Component.text("Способность " + name + (off ? " отключена. Предметы остаются у игроков, защита работает." : " снова включена."),
                    off ? NamedTextColor.RED : NamedTextColor.GREEN));
            return true;
        }
        if (sub.equals("disabled")) {
            List<String> off = new ArrayList<>();
            for (Map.Entry<String, String> en : FokendLegendaryWepons.ABILITY_SWITCHES.entrySet()) {
                if (this.plugin.disabledAbilities.contains(en.getValue())) {
                    off.add(en.getKey());
                }
            }
            java.util.Collections.sort(off);
            p.sendMessage(off.isEmpty()
                    ? Component.text("Все способности включены.", NamedTextColor.GREEN)
                    : Component.text("Отключены: " + String.join(", ", off), NamedTextColor.RED));
            return true;
        }

        Player targetPlayer = p;
        int amount = 1;
        if (sub.equals("give")) {
            if (args.length == 2) {
                sub = args[1].toLowerCase(Locale.ROOT);
            } else if (args.length >= 3) {
                Player found = this.plugin.getServer().getPlayerExact(args[1]);
                if (found == null) {
                    p.sendMessage(Component.text("Игрок не найден.", NamedTextColor.RED));
                    return true;
                }
                targetPlayer = found;
                sub = args[2].toLowerCase(Locale.ROOT);
                if (args.length >= 4) {
                    try {
                        amount = Math.max(1, Math.min(64, Integer.parseInt(args[3])));
                    } catch (NumberFormatException ignored) {
                    }
                }
            } else {
                p.sendMessage("Использование: /legendary <id|fix|clearsouls|resetcrafts> или /legendary give <ник> <id>");
                return true;
            }
        }

        ItemStack item = switch (sub) {
            case "cb_orange" -> this.plugin.items.cbOrange();
            case "cb_purple" -> this.plugin.items.cbPurple();
            case "cb_green" -> this.plugin.items.cbGreen();
            case "ra_blade", "ra", "ra_god_blade" -> this.plugin.items.raBlade();
            case "chaos_boomerang", "chaos", "boomerang" -> this.plugin.items.chaosBoomerang();
            case "warden_helmet", "warden" -> this.plugin.items.wardenHelmet();
            case "twins_sword", "twins" -> this.plugin.items.twinsSword();
            case "magnet" -> this.plugin.items.magnet();
            case "time_clock", "clock" -> this.plugin.items.timeClock();
            case "ice_spear", "spear" -> this.plugin.items.iceSpear();
            case "ice_spear_ice" -> this.plugin.items.iceSpearIce();
            case "ice_spear_spike" -> this.plugin.items.iceSpearSpike();
            case "default_mask", "mask" -> this.plugin.items.defaultMask();
            case "happy_mask" -> this.plugin.items.happyMask();
            case "mask_block" -> this.plugin.items.maskBlock();
            case "mask_mace" -> this.plugin.items.maskMace();
            case "mask_tompson", "thompson" -> this.plugin.items.maskThompson();
            case "soul_bow", "bow" -> this.plugin.items.soulBow();
            case "death_note", "deathnote" -> DeathNoteListener.create(this.plugin);
            case "fate_scales", "scales_of_fate", "scales" -> ScalesOfFateListener.createScales(this.plugin);
            case "fate_lightning", "lightning" -> ScalesOfFateListener.createLightning(this.plugin);
            default -> null;
        };
        if (item == null) {
            p.sendMessage("Неизвестный ID. Доступно: " + String.join(", ", this.IDS));
            return true;
        }
        for (int i = 0; i < amount; i++) {
            this.plugin.giveOrDrop(targetPlayer, item.clone());
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (!sender.hasPermission("legendary.admin")) {
            return out;
        }
        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            for (String id : this.IDS) {
                if (id.toLowerCase(Locale.ROOT).startsWith(prefix)) {
                    out.add(id);
                }
            }
        } else if (args.length == 2 && (args[0].equalsIgnoreCase("disable") || args[0].equalsIgnoreCase("enable"))) {
            String prefix = args[1].toLowerCase(Locale.ROOT);
            boolean enabling = args[0].equalsIgnoreCase("enable");
            for (Map.Entry<String, String> en : new java.util.TreeMap<>(FokendLegendaryWepons.ABILITY_SWITCHES).entrySet()) {
                boolean off = this.plugin.disabledAbilities.contains(en.getValue());
                if (off == enabling && en.getKey().startsWith(prefix)) {
                    out.add(en.getKey());
                }
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("give")) {
            String prefix = args[1].toLowerCase(Locale.ROOT);
            for (Player online : this.plugin.getServer().getOnlinePlayers()) {
                if (online.getName().toLowerCase(Locale.ROOT).startsWith(prefix)) {
                    out.add(online.getName());
                }
            }
        } else if (args.length == 3 && args[0].equalsIgnoreCase("give")) {
            String prefix = args[2].toLowerCase(Locale.ROOT);
            for (String id : this.IDS) {
                if (id.toLowerCase(Locale.ROOT).startsWith(prefix)) {
                    out.add(id);
                }
            }
        }
        return out;
    }
}
