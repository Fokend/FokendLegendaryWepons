package FuckFokend.fokendLegendaryWepons;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.WeakHashMap;
import org.bukkit.Material;
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

    private record Plan(int signature, ItemStack result, boolean success, Enchantment enchantment) {}

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
        if (plugin.isCraftingCore(secondId)) {
            target = first;
            coreId = secondId;
        } else if (plugin.isCraftingCore(firstId)) {
            target = second;
            coreId = firstId;
        } else {
            plans.remove(inventory);
            return;
        }

        if (!isSupportedTarget(target)) {
            plans.remove(inventory);
            event.setResult(null);
            return;
        }

        int signature = 31 * (first == null ? 0 : first.hashCode()) + (second == null ? 0 : second.hashCode());
        Plan plan = plans.get(inventory);
        if (plan == null || plan.signature() != signature) {
            plan = createPlan(signature, target, coreId);
            if (plan == null) {
                plans.remove(inventory);
                event.setResult(null);
                return;
            }
            plans.put(inventory, plan);
        }
        event.setResult(plan.result().clone());
        event.getView().setRepairCost(1);
        event.getView().setMaximumRepairCost(40);
    }

    @EventHandler
    public void onTakeResult(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player) || event.getRawSlot() != 2
                || !(event.getInventory() instanceof AnvilInventory inventory)) return;
        Plan plan = plans.get(inventory);
        if (plan == null) return;
        if (plan.success()) {
            player.sendMessage("§aЯдро усилило зачарование " + plan.enchantment().getKey().getKey() + " на 1 уровень.");
        } else {
            player.sendMessage("§cЯдро не сработало: предмет потерял 80% оставшейся прочности.");
        }
    }

    private Plan createPlan(int signature, ItemStack target, String coreId) {
        ItemMeta meta = target.getItemMeta();
        if (meta == null) return null;
        List<Enchantment> upgradable = new ArrayList<>();
        for (Map.Entry<Enchantment, Integer> entry : meta.getEnchants().entrySet()) {
            if (entry.getValue() < entry.getKey().getMaxLevel()) upgradable.add(entry.getKey());
        }
        if (upgradable.isEmpty()) return null;

        double chance = switch (coreId) {
            case FokendLegendaryWepons.ID_CB_ORANGE -> 0.10D;
            case FokendLegendaryWepons.ID_CB_PURPLE -> 0.15D;
            case FokendLegendaryWepons.ID_CB_GREEN -> 0.17D;
            default -> 0.0D;
        };
        boolean success = RANDOM.nextDouble() < chance;
        ItemStack result = target.clone();
        Enchantment selected = null;
        if (success) {
            selected = upgradable.get(RANDOM.nextInt(upgradable.size()));
            ItemMeta resultMeta = result.getItemMeta();
            resultMeta.addEnchant(selected, meta.getEnchantLevel(selected) + 1, true);
            result.setItemMeta(resultMeta);
        } else if (meta instanceof Damageable damageable) {
            int maxDurability = target.getType().getMaxDurability();
            int currentDamage = damageable.getDamage();
            int remaining = maxDurability - currentDamage;
            if (maxDurability <= 0 || remaining <= 0) return null;
            int remainingAfter = Math.max(1, (int) Math.round(remaining * 0.20D));
            Damageable resultDamage = (Damageable) result.getItemMeta();
            resultDamage.setDamage(maxDurability - remainingAfter);
            result.setItemMeta((ItemMeta) resultDamage);
        }
        return new Plan(signature, result, success, selected);
    }

    private boolean isSupportedTarget(ItemStack item) {
        if (item == null || item.getType().isAir() || plugin.getId(item) != null) return false;
        if (item.getType().getMaxDurability() <= 0 || !(item.getItemMeta() instanceof Damageable)) return false;
        return !item.getEnchantments().isEmpty();
    }
}
