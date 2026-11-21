package FuckFokend.fokendLegendaryWepons;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.bukkit.Material;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

class FateLightningContainmentTest extends PluginTestBase {

    private InventoryView openChest(PlayerMock player) {
        return player.openInventory(this.server.createInventory(null, InventoryType.CHEST));
    }

    private boolean numberKeyIntoChest(ItemStack hotbarItem) throws Exception {
        PlayerMock player = this.server.addPlayer();
        player.getInventory().setItem(0, hotbarItem);
        InventoryView view = this.openChest(player);
        InventoryClickEvent event = new InventoryClickEvent(view, InventoryType.SlotType.CONTAINER, 0,
                ClickType.NUMBER_KEY, InventoryAction.HOTBAR_SWAP, 0);
        callListener(InventoryClickEvent.getHandlerList(), ScalesOfFateListener.class, event);
        return event.isCancelled();
    }

    private boolean dragIntoChest(ItemStack cursor) throws Exception {
        PlayerMock player = this.server.addPlayer();
        InventoryView view = this.openChest(player);
        InventoryDragEvent event = new InventoryDragEvent(view, null, cursor, false, Map.of(0, cursor.clone()));
        callListener(InventoryDragEvent.getHandlerList(), ScalesOfFateListener.class, event);
        return event.isCancelled();
    }

    @Test
    void lightningCannotBeMovedIntoChestWithNumberKey() throws Exception {
        assertTrue(this.numberKeyIntoChest(ScalesOfFateListener.createLightning(this.plugin)));
    }

    @Test
    void lightningCannotBeDraggedIntoChest() throws Exception {
        assertTrue(this.dragIntoChest(ScalesOfFateListener.createLightning(this.plugin)));
    }

    @Test
    void ordinaryItemsAreNotAffected() throws Exception {
        assertFalse(this.numberKeyIntoChest(new ItemStack(Material.DIAMOND_AXE)));
        assertFalse(this.dragIntoChest(new ItemStack(Material.DIAMOND_AXE)));
    }
}
