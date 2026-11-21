package com.cigaro;

import com.cigaro.command.CigaroCommand;
import com.cigaro.command.CigaroTabCompleter;
import com.cigaro.config.ConfigManager;
import com.cigaro.data.DatabaseManager;
import com.cigaro.data.PlayerDataManager;
import com.cigaro.gui.CigaroGui;
import com.cigaro.gui.GuiListener;
import com.cigaro.item.ItemManager;
import com.cigaro.listener.*;
import com.cigaro.mechanic.*;
import com.cigaro.recipe.RecipeManager;
import com.cigaro.station.DisplayManager;
import com.cigaro.station.StationManager;
import com.cigaro.task.WithdrawalTask;
import com.cigaro.tobacco.TobaccoManager;
import org.bukkit.plugin.java.JavaPlugin;

public final class Cigaro extends JavaPlugin {
    private WithdrawalEffectsManager withdrawalEffectsManager;
    private AddictionCalculator addictionCalculator;
    private VisualEffectsManager visualEffectsManager;
    private CoughManager coughManager;
    private TremorManager tremorManager;
    private LungDamageManager lungDamageManager;
    private static Cigaro instance;
    private ConfigManager configManager;
    private DatabaseManager databaseManager;
    private PlayerDataManager playerDataManager;
    private ItemManager itemManager;
    private TobaccoManager tobaccoManager;
    private StationManager stationManager;
    private RecipeManager recipeManager;
    private DisplayManager displayManager;
    private WithdrawalTask withdrawalTask;
    private SmokingListener smokingListener;
    private GuiListener guiListener;
    private AchievementManager achievementManager;
    private FireHazardManager fireHazardManager;
    private AnalyticsManager analyticsManager;
    private BreathingListener breathingListener;
    private PackingListener packingListener;
    private HookahListener hookahListener;
    private PassiveSmokingManager passiveSmokingManager;
    private com.cigaro.config.LanguageManager languageManager;
    private PipeListener pipeListener;

    @Override
    public void onEnable() {
        instance = this;

        configManager = new ConfigManager(this);
        configManager.load();
        languageManager = new com.cigaro.config.LanguageManager(this);
        languageManager.load();
        databaseManager = new DatabaseManager(this);
        databaseManager.initialize();

        playerDataManager = new PlayerDataManager(this);
        playerDataManager.startFlushTask();
        itemManager = new ItemManager(this);

        tobaccoManager = new TobaccoManager(this);
        tobaccoManager.start();

        displayManager = new DisplayManager(this);
        CigaroGui.init(this);
        stationManager = new StationManager(this);
        stationManager.start();

        guiListener = new GuiListener(this);

        recipeManager = new RecipeManager(this);
        recipeManager.registerAll();

        withdrawalTask = new WithdrawalTask(this);
        withdrawalTask.start();
        addictionCalculator = new AddictionCalculator(this);

        achievementManager = new AchievementManager(this);
        fireHazardManager = new FireHazardManager(this);
        analyticsManager = new AnalyticsManager(this);

        lungDamageManager = new LungDamageManager(this);

        visualEffectsManager = new VisualEffectsManager(this);
        visualEffectsManager.start();
        tremorManager = new TremorManager(this);
        tremorManager.start();
        var cmd = getCommand("cigaro");
        cmd.setExecutor(new CigaroCommand(this));
        cmd.setTabCompleter(new CigaroTabCompleter());
        var pm = getServer().getPluginManager();
        pm.registerEvents(guiListener, this);
        pm.registerEvents(new PlayerListener(this), this);
        pm.registerEvents(new TobaccoGrowthListener(this), this);
        pm.registerEvents(new FlavorDropListener(this), this);
        pm.registerEvents(new StationListener(this), this);

        coughManager = new CoughManager(this);
        pm.registerEvents(coughManager, this);
        pm.registerEvents(stationManager, this);
        withdrawalEffectsManager = new WithdrawalEffectsManager(this);
        pm.registerEvents(withdrawalEffectsManager, this);

        smokingListener = new SmokingListener(this);
        pm.registerEvents(smokingListener, this);

        pm.registerEvents(new BundleProtectionListener(this), this);

        breathingListener = new BreathingListener(this);
        packingListener = new PackingListener(this);
        hookahListener = new HookahListener(this);
        pipeListener = new PipeListener(this);
        passiveSmokingManager = new PassiveSmokingManager(this);

        pm.registerEvents(breathingListener, this);
        pm.registerEvents(passiveSmokingManager, this);
        pm.registerEvents(new ItemProtectionListener(this), this);
        pm.registerEvents(new PackListener(this), this);
        pm.registerEvents(packingListener, this);
        pm.registerEvents(new UtilityItemListener(this), this);
        pm.registerEvents(new MachineListener(this), this);
        pm.registerEvents(new StationProtectionListener(this), this);
        pm.registerEvents(hookahListener, this);
        pm.registerEvents(pipeListener, this);
        pm.registerEvents(new BlendingListener(this), this);
        pm.registerEvents(new InsomniaListener(this), this);
        pm.registerEvents(new PatchListener(this), this);
        pm.registerEvents(new DeathListener(this), this);

        pm.registerEvents(new ExploitProtectionListener(this), this);
        pm.registerEvents(new RpChatListener(this), this);
        pm.registerEvents(new MysticSmokesManager(this), this);

        getLogger().info("Cigaro loaded. Light up.");
    }

    @Override
    public void onDisable() {
        CigaroGui.closeAll();
        if (hookahListener != null) hookahListener.cleanupAll();
        if (withdrawalTask != null) withdrawalTask.stop();
        if (stationManager != null) stationManager.stop();
        if (tobaccoManager != null) tobaccoManager.stop();
        if (playerDataManager != null) {
            playerDataManager.stopFlushTask();
            playerDataManager.saveAll();
        }
        if (databaseManager != null) databaseManager.close();
        if (tremorManager != null) tremorManager.stop();
        if (analyticsManager != null) analyticsManager.save();
        if (visualEffectsManager != null) visualEffectsManager.stop();
        getLogger().info("Cigaro disabled. Last drag.");
        instance = null;
    }

    public WithdrawalTask getWithdrawalTask() { return withdrawalTask; }
    public CoughManager getCoughManager() { return coughManager; }
    public com.cigaro.config.LanguageManager getLanguageManager() { return languageManager; }
    public AddictionCalculator getAddictionCalculator() { return addictionCalculator; }
    public WithdrawalEffectsManager getWithdrawalEffectsManager() { return withdrawalEffectsManager; }
    public VisualEffectsManager getVisualEffectsManager() { return visualEffectsManager; }
    public static Cigaro getInstance() { return instance; }
    public ConfigManager getConfigManager() { return configManager; }
    public DatabaseManager getDatabaseManager() { return databaseManager; }
    public PlayerDataManager getPlayerDataManager() { return playerDataManager; }
    public ItemManager getItemManager() { return itemManager; }
    public TobaccoManager getTobaccoManager() { return tobaccoManager; }
    public StationManager getStationManager() { return stationManager; }
    public DisplayManager getDisplayManager() { return displayManager; }
    public SmokingListener getSmokingListener() { return smokingListener; }
    public GuiListener getGuiListener() { return guiListener; }
    public AchievementManager getAchievementManager() { return achievementManager; }
    public FireHazardManager getFireHazardManager() { return fireHazardManager; }
    public AnalyticsManager getAnalyticsManager() { return analyticsManager; }
    public BreathingListener getBreathingListener() { return breathingListener; }
    public PackingListener getPackingListener() { return packingListener; }
    public HookahListener getHookahListener() { return hookahListener; }
    public PipeListener getPipeListener() { return pipeListener; }
    public PassiveSmokingManager getPassiveSmokingManager() { return passiveSmokingManager; }
    public LungDamageManager getLungDamageManager() { return lungDamageManager; }
}

