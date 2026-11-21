package com.cigaro.recipe;

import com.cigaro.Cigaro;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;

public class RecipeManager {

    private final Cigaro plugin;

    public RecipeManager(Cigaro plugin) {
        this.plugin = plugin;
    }

    public void registerAll() {
        registerDryingRack();
        registerRollingMachine();
        registerCuttingTable();
        registerPackingMachine();
        registerInfuser();
        registerFertilizer();
        registerDryingTimer();
        registerLighterOfStats();
        registerTestStrip();
        registerCigarettePaper();
        registerCardboard();
        registerFuelPellets();
        registerAutoDryingRack();
        registerCuttingMachine();
        registerRollingPress();
        registerPackingPress();
        registerPipe();
        registerHookah();
        registerBlendingTable();
        registerNicotinePatch();
        registerSmokescreenCigarette();
        plugin.getServer().getPluginManager().registerEvents(
                new org.bukkit.event.Listener() {
                    @org.bukkit.event.EventHandler
                    public void onPrepare(org.bukkit.event.inventory.PrepareItemCraftEvent event) {
                        org.bukkit.inventory.Recipe recipe = event.getRecipe();
                        if (!(recipe instanceof ShapedRecipe shaped)) return;
                        if (shaped.getKey().getKey().equals("smoking_pipe")) {
                            event.getInventory().setResult(
                                    plugin.getItemManager().createPipe()
                            );
                        }
                    }
                },
                plugin
        );
        plugin.getLogger().info("Recipes registered.");
    }

    private void registerDryingRack() {
        ItemStack result = plugin.getItemManager().createDryingRack();
        NamespacedKey key = new NamespacedKey(plugin, "drying_rack");
        ShapedRecipe recipe = new ShapedRecipe(key, result);

        recipe.shape("SSS", "SPS", "SSS");
        recipe.setIngredient('S', Material.STICK);
        recipe.setIngredient('P', Material.OAK_PLANKS);

        plugin.getServer().addRecipe(recipe);
    }
    private void registerCraftResultFixes() {
        plugin.getServer().getPluginManager().registerEvents(
                new org.bukkit.event.Listener() {

                    @org.bukkit.event.EventHandler
                    public void onPrepare(org.bukkit.event.inventory.PrepareItemCraftEvent event) {
                        org.bukkit.inventory.Recipe recipe = event.getRecipe();
                        if (recipe == null) return;

                        if (recipe instanceof ShapedRecipe shaped) {
                            String keyStr = shaped.getKey().getKey();

                            if (keyStr.equals("smoking_pipe")) {
                                event.getInventory().setResult(
                                        plugin.getItemManager().createPipe()
                                );
                            }
                        }
                    }
                },
                plugin
        );
    }
    private void registerRollingMachine() {
        ItemStack result = plugin.getItemManager().createRollingMachine();
        NamespacedKey key = new NamespacedKey(plugin, "rolling_machine");
        ShapedRecipe recipe = new ShapedRecipe(key, result);

        recipe.shape("IPI", "IRI", "SSS");
        recipe.setIngredient('I', Material.IRON_INGOT);
        recipe.setIngredient('P', Material.PISTON);
        recipe.setIngredient('R', Material.REDSTONE);
        recipe.setIngredient('S', Material.SMOOTH_STONE);

        plugin.getServer().addRecipe(recipe);
    }
    private void registerInfuser() {
        ItemStack result = plugin.getItemManager().createInfuser();
        NamespacedKey key = new NamespacedKey(plugin, "infuser");
        ShapedRecipe recipe = new ShapedRecipe(key, result);

        recipe.shape("GBG", "GIG", "SSS");
        recipe.setIngredient('G', Material.GLASS);
        recipe.setIngredient('B', Material.GLASS_BOTTLE);
        recipe.setIngredient('I', Material.IRON_INGOT);
        recipe.setIngredient('S', Material.SMOOTH_STONE);

        plugin.getServer().addRecipe(recipe);
    }
    private void registerPackingMachine() {
        ItemStack result = plugin.getItemManager()
                .createPackingMachine();
        NamespacedKey key = new NamespacedKey(
                plugin, "packing_machine");
        ShapedRecipe recipe = new ShapedRecipe(key, result);

        recipe.shape("IPI", "IRI", "III");
        recipe.setIngredient('I', Material.IRON_INGOT);
        recipe.setIngredient('P', Material.PISTON);
        recipe.setIngredient('R', Material.REDSTONE);

        plugin.getServer().addRecipe(recipe);
    }
    private void registerCuttingTable() {
        ItemStack result = plugin.getItemManager().createCuttingTable();
        NamespacedKey key = new NamespacedKey(plugin, "cutting_table");
        ShapedRecipe recipe = new ShapedRecipe(key, result);

        recipe.shape("FFF", "SIS", "SSS");
        recipe.setIngredient('F', Material.FLINT);
        recipe.setIngredient('S', Material.SMOOTH_STONE);
        recipe.setIngredient('I', Material.IRON_INGOT);

        plugin.getServer().addRecipe(recipe);
    }

    private void registerFertilizer() {
        ItemStack result = plugin.getItemManager().createTobaccoFertilizer(4);
        NamespacedKey key = new NamespacedKey(plugin, "tobacco_fertilizer");
        ShapedRecipe recipe = new ShapedRecipe(key, result);

        recipe.shape("GDG", "DBD", "GDG");
        recipe.setIngredient('G', Material.GREEN_DYE);
        recipe.setIngredient('D', Material.DANDELION);
        recipe.setIngredient('B', Material.BONE_MEAL);

        plugin.getServer().addRecipe(recipe);
    }

    private void registerDryingTimer() {
        ItemStack result = plugin.getItemManager().createDryingTimer();
        NamespacedKey key = new NamespacedKey(plugin, "drying_timer");
        ShapedRecipe recipe = new ShapedRecipe(key, result);

        recipe.shape("ICI", "CRC", "ICI");
        recipe.setIngredient('I', Material.IRON_NUGGET);
        recipe.setIngredient('C', Material.CLOCK);
        recipe.setIngredient('R', Material.REDSTONE);

        plugin.getServer().addRecipe(recipe);
    }

    private void registerLighterOfStats() {
        ItemStack result = plugin.getItemManager().createLighterOfStats();
        NamespacedKey key = new NamespacedKey(plugin, "lighter_stats");
        ShapedRecipe recipe = new ShapedRecipe(key, result);

        recipe.shape("GFG", "GRG", "GIG");
        recipe.setIngredient('G', Material.GOLD_NUGGET);
        recipe.setIngredient('F', Material.FLINT_AND_STEEL);
        recipe.setIngredient('R', Material.REDSTONE);
        recipe.setIngredient('I', Material.IRON_INGOT);

        plugin.getServer().addRecipe(recipe);
    }

    private void registerTestStrip() {
        ItemStack result = plugin.getItemManager().createTestStrip(4);
        NamespacedKey key = new NamespacedKey(plugin, "test_strip");
        ShapedRecipe recipe = new ShapedRecipe(key, result);

        recipe.shape("PBP", "PWP", "PPP");
        recipe.setIngredient('P', Material.PAPER);
        recipe.setIngredient('B', Material.BLUE_DYE);
        recipe.setIngredient('W', Material.WATER_BUCKET);

        plugin.getServer().addRecipe(recipe);
    }

    private void registerCigarettePaper() {
        ItemStack result = plugin.getItemManager().createCigarettePaper(4);
        NamespacedKey key = new NamespacedKey(plugin, "cigarette_paper");
        ShapedRecipe recipe = new ShapedRecipe(key, result);

        recipe.shape("S", "S", "N");
        recipe.setIngredient('S', Material.SUGAR_CANE);
        recipe.setIngredient('N', Material.SHEARS);

        plugin.getServer().addRecipe(recipe);
    }

    private void registerCardboard() {
        ItemStack result = plugin.getItemManager().createCardboard(4);
        NamespacedKey key = new NamespacedKey(plugin, "cardboard");
        ShapedRecipe recipe = new ShapedRecipe(key, result);

        recipe.shape("PPP", "PSP", "PPP");
        recipe.setIngredient('P', Material.PAPER);
        recipe.setIngredient('S', Material.SLIME_BALL);

        plugin.getServer().addRecipe(recipe);
    }

    private void registerFuelPellets() {
        ItemStack result = plugin.getItemManager().createFuelPellets(8);
        NamespacedKey key = new NamespacedKey(plugin, "fuel_pellets");
        ShapedRecipe recipe = new ShapedRecipe(key, result);

        recipe.shape("OOO", "OSO", "OOO");
        recipe.setIngredient('O', Material.OAK_WOOD);
        recipe.setIngredient('S', Material.SLIME_BALL);

        plugin.getServer().addRecipe(recipe);
    }

    private void registerAutoDryingRack() {
        ItemStack result = plugin.getItemManager().createAutoDryingRack();
        NamespacedKey key = new NamespacedKey(plugin, "auto_drying_rack");
        ShapedRecipe recipe = new ShapedRecipe(key, result);

        recipe.shape("IRI", "IDI", "SSS");
        recipe.setIngredient('I', Material.IRON_INGOT);
        recipe.setIngredient('R', Material.REDSTONE_BLOCK);
        recipe.setIngredient('D', Material.SMOKER);
        recipe.setIngredient('S', Material.SMOOTH_STONE);

        plugin.getServer().addRecipe(recipe);
    }

    private void registerCuttingMachine() {
        ItemStack result = plugin.getItemManager().createCuttingMachine();
        NamespacedKey key = new NamespacedKey(plugin, "cutting_machine");
        ShapedRecipe recipe = new ShapedRecipe(key, result);

        recipe.shape("FIF", "ICI", "SSS");
        recipe.setIngredient('F', Material.FLINT);
        recipe.setIngredient('I', Material.IRON_INGOT);
        recipe.setIngredient('C', Material.IRON_BARS);
        recipe.setIngredient('S', Material.SMOOTH_STONE);

        plugin.getServer().addRecipe(recipe);
    }

    private void registerRollingPress() {
        ItemStack result = plugin.getItemManager().createRollingPress();
        NamespacedKey key = new NamespacedKey(plugin, "rolling_press");
        ShapedRecipe recipe = new ShapedRecipe(key, result);

        recipe.shape("IPI", "RMR", "III");
        recipe.setIngredient('I', Material.IRON_INGOT);
        recipe.setIngredient('P', Material.PISTON);
        recipe.setIngredient('R', Material.REDSTONE);
        recipe.setIngredient('M', Material.NOTE_BLOCK);

        plugin.getServer().addRecipe(recipe);
    }

    private void registerPackingPress() {
        ItemStack result = plugin.getItemManager().createPackingPress();
        NamespacedKey key = new NamespacedKey(plugin, "packing_press");
        ShapedRecipe recipe = new ShapedRecipe(key, result);

        recipe.shape("ICI", "IPI", "III");
        recipe.setIngredient('I', Material.IRON_INGOT);
        recipe.setIngredient('C', Material.CRAFTING_TABLE);
        recipe.setIngredient('P', Material.PAPER);

        plugin.getServer().addRecipe(recipe);
    }

    private void registerPipe() {
        ItemStack result = plugin.getItemManager().createPipe();
        NamespacedKey key = new NamespacedKey(plugin, "smoking_pipe");
        ShapedRecipe recipe = new ShapedRecipe(key, result);

        recipe.shape("  I", " I ", "P  ");
        recipe.setIngredient('I', Material.IRON_INGOT);
        recipe.setIngredient('P', Material.OAK_PLANKS);

        plugin.getServer().addRecipe(recipe);
    }

    private void registerHookah() {
        ItemStack result = plugin.getItemManager().createHookah();
        NamespacedKey key = new NamespacedKey(plugin, "hookah");
        ShapedRecipe recipe = new ShapedRecipe(key, result);

        recipe.shape("IBI", "IGI", "SSS");
        recipe.setIngredient('I', Material.IRON_INGOT);
        recipe.setIngredient('B', Material.GLASS_BOTTLE);
        recipe.setIngredient('G', Material.GLASS);
        recipe.setIngredient('S', Material.SMOOTH_STONE);

        plugin.getServer().addRecipe(recipe);
    }

    private void registerBlendingTable() {
        ItemStack result = plugin.getItemManager()
                .createBlendingTable();
        NamespacedKey key = new NamespacedKey(
                plugin, "blending_table");
        ShapedRecipe recipe = new ShapedRecipe(key, result);

        recipe.shape("III", "SBS", "SSS");
        recipe.setIngredient('I', Material.IRON_INGOT);
        recipe.setIngredient('S', Material.SMOOTH_STONE);
        recipe.setIngredient('B', Material.BREWING_STAND);

        plugin.getServer().addRecipe(recipe);
    }

    private void registerNicotinePatch() {
        ItemStack result = plugin.getItemManager()
                .createNicotinePatch(2);
        NamespacedKey key = new NamespacedKey(
                plugin, "nicotine_patch");
        ShapedRecipe recipe = new ShapedRecipe(key, result);

        recipe.shape("PPP", "PSP", "PPP");
        recipe.setIngredient('P', Material.PAPER);
        recipe.setIngredient('S', Material.WHEAT_SEEDS);

        plugin.getServer().addRecipe(recipe);
    }

    private void registerSmokescreenCigarette() {
        ItemStack result = plugin.getItemManager().createSmokescreenCigarette(1);
        NamespacedKey key = new NamespacedKey(plugin, "smokescreen_cigarette");

        org.bukkit.inventory.ShapelessRecipe recipe = new org.bukkit.inventory.ShapelessRecipe(key, result);
        recipe.addIngredient(Material.PAPER);
        recipe.addIngredient(Material.GUNPOWDER);
        recipe.addIngredient(Material.DRIED_KELP);

        plugin.getServer().addRecipe(recipe);
    }
}