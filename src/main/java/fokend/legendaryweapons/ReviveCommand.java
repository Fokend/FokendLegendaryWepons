package fokend.legendaryweapons;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.GameMode;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

/** /revive: bring a soulless player back at the cost of the reviver's hearts. */
final class ReviveCommand implements CommandExecutor, TabCompleter {
    private final FokendLegendaryWepons plugin;

    ReviveCommand(FokendLegendaryWepons plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player reviver)) {
            sender.sendMessage("Только для игроков.");
            return true;
        }
        if (args.length != 1) {
            reviver.sendMessage("Использование: /revive <ник>");
            return true;
        }
        if (this.plugin.isSoulless(reviver.getUniqueId())) {
            reviver.sendMessage(Component.text("Бездушный не может никого возродить.", NamedTextColor.RED));
            return true;
        }
        Player target = this.plugin.getServer().getPlayerExact(args[0]);
        if (target == null) {
            reviver.sendMessage(Component.text("Игрок не в сети.", NamedTextColor.RED));
            return true;
        }
        if (target.equals(reviver)) {
            reviver.sendMessage(Component.text("Нельзя возродить себя.", NamedTextColor.RED));
            return true;
        }
        UUID bowOwnerId = this.plugin.soulless.get(target.getUniqueId());
        if (bowOwnerId == null) {
            reviver.sendMessage(Component.text("У этого игрока душа на месте.", NamedTextColor.RED));
            return true;
        }
        AttributeInstance maxHealth = reviver.getAttribute(Attribute.MAX_HEALTH);
        if (maxHealth == null || maxHealth.getValue() < 12.0D) {
            reviver.sendMessage(Component.text(
                    "Недостаточно сердец: нужно отдать 5, и должно остаться хотя бы одно.", NamedTextColor.RED));
            return true;
        }

        this.plugin.setHealthModifier(
                reviver,
                "soul_gift_" + target.getUniqueId() + "_" + System.currentTimeMillis(),
                -10.0D,
                true);
        this.plugin.soulless.remove(target.getUniqueId());
        this.plugin.revived.put(target.getUniqueId(), bowOwnerId);
        this.plugin.revivers.put(reviver.getUniqueId(), bowOwnerId);
        this.plugin.saveSouls();
        this.plugin.setHealthModifier(target, "soul_cap", -10.0D, true);
        target.teleport(reviver.getLocation());
        target.setGameMode(GameMode.SURVIVAL);
        AttributeInstance targetMax = target.getAttribute(Attribute.MAX_HEALTH);
        double reviveHp = this.plugin.balance.getDouble("revive.health");
        target.setHealth(Math.min(reviveHp, targetMax != null ? targetMax.getValue() : reviveHp));
        target.getWorld().spawnParticle(
                Particle.SOUL, target.getLocation().add(0.0D, 1.0D, 0.0D), 40, 0.4D, 0.8D, 0.4D, 0.05D);
        target.getWorld().playSound(target.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 1.0F, 0.8F);
        reviver.sendMessage(Component.text(
                "Вы отдали 5 сердец и возродили " + target.getName() + ".", NamedTextColor.GREEN));
        target.sendMessage(Component.text(
                "Вас возродил " + reviver.getName() + ". Вы вернулись с 5 сердцами.", NamedTextColor.GREEN));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            for (Player online : this.plugin.getServer().getOnlinePlayers()) {
                if (this.plugin.isSoulless(online.getUniqueId())
                        && online.getName().toLowerCase(Locale.ROOT).startsWith(prefix)) {
                    out.add(online.getName());
                }
            }
        }
        return out;
    }
}
