package FuckFokend.fokendLegendaryWepons;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.WeakHashMap;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.inventory.AnvilInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;

final class CoreUpgradeListener implements Listener {
    private static final Random RANDOM = new Random();
    private final FokendLegendaryWepons plugin;
    private final Map<AnvilInventory, Plan> plans = new WeakHashMap<>();

    private record Plan(ItemStack first, ItemStack second, int targetSlot, int coreSlot,
                        double chance, Outcome outcome) {}
    private record Outcome(ItemStack result, boolean success, boolean broken, Enchantment enchantment) {}

    CoreUpgradeListener(FokendLegendaryWepons plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPrepare(PrepareAnvilEvent event) {
        AnvilInventory inventory = event.getInventory();
        ItemStack first = inventory.getFirstItem();
        ItemStack second = inventory.getSecondItem();
        String firstId = plugin.getId(first);
        String secondId = plugin.getId(second);
        ItemStack target;
        String coreId;
        int targetSlot;
        int coreSlot;
        if (plugin.isCraftingCore(secondId)) {
            target = first;
            coreId = secondId;
            targetSlot = 0;
            coreSlot = 1;
        } else if (plugin.isCraftingCore(firstId)) {
            target = second;
            coreId = firstId;
            targetSlot = 1;
            coreSlot = 0;
        } else {
            plans.remove(inventory);
            return;
        }

        if (!isSupportedTarget(target)) {
            plans.remove(inventory);
            event.setResult(null);
            return;
        }

        Plan plan = plans.get(inventory);
        if (plan == null || !sameInputs(plan, first, second)) {
            plan = new Plan(copy(first), copy(second), targetSlot, coreSlot, chanceFor(coreId), null);
            plans.put(inventory, plan);
        }

        // Keep the preview neutral. The random roll happens only when the player takes the result.
        ItemStack preview = target.clone();
        preview.setAmount(1);
        event.setResult(preview);
        event.getView().setRepairCost(1);
        event.getView().setMaximumRepairCost(40);
    }

    @EventHandler(ignoreCancelled = true)
    public void onTakeResult(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player) || event.getRawSlot() != 2
                || !(event.getView().getTopInventory() instanceof AnvilInventory inventory)) return;
        Plan plan = plans.get(inventory);
        if (plan == null || !sameInputs(plan, inventory.getFirstItem(), inventory.getSecondItem())) return;
        event.setCancelled(true);

        int cost = event.getView() instanceof org.bukkit.inventory.view.AnvilView view ? view.getRepairCost() : 1;
        if (player.getGameMode() != org.bukkit.GameMode.CREATIVE && player.getLevel() < cost) {
            player.sendMessage("§cДля обработки ядра нужен как минимум 1 уровень опыта.");
            return;
        }
        // Require output space before rolling, so a full inventory cannot be used to reroll attempts.
        if (!hasOutputSpace(player, event)) {
            player.sendMessage("§cОсвободи место в инвентаре (или на курсоре), чтобы обработать ядро.");
            return;
        }

        Outcome outcome = plan.outcome();
        if (outcome == null) {
            outcome = rollOutcome(plan);
            plan = new Plan(plan.first(), plan.second(), plan.targetSlot(), plan.coreSlot(), plan.chance(), outcome);
            plans.put(inventory, plan);
        }
        if (!outcome.broken() && !storeOutput(player, event, outcome.result().clone())) {
            player.sendMessage("§cОсвободи место в инвентаре, чтобы забрать результат.");
            return;
        }

        consumeOne(inventory, plan.targetSlot());
        consumeOne(inventory, plan.coreSlot());
        inventory.setItem(2, null);
        if (player.getGameMode() != org.bukkit.GameMode.CREATIVE) player.setLevel(player.getLevel() - cost);
        plans.remove(inventory);

        if (outcome.broken()) {
            player.sendMessage("§cЯдро не сработало: предмет разрушен из-за потери прочности.");
        } else if (outcome.success()) {
            player.sendMessage("§aЯдро усилило зачарование " + outcome.enchantment().getKey().getKey() + " на 1 уровень.");
        } else {
            player.sendMessage("§cЯдро не сработало: предмет потерял 80% оставшейся прочности.");
        }
        player.updateInventory();
    }

    private Outcome rollOutcome(Plan plan) {
        ItemStack target = plan.targetSlot() == 0 ? plan.first() : plan.second();
        ItemMeta meta = target.getItemMeta();
        List<Enchantment> enchants = new ArrayList<>();
        for (Map.Entry<Enchantment, Integer> entry : meta.getEnchants().entrySet()) {
            // Deliberately ignore vanilla max levels: each core adds exactly one level.
            if (entry.getValue() < Integer.MAX_VALUE) enchants.add(entry.getKey());
        }
        if (enchants.isEmpty()) return new Outcome(target.clone(), false, false, null);

        if (RANDOM.nextDouble() < plan.chance()) {
            Enchantment selected = enchants.get(RANDOM.nextInt(enchants.size()));
            ItemStack upgraded = target.clone();
            upgraded.setAmount(1);
            ItemMeta upgradedMeta = upgraded.getItemMeta();
            upgradedMeta.addEnchant(selected, meta.getEnchantLevel(selected) + 1, true);
            upgraded.setItemMeta(upgradedMeta);
            return new Outcome(upgraded, true, false, selected);
        }

        int maxDurability = target.getType().getMaxDurability();
        int currentDamage = ((Damageable) meta).getDamage();
        int remaining = maxDurability - currentDamage;
        int damageAfter = currentDamage + (int) Math.ceil(remaining * 0.80D);
        boolean broken = damageAfter >= maxDurability;
        ItemStack damaged = target.clone();
        damaged.setAmount(1);
        Damageable damagedMeta = (Damageable) damaged.getItemMeta();
        damagedMeta.setDamage(Math.min(maxDurability, damageAfter));
        damaged.setItemMeta((ItemMeta) damagedMeta);
        return new Outcome(damaged, false, broken, null);
    }

    private double chanceFor(String coreId) {
        return switch (coreId) {
            case FokendLegendaryWepons.ID_CB_ORANGE -> 0.10D;
            case FokendLegendaryWepons.ID_CB_PURPLE -> 0.15D;
            case FokendLegendaryWepons.ID_CB_GREEN -> 0.17D;
            default -> 0.0D;
        };
    }

    private boolean isSupportedTarget(ItemStack item) {
        if (item == null || item.getType().isAir()) return false;
        if (item.getType().getMaxDurability() <= 0 || !(item.getItemMeta() instanceof Damageable)) return false;
        return !item.getEnchantments().isEmpty();
    }

    private boolean sameInputs(Plan plan, ItemStack first, ItemStack second) {
        return Objects.equals(plan.first(), first) && Objects.equals(plan.second(), second);
    }

    private ItemStack copy(ItemStack item) {
        return item == null ? null : item.clone();
    }

    private boolean hasOutputSpace(Player player, InventoryClickEvent event) {
        if (event.isShiftClick()) return player.getInventory().firstEmpty() != -1;
        ItemStack cursor = event.getCursor();
        return cursor == null || cursor.getType().isAir();
    }

    private boolean storeOutput(Player player, InventoryClickEvent event, ItemStack output) {
        if (event.isShiftClick()) return player.getInventory().addItem(output).isEmpty();
        ItemStack cursor = event.getCursor();
        if (cursor == null || cursor.getType().isAir()) {
            event.getView().setCursor(output);
            return true;
        }
        return false;
    }

    private void consumeOne(AnvilInventory inventory, int slot) {
        ItemStack item = inventory.getItem(slot);
        if (item == null || item.getType().isAir()) return;
        if (item.getAmount() <= 1) inventory.setItem(slot, null);
        else item.setAmount(item.getAmount() - 1);
    }
}
