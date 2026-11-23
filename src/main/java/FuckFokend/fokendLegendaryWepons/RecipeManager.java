package FuckFokend.fokendLegendaryWepons;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;

/** Registers the crafting recipes of all legendaries and checks that the server accepted them. */
final class RecipeManager {
    private final FokendLegendaryWepons plugin;

    RecipeManager(FokendLegendaryWepons plugin) {
        this.plugin = plugin;
    }

    void logRecipes() {
        int count = 0;
        for (String key : FokendLegendaryWepons.RECIPE_KEYS) {
            if (this.plugin.getServer().getRecipe(new NamespacedKey(this.plugin, key)) != null) {
                count++;
            } else {
                this.plugin.getLogger().warning("Рецепт НЕ зарегистрирован: " + key);
            }
        }
        this.plugin.getLogger().info("Рецептов зарегистрировано: " + count + "/" + FokendLegendaryWepons.RECIPE_KEYS.size());
    }

    void registerRecipes() {
        NamespacedKey kOrange = new NamespacedKey(this.plugin, "cb_orange");
        this.plugin.getServer().removeRecipe(kOrange);
        ShapedRecipe rOrange = new ShapedRecipe(kOrange, this.plugin.items.cbOrange());
        rOrange.shape("CDC", "NPN", "TET");
        rOrange.setIngredient('C', Material.COPPER_BLOCK);
        rOrange.setIngredient('D', Material.ANCIENT_DEBRIS);
        rOrange.setIngredient('N', Material.NETHERITE_INGOT);
        rOrange.setIngredient('P', Material.PIGLIN_HEAD);
        rOrange.setIngredient('T', Material.TOTEM_OF_UNDYING);
        rOrange.setIngredient('E', Material.ENDER_EYE);
        this.plugin.getServer().addRecipe(rOrange);

        RecipeChoice.ExactChoice cOrange = new RecipeChoice.ExactChoice(this.plugin.items.cbOrange());
        RecipeChoice.ExactChoice cPurple = new RecipeChoice.ExactChoice(this.plugin.items.cbPurple());
        RecipeChoice.ExactChoice cGreen = new RecipeChoice.ExactChoice(this.plugin.items.cbGreen());

        NamespacedKey kPurple = new NamespacedKey(this.plugin, "cb_purple");
        this.plugin.getServer().removeRecipe(kPurple);
        ShapedRecipe rPurple = new ShapedRecipe(kPurple, this.plugin.items.cbPurple());
        rPurple.shape(" SI", "SCS", "IS ");
        rPurple.setIngredient('S', Material.NETHERITE_SCRAP);
        rPurple.setIngredient('I', Material.NETHERITE_INGOT);
        rPurple.setIngredient('C', cOrange);
        this.plugin.getServer().addRecipe(rPurple);

        NamespacedKey kGreen = new NamespacedKey(this.plugin, "cb_green");
        this.plugin.getServer().removeRecipe(kGreen);
        ShapedRecipe rGreen = new ShapedRecipe(kGreen, this.plugin.items.cbGreen());
        rGreen.shape("BAB", "ANA", "BAB");
        rGreen.setIngredient('B', Material.BLUE_ICE);
        rGreen.setIngredient('A', Material.ENCHANTED_GOLDEN_APPLE);
        rGreen.setIngredient('N', Material.NETHERITE_INGOT);
        this.plugin.getServer().addRecipe(rGreen);

        NamespacedKey kClock = new NamespacedKey(this.plugin, "time_clock");
        this.plugin.getServer().removeRecipe(kClock);
        ShapedRecipe rClock = new ShapedRecipe(kClock, this.plugin.items.timeClock());
        rClock.shape("SNS", "PCP", "GBG");
        rClock.setIngredient('S', Material.NETHER_STAR);
        rClock.setIngredient('N', Material.NETHERITE_BLOCK);
        rClock.setIngredient('P', cPurple);
        rClock.setIngredient('C', Material.CLOCK);
        rClock.setIngredient('G', Material.GOLD_BLOCK);
        rClock.setIngredient('B', cOrange);
        this.plugin.getServer().addRecipe(rClock);

        NamespacedKey kSpear = new NamespacedKey(this.plugin, "ice_spear");
        this.plugin.getServer().removeRecipe(kSpear);
        ShapedRecipe rSpear = new ShapedRecipe(kSpear, this.plugin.items.iceSpear());
        rSpear.shape("IBI", "GSG", "INI");
        rSpear.setIngredient('I', Material.BLUE_ICE);
        rSpear.setIngredient('B', cOrange);
        rSpear.setIngredient('G', cGreen);
        rSpear.setIngredient('S', new RecipeChoice.MaterialChoice(Material.NETHERITE_SPEAR));
        rSpear.setIngredient('N', Material.NETHERITE_INGOT);
        this.plugin.getServer().addRecipe(rSpear);

        NamespacedKey kMask = new NamespacedKey(this.plugin, "default_mask");
        this.plugin.getServer().removeRecipe(kMask);
        ShapedRecipe rMask = new ShapedRecipe(kMask, this.plugin.items.defaultMask());
        rMask.shape("CHC", "NSN", "CEC");
        rMask.setIngredient('C', cOrange);
        rMask.setIngredient('H', new RecipeChoice.MaterialChoice(Material.NETHERITE_HELMET));
        rMask.setIngredient('N', Material.NETHERITE_BLOCK);
        rMask.setIngredient('S', Material.NETHER_STAR);
        rMask.setIngredient('E', Material.EMERALD_BLOCK);
        this.plugin.getServer().addRecipe(rMask);

        NamespacedKey kWarden = new NamespacedKey(this.plugin, "warden_helmet");
        this.plugin.getServer().removeRecipe(kWarden);
        ShapedRecipe rWarden = new ShapedRecipe(kWarden, this.plugin.items.wardenHelmet());
        rWarden.shape("HSH", "SCS", "N N");
        rWarden.setIngredient('H', new RecipeChoice.MaterialChoice(Material.NETHERITE_HELMET));
        rWarden.setIngredient('S', Material.WITHER_SKELETON_SKULL);
        rWarden.setIngredient('C', Material.SCULK_CATALYST);
        rWarden.setIngredient('N', Material.NETHERITE_INGOT);
        this.plugin.getServer().addRecipe(rWarden);

        NamespacedKey kRa = new NamespacedKey(this.plugin, "ra_blade");
        this.plugin.getServer().removeRecipe(kRa);
        ShapedRecipe rRa = new ShapedRecipe(kRa, this.plugin.items.raBlade());
        rRa.shape("ASA", "TMT", "RBR");
        rRa.setIngredient('A', Material.ENCHANTED_GOLDEN_APPLE);
        rRa.setIngredient('R', Material.NETHER_STAR);
        rRa.setIngredient('S', new RecipeChoice.MaterialChoice(Material.NETHERITE_SWORD));
        rRa.setIngredient('T', Material.TOTEM_OF_UNDYING);
        rRa.setIngredient('M', Material.MACE);
        rRa.setIngredient('B', Material.BLAZE_ROD);
        this.plugin.getServer().addRecipe(rRa);

        NamespacedKey kBoomerang = new NamespacedKey(this.plugin, "chaos_boomerang");
        this.plugin.getServer().removeRecipe(kBoomerang);
        ShapedRecipe rBoomerang = new ShapedRecipe(kBoomerang, this.plugin.items.chaosBoomerang());
        rBoomerang.shape("DSD", "HTH", "NBN");
        rBoomerang.setIngredient('H', Material.HEAVY_CORE);
        rBoomerang.setIngredient('D', new RecipeChoice.MaterialChoice(Material.DIAMOND_SWORD));
        rBoomerang.setIngredient('S', new RecipeChoice.MaterialChoice(Material.NETHERITE_SWORD));
        rBoomerang.setIngredient('T', new RecipeChoice.MaterialChoice(Material.TRIDENT, Material.NETHERITE_SWORD));
        rBoomerang.setIngredient('N', Material.NETHERITE_INGOT);
        rBoomerang.setIngredient('B', Material.SHIELD);
        this.plugin.getServer().addRecipe(rBoomerang);

        NamespacedKey kTwins = new NamespacedKey(this.plugin, "twins_sword");
        this.plugin.getServer().removeRecipe(kTwins);
        ShapedRecipe rTwins = new ShapedRecipe(kTwins, this.plugin.items.twinsSword());
        rTwins.shape("S S", "WNW", "RBR");
        rTwins.setIngredient('S', new RecipeChoice.MaterialChoice(Material.NETHERITE_SWORD));
        rTwins.setIngredient('W', Material.WITHER_SKELETON_SKULL);
        rTwins.setIngredient('N', Material.MUSIC_DISC_5);
        rTwins.setIngredient('R', Material.NETHER_STAR);
        rTwins.setIngredient('B', Material.END_ROD);
        this.plugin.getServer().addRecipe(rTwins);

        NamespacedKey kMagnet = new NamespacedKey(this.plugin, "magnet");
        this.plugin.getServer().removeRecipe(kMagnet);
        ShapedRecipe rMagnet = new ShapedRecipe(kMagnet, this.plugin.items.magnet());
        rMagnet.shape("RWB", "RMB", "NIN");
        rMagnet.setIngredient('R', Material.REDSTONE_BLOCK);
        rMagnet.setIngredient('B', Material.LAPIS_BLOCK);
        rMagnet.setIngredient('W', Material.WITHER_SKELETON_SKULL);
        rMagnet.setIngredient('M', Material.MACE);
        rMagnet.setIngredient('I', Material.IRON_BLOCK);
        rMagnet.setIngredient('N', Material.NETHERITE_INGOT);
        this.plugin.getServer().addRecipe(rMagnet);

        // The Happy Mask is only the worn form of the Chaos Mask, not a separate craft.
        // Remove the old recipe so it disappears after a reload too.
        this.plugin.getServer().removeRecipe(new NamespacedKey(this.plugin, "happy_mask"));

        NamespacedKey kDeath = new NamespacedKey(this.plugin, "death_note");
        this.plugin.getServer().removeRecipe(kDeath);
        ShapedRecipe rDeath = new ShapedRecipe(kDeath, DeathNoteListener.create(this.plugin));
        rDeath.shape("SWS", "NPN", "COC");
        rDeath.setIngredient('S', Material.WITHER_SKELETON_SKULL);
        rDeath.setIngredient('W', Material.WRITABLE_BOOK);
        rDeath.setIngredient('N', Material.NETHERITE_INGOT);
        rDeath.setIngredient('P', cPurple);
        rDeath.setIngredient('C', Material.CRYING_OBSIDIAN);
        rDeath.setIngredient('O', cOrange);
        this.plugin.getServer().addRecipe(rDeath);

        NamespacedKey kScales = new NamespacedKey(this.plugin, "fate_scales");
        this.plugin.getServer().removeRecipe(kScales);
        ShapedRecipe rScales = new ShapedRecipe(kScales, ScalesOfFateListener.createScales(this.plugin));
        rScales.shape("GNG", "PSA", "GTG");
        rScales.setIngredient('G', Material.GOLD_BLOCK);
        rScales.setIngredient('N', Material.NETHER_STAR);
        rScales.setIngredient('P', cPurple);
        rScales.setIngredient('S', new RecipeChoice.MaterialChoice(Material.NETHERITE_SWORD));
        rScales.setIngredient('A', cOrange);
        rScales.setIngredient('T', Material.TOTEM_OF_UNDYING);
        this.plugin.getServer().addRecipe(rScales);
    }
}
