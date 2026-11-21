package FuckFokend.fokendLegendaryWepons;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
import org.junit.jupiter.api.Test;

/** The core and Frost Spear recipes must match the ones announced in Discord. */
class AnnouncedRecipesTest extends PluginTestBase {

    private ShapedRecipe recipe(String key) {
        Recipe r = this.server.getRecipe(new NamespacedKey(this.plugin, key));
        return assertInstanceOf(ShapedRecipe.class, r, key + " is not registered");
    }

    private static void assertMaterial(Map<Character, RecipeChoice> choices, char c, Material expected) {
        RecipeChoice choice = choices.get(c);
        assertInstanceOf(RecipeChoice.MaterialChoice.class, choice, "slot " + c);
        assertEquals(java.util.List.of(expected), ((RecipeChoice.MaterialChoice) choice).getChoices(), "slot " + c);
    }

    @Test
    void impulseCore() {
        ShapedRecipe r = recipe("cb_orange");
        assertArrayEquals(new String[]{"CDC", "NPN", "TET"}, r.getShape());
        Map<Character, RecipeChoice> m = r.getChoiceMap();
        assertMaterial(m, 'C', Material.COPPER_BLOCK);
        assertMaterial(m, 'D', Material.ANCIENT_DEBRIS);
        assertMaterial(m, 'N', Material.NETHERITE_INGOT);
        assertMaterial(m, 'P', Material.PIGLIN_HEAD);
        assertMaterial(m, 'T', Material.TOTEM_OF_UNDYING);
        assertMaterial(m, 'E', Material.ENDER_EYE);
    }

    @Test
    void eternalCycleCoreIsMadeFromImpulseCore() {
        ShapedRecipe r = recipe("cb_purple");
        assertArrayEquals(new String[]{" SI", "SCS", "IS "}, r.getShape());
        Map<Character, RecipeChoice> m = r.getChoiceMap();
        assertMaterial(m, 'S', Material.NETHERITE_SCRAP);
        assertMaterial(m, 'I', Material.NETHERITE_INGOT);
        RecipeChoice core = m.get('C');
        assertInstanceOf(RecipeChoice.ExactChoice.class, core);
        assertTrue(core.test(this.plugin.cbOrange()), "centre must accept the Impulse Core");
    }

    @Test
    void singularityChainCore() {
        ShapedRecipe r = recipe("cb_green");
        assertArrayEquals(new String[]{"BAB", "ANA", "BAB"}, r.getShape());
        Map<Character, RecipeChoice> m = r.getChoiceMap();
        assertMaterial(m, 'B', Material.BLUE_ICE);
        assertMaterial(m, 'A', Material.ENCHANTED_GOLDEN_APPLE);
        assertMaterial(m, 'N', Material.NETHERITE_INGOT);
    }

    @Test
    void happyMaskIsNotCraftable() {
        assertEquals(null, this.server.getRecipe(new NamespacedKey(this.plugin, "happy_mask")),
                "the Happy Mask is the worn form of the Chaos Mask, not a separate recipe");
        assertTrue(!FokendLegendaryWepons.RECIPE_KEYS.contains("happy_mask"));
    }

    @Test
    void frostSpear() {
        ShapedRecipe r = recipe("ice_spear");
        assertArrayEquals(new String[]{"IBI", "GSG", "INI"}, r.getShape());
        Map<Character, RecipeChoice> m = r.getChoiceMap();
        assertMaterial(m, 'I', Material.BLUE_ICE);
        assertMaterial(m, 'S', Material.NETHERITE_SPEAR);
        assertMaterial(m, 'N', Material.NETHERITE_INGOT);
    }
}
