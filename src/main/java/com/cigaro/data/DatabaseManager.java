package com.cigaro.data;

import com.cigaro.Cigaro;
import com.cigaro.station.*;
import com.cigaro.tobacco.TobaccoBlock;
import org.bukkit.Location;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

public class DatabaseManager {

    private final Cigaro plugin;
    private Connection connection;
    private ExecutorService ioExecutor;

    public DatabaseManager(Cigaro plugin) {
        this.plugin = plugin;
    }

    private void async(Runnable task) {
        ExecutorService executor = ioExecutor;
        if (executor == null || executor.isShutdown()) {
            task.run();
            return;
        }
        try {
            executor.execute(task);
        } catch (RejectedExecutionException e) {
            task.run();
        }
    }

    public void initialize() {
        try {
            File dbFile = new File(plugin.getDataFolder(), "cigaro.db");
            plugin.getDataFolder().mkdirs();

            connection = DriverManager.getConnection(
                    "jdbc:sqlite:" + dbFile.getAbsolutePath()
            );

            try (Statement pragma = connection.createStatement()) {
                pragma.execute("PRAGMA journal_mode=WAL;");
                pragma.execute("PRAGMA synchronous=NORMAL;");
                pragma.execute("PRAGMA busy_timeout=5000;");
            }

            createTables();
            migrateTables();

            ioExecutor = Executors.newSingleThreadExecutor(r -> {
                Thread thread = new Thread(r, "Cigaro-DB-IO");
                thread.setDaemon(true);
                return thread;
            });

            plugin.getLogger().info("Database connected.");
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to connect to database!", e);
        }
    }

    private void createTables() throws SQLException {
        try (Statement stmt = connection.createStatement()) {

            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS player_data (
                    uuid TEXT PRIMARY KEY,
                    addiction_stage INTEGER DEFAULT 0,
                    total_smoked INTEGER DEFAULT 0,
                    last_smoke_time BIGINT DEFAULT 0,
                    online_sober_seconds BIGINT DEFAULT 0,
                    tolerance_mint REAL DEFAULT 0.0,
                    tolerance_coffee REAL DEFAULT 0.0,
                    tolerance_caramel REAL DEFAULT 0.0,
                    tolerance_vanilla REAL DEFAULT 0.0,
                    tolerance_cherry REAL DEFAULT 0.0,
                    tolerance_spice REAL DEFAULT 0.0,
                    tolerance_herbal REAL DEFAULT 0.0,
                    tolerance_shadow REAL DEFAULT 0.0,
                    predisposition_mint INTEGER DEFAULT 0,
                    predisposition_coffee INTEGER DEFAULT 0,
                    predisposition_caramel INTEGER DEFAULT 0,
                    predisposition_vanilla INTEGER DEFAULT 0,
                    predisposition_cherry INTEGER DEFAULT 0,
                    predisposition_spice INTEGER DEFAULT 0,
                    predisposition_herbal INTEGER DEFAULT 0,
                    predisposition_shadow INTEGER DEFAULT 0,
                    predisposition_generated INTEGER DEFAULT 0,
                    mastery REAL DEFAULT 0.0,
                    smoked_mint INTEGER DEFAULT 0,
                    smoked_coffee INTEGER DEFAULT 0,
                    smoked_caramel INTEGER DEFAULT 0,
                    smoked_vanilla INTEGER DEFAULT 0,
                    smoked_cherry INTEGER DEFAULT 0,
                    smoked_spice INTEGER DEFAULT 0,
                    smoked_herbal INTEGER DEFAULT 0,
                    smoked_shadow INTEGER DEFAULT 0,
                    last_session_start BIGINT DEFAULT 0,
                    smoked_this_session INTEGER DEFAULT 0,
                    consecutive_days INTEGER DEFAULT 0,
                    last_day_smoked BIGINT DEFAULT 0,
                    last_smoked_type TEXT DEFAULT '',
                    same_type_streak INTEGER DEFAULT 0
                )
            """);

            stmt.executeUpdate("""
                CREATE INDEX IF NOT EXISTS idx_player_total_smoked
                ON player_data(total_smoked DESC)
            """);

            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS tobacco_plants (
                    world TEXT NOT NULL,
                    x INTEGER NOT NULL,
                    y INTEGER NOT NULL,
                    z INTEGER NOT NULL,
                    tobacco_type TEXT DEFAULT 'VIRGINIA',
                    stage INTEGER DEFAULT 0,
                    last_growth_time BIGINT DEFAULT 0,
                    fertilizer_bonus INTEGER DEFAULT 0,
                    PRIMARY KEY (world, x, y, z)
                )
            """);

            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS auto_drying_racks (
                    world TEXT NOT NULL,
                    x INTEGER NOT NULL,
                    y INTEGER NOT NULL,
                    z INTEGER NOT NULL,
                    fuel_ticks INTEGER DEFAULT 0,
                    items TEXT DEFAULT '',
                    leaf_entries TEXT DEFAULT '',
                    PRIMARY KEY (world, x, y, z)
                )
            """);

            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS cutting_machines (
                    world TEXT NOT NULL,
                    x INTEGER NOT NULL,
                    y INTEGER NOT NULL,
                    z INTEGER NOT NULL,
                    fuel_ticks INTEGER DEFAULT 0,
                    progress INTEGER DEFAULT 0,
                    leaf_item TEXT DEFAULT '',
                    fuel_item TEXT DEFAULT '',
                    tobacco_type TEXT DEFAULT '',
                    is_elite INTEGER DEFAULT 0,
                    drying_level TEXT DEFAULT '',
                    PRIMARY KEY (world, x, y, z)
                )
            """);

            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS rolling_presses (
                    world TEXT NOT NULL,
                    x INTEGER NOT NULL,
                    y INTEGER NOT NULL,
                    z INTEGER NOT NULL,
                    fuel_ticks INTEGER DEFAULT 0,
                    progress INTEGER DEFAULT 0,
                    tobacco_type TEXT DEFAULT '',
                    tobacco_amount INTEGER DEFAULT 0,
                    paper_amount INTEGER DEFAULT 0,
                    tobacco_item TEXT DEFAULT '',
                    paper_item TEXT DEFAULT '',
                    fuel_item TEXT DEFAULT '',
                    operator_mastery REAL DEFAULT 0.0,
                    operator_flavor_predisposition INTEGER DEFAULT 0,
                    PRIMARY KEY (world, x, y, z)
                )
            """);

            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS packing_presses (
                    world TEXT NOT NULL,
                    x INTEGER NOT NULL,
                    y INTEGER NOT NULL,
                    z INTEGER NOT NULL,
                    progress INTEGER DEFAULT 0,
                    cigarette_type TEXT DEFAULT '',
                    cigarette_count INTEGER DEFAULT 0,
                    cardboard_count INTEGER DEFAULT 0,
                    cigarette_item TEXT DEFAULT '',
                    cardboard_item TEXT DEFAULT '',
                    dye_color TEXT DEFAULT '',
                    dye_item TEXT DEFAULT '',
                    PRIMARY KEY (world, x, y, z)
                )
            """);

            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS packing_stations_manual (
                    world TEXT NOT NULL,
                    x INTEGER NOT NULL,
                    y INTEGER NOT NULL,
                    z INTEGER NOT NULL,
                    items_base64 TEXT DEFAULT '',
                    dye_color TEXT DEFAULT '',
                    cranks INTEGER DEFAULT 0,
                    has_items INTEGER DEFAULT 0,
                    PRIMARY KEY (world, x, y, z)
                )
            """);

            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS drying_stations (
                    world TEXT NOT NULL,
                    x INTEGER NOT NULL,
                    y INTEGER NOT NULL,
                    z INTEGER NOT NULL,
                    leaves_data TEXT DEFAULT '',
                    display_entities TEXT DEFAULT '',
                    block_display TEXT,
                    PRIMARY KEY (world, x, y, z)
                )
            """);

            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS rolling_stations (
                    world TEXT NOT NULL,
                    x INTEGER NOT NULL,
                    y INTEGER NOT NULL,
                    z INTEGER NOT NULL,
                    has_leaf INTEGER DEFAULT 0,
                    leaf_type TEXT,
                    elite_leaf INTEGER DEFAULT 0,
                    drying_level TEXT,
                    cut_quality INTEGER DEFAULT 0,
                    has_flavor INTEGER DEFAULT 0,
                    flavor_type TEXT,
                    start_time BIGINT DEFAULT 0,
                    display_entity TEXT,
                    block_display TEXT,
                    is_cigar_mode INTEGER DEFAULT 0,
                    cigar_leaves_count INTEGER DEFAULT 0,
                    blend_data TEXT DEFAULT '',
                    PRIMARY KEY (world, x, y, z)
                )
            """);

            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS hookah_stations (
                    world TEXT NOT NULL,
                    x INTEGER NOT NULL,
                    y INTEGER NOT NULL,
                    z INTEGER NOT NULL,
                    hookah_data TEXT DEFAULT '',
                    display_entity TEXT,
                    PRIMARY KEY (world, x, y, z)
                )
            """);

            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS cutting_tables (
                    world TEXT NOT NULL,
                    x INTEGER NOT NULL,
                    y INTEGER NOT NULL,
                    z INTEGER NOT NULL,
                    PRIMARY KEY (world, x, y, z)
                )
            """);

            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS blending_tables (
                    world TEXT NOT NULL,
                    x INTEGER NOT NULL,
                    y INTEGER NOT NULL,
                    z INTEGER NOT NULL,
                    PRIMARY KEY (world, x, y, z)
                )
            """);

            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS infusers (
                    world TEXT NOT NULL,
                    x INTEGER NOT NULL,
                    y INTEGER NOT NULL,
                    z INTEGER NOT NULL,
                    PRIMARY KEY (world, x, y, z)
                )
            """);
        }
    }

    private void migrateTables() {
        String[] migrations = {
                "ALTER TABLE player_data ADD COLUMN tolerance_cherry REAL DEFAULT 0.0",
                "ALTER TABLE player_data ADD COLUMN tolerance_spice REAL DEFAULT 0.0",
                "ALTER TABLE player_data ADD COLUMN tolerance_herbal REAL DEFAULT 0.0",
                "ALTER TABLE player_data ADD COLUMN tolerance_shadow REAL DEFAULT 0.0",
                "ALTER TABLE player_data ADD COLUMN predisposition_cherry INTEGER DEFAULT 0",
                "ALTER TABLE player_data ADD COLUMN predisposition_spice INTEGER DEFAULT 0",
                "ALTER TABLE player_data ADD COLUMN predisposition_herbal INTEGER DEFAULT 0",
                "ALTER TABLE player_data ADD COLUMN predisposition_shadow INTEGER DEFAULT 0",
                "ALTER TABLE player_data ADD COLUMN smoked_cherry INTEGER DEFAULT 0",
                "ALTER TABLE player_data ADD COLUMN smoked_spice INTEGER DEFAULT 0",
                "ALTER TABLE player_data ADD COLUMN smoked_herbal INTEGER DEFAULT 0",
                "ALTER TABLE player_data ADD COLUMN smoked_shadow INTEGER DEFAULT 0",
                "ALTER TABLE player_data ADD COLUMN last_session_start BIGINT DEFAULT 0",
                "ALTER TABLE player_data ADD COLUMN online_sober_seconds BIGINT DEFAULT 0",
                "ALTER TABLE player_data ADD COLUMN smoked_this_session INTEGER DEFAULT 0",
                "ALTER TABLE player_data ADD COLUMN consecutive_days INTEGER DEFAULT 0",
                "ALTER TABLE player_data ADD COLUMN last_day_smoked BIGINT DEFAULT 0",
                "ALTER TABLE player_data ADD COLUMN last_smoked_type TEXT DEFAULT ''",
                "ALTER TABLE player_data ADD COLUMN same_type_streak INTEGER DEFAULT 0",
                "ALTER TABLE player_data ADD COLUMN patch_applied_time BIGINT DEFAULT 0",
                "ALTER TABLE player_data ADD COLUMN was_addicted INTEGER DEFAULT 0",
                "ALTER TABLE player_data ADD COLUMN achievements BIGINT DEFAULT 0",
                "ALTER TABLE player_data ADD COLUMN addon_data TEXT DEFAULT '{}'",
                "ALTER TABLE tobacco_plants ADD COLUMN tobacco_type TEXT DEFAULT 'VIRGINIA'",
                "ALTER TABLE tobacco_plants ADD COLUMN fertilizer_bonus INTEGER DEFAULT 0",
                "ALTER TABLE packing_presses ADD COLUMN dye_color TEXT DEFAULT ''",
                "ALTER TABLE tobacco_plants ADD COLUMN interaction_id TEXT DEFAULT ''",
                "ALTER TABLE packing_presses ADD COLUMN dye_item TEXT DEFAULT ''",
                "ALTER TABLE rolling_stations ADD COLUMN is_cigar_mode INTEGER DEFAULT 0",
                "ALTER TABLE rolling_stations ADD COLUMN cigar_leaves_count INTEGER DEFAULT 0",
                "ALTER TABLE rolling_stations ADD COLUMN blend_data TEXT DEFAULT ''",
                "ALTER TABLE rolling_presses ADD COLUMN operator_mastery REAL DEFAULT 0.0",
                "ALTER TABLE rolling_presses ADD COLUMN operator_flavor_predisposition INTEGER DEFAULT 0",
                "CREATE INDEX IF NOT EXISTS idx_player_total_smoked ON player_data(total_smoked DESC)"
        };

        for (String sql : migrations) {
            try (Statement stmt = connection.createStatement()) {
                stmt.executeUpdate(sql);
            } catch (SQLException ignored) {}
        }
    }

    public void savePlayerData(PlayerData data) {
        async(() -> savePlayerDataSync(data));
    }

    public void savePlayerDataSync(PlayerData data) {
        String sql = """
            INSERT OR REPLACE INTO player_data (
                uuid, addiction_stage, total_smoked, last_smoke_time,
                online_sober_seconds,
                tolerance_mint, tolerance_coffee,
                tolerance_caramel, tolerance_vanilla,
                tolerance_cherry, tolerance_spice,
                tolerance_herbal, tolerance_shadow,
                predisposition_mint, predisposition_coffee,
                predisposition_caramel, predisposition_vanilla,
                predisposition_cherry, predisposition_spice,
                predisposition_herbal, predisposition_shadow,
                predisposition_generated,
                mastery,
                smoked_mint, smoked_coffee,
                smoked_caramel, smoked_vanilla,
                smoked_cherry, smoked_spice,
                smoked_herbal, smoked_shadow,
                last_session_start, smoked_this_session,
                consecutive_days, last_day_smoked,
                last_smoked_type, same_type_streak,
                patch_applied_time, was_addicted, achievements, addon_data
            ) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
        """;

        synchronized (connection) {
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                int i = 1;
                ps.setString(i++, data.getUuid().toString());
                ps.setInt(i++, data.getAddictionStage());
                ps.setInt(i++, data.getTotalSmoked());
                ps.setLong(i++, data.getLastSmokeTime());
                ps.setLong(i++, data.getOnlineSoberSeconds());
                ps.setDouble(i++, data.getTolerance(CigaretteType.MINT));
                ps.setDouble(i++, data.getTolerance(CigaretteType.COFFEE));
                ps.setDouble(i++, data.getTolerance(CigaretteType.CARAMEL));
                ps.setDouble(i++, data.getTolerance(CigaretteType.VANILLA));
                ps.setDouble(i++, data.getTolerance(CigaretteType.CHERRY));
                ps.setDouble(i++, data.getTolerance(CigaretteType.SPICE));
                ps.setDouble(i++, data.getTolerance(CigaretteType.HERBAL));
                ps.setDouble(i++, data.getTolerance(CigaretteType.SHADOW));
                ps.setInt(i++, data.getPredisposition(CigaretteType.MINT));
                ps.setInt(i++, data.getPredisposition(CigaretteType.COFFEE));
                ps.setInt(i++, data.getPredisposition(CigaretteType.CARAMEL));
                ps.setInt(i++, data.getPredisposition(CigaretteType.VANILLA));
                ps.setInt(i++, data.getPredisposition(CigaretteType.CHERRY));
                ps.setInt(i++, data.getPredisposition(CigaretteType.SPICE));
                ps.setInt(i++, data.getPredisposition(CigaretteType.HERBAL));
                ps.setInt(i++, data.getPredisposition(CigaretteType.SHADOW));
                ps.setInt(i++, data.isPredispositionGenerated() ? 1 : 0);
                ps.setDouble(i++, data.getMastery());
                ps.setInt(i++, data.getSmoked(CigaretteType.MINT));
                ps.setInt(i++, data.getSmoked(CigaretteType.COFFEE));
                ps.setInt(i++, data.getSmoked(CigaretteType.CARAMEL));
                ps.setInt(i++, data.getSmoked(CigaretteType.VANILLA));
                ps.setInt(i++, data.getSmoked(CigaretteType.CHERRY));
                ps.setInt(i++, data.getSmoked(CigaretteType.SPICE));
                ps.setInt(i++, data.getSmoked(CigaretteType.HERBAL));
                ps.setInt(i++, data.getSmoked(CigaretteType.SHADOW));
                ps.setLong(i++, data.getLastSessionStart());
                ps.setInt(i++, data.getSmokedThisSession());
                ps.setInt(i++, data.getConsecutiveDays());
                ps.setLong(i++, data.getLastDaySmoked());
                ps.setString(i++, data.getLastSmokedType());
                ps.setInt(i++, data.getSameTypeStreak());
                ps.setLong(i++, data.getPatchAppliedTime());
                ps.setInt(i++, data.wasAddicted() ? 1 : 0);
                ps.setLong(i++, data.getAchievements());
                ps.setString(i++, data.serializeAddonData());
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to save data sync for " + data.getUuid());
            }
        }
    }

    public PlayerData loadPlayerData(UUID uuid) {
        String sql = "SELECT * FROM player_data WHERE uuid = ?";

        synchronized (connection) {
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setString(1, uuid.toString());
                ResultSet rs = ps.executeQuery();

                if (rs.next()) {
                    PlayerData data = new PlayerData(uuid);
                    data.setAddictionStage(rs.getInt("addiction_stage"));
                    data.setTotalSmoked(rs.getInt("total_smoked"));
                    data.setLastSmokeTime(rs.getLong("last_smoke_time"));
                    data.setOnlineSoberSeconds(rs.getLong("online_sober_seconds"));

                    data.setToleranceRaw(CigaretteType.MINT.name(), rs.getDouble("tolerance_mint"));
                    data.setToleranceRaw(CigaretteType.COFFEE.name(), rs.getDouble("tolerance_coffee"));
                    data.setToleranceRaw(CigaretteType.CARAMEL.name(), rs.getDouble("tolerance_caramel"));
                    data.setToleranceRaw(CigaretteType.VANILLA.name(), rs.getDouble("tolerance_vanilla"));
                    data.setToleranceRaw(CigaretteType.CHERRY.name(), getDoubleSafe(rs, "tolerance_cherry", 0.0));
                    data.setToleranceRaw(CigaretteType.SPICE.name(), getDoubleSafe(rs, "tolerance_spice", 0.0));
                    data.setToleranceRaw(CigaretteType.HERBAL.name(), getDoubleSafe(rs, "tolerance_herbal", 0.0));
                    data.setToleranceRaw(CigaretteType.SHADOW.name(), getDoubleSafe(rs, "tolerance_shadow", 0.0));

                    data.setPredispositionRaw(CigaretteType.MINT.name(), rs.getInt("predisposition_mint"));
                    data.setPredispositionRaw(CigaretteType.COFFEE.name(), rs.getInt("predisposition_coffee"));
                    data.setPredispositionRaw(CigaretteType.CARAMEL.name(), rs.getInt("predisposition_caramel"));
                    data.setPredispositionRaw(CigaretteType.VANILLA.name(), rs.getInt("predisposition_vanilla"));
                    data.setPredispositionRaw(CigaretteType.CHERRY.name(), getIntSafe(rs, "predisposition_cherry", 0));
                    data.setPredispositionRaw(CigaretteType.SPICE.name(), getIntSafe(rs, "predisposition_spice", 0));
                    data.setPredispositionRaw(CigaretteType.HERBAL.name(), getIntSafe(rs, "predisposition_herbal", 0));
                    data.setPredispositionRaw(CigaretteType.SHADOW.name(), getIntSafe(rs, "predisposition_shadow", 0));
                    data.setPredispositionGenerated(rs.getInt("predisposition_generated") == 1);

                    data.setMastery(rs.getDouble("mastery"));

                    data.setSmokedRaw(CigaretteType.MINT.name(), rs.getInt("smoked_mint"));
                    data.setSmokedRaw(CigaretteType.COFFEE.name(), rs.getInt("smoked_coffee"));
                    data.setSmokedRaw(CigaretteType.CARAMEL.name(), rs.getInt("smoked_caramel"));
                    data.setSmokedRaw(CigaretteType.VANILLA.name(), rs.getInt("smoked_vanilla"));
                    data.setSmokedRaw(CigaretteType.CHERRY.name(), getIntSafe(rs, "smoked_cherry", 0));
                    data.setSmokedRaw(CigaretteType.SPICE.name(), getIntSafe(rs, "smoked_spice", 0));
                    data.setSmokedRaw(CigaretteType.HERBAL.name(), getIntSafe(rs, "smoked_herbal", 0));
                    data.setSmokedRaw(CigaretteType.SHADOW.name(), getIntSafe(rs, "smoked_shadow", 0));

                    data.setLastSessionStart(getLongSafe(rs, "last_session_start", 0));
                    data.setSmokedThisSession(getIntSafe(rs, "smoked_this_session", 0));
                    data.setConsecutiveDays(getIntSafe(rs, "consecutive_days", 0));
                    data.setLastDaySmoked(getLongSafe(rs, "last_day_smoked", 0));
                    data.setLastSmokedType(getStringSafe(rs, "last_smoked_type", ""));
                    data.setSameTypeStreak(getIntSafe(rs, "same_type_streak", 0));

                    data.setPatchAppliedTime(getLongSafe(rs, "patch_applied_time", 0));
                    data.setWasAddicted(getIntSafe(rs, "was_addicted", 0) == 1);
                    data.setAchievements(getLongSafe(rs, "achievements", 0));

                    data.deserializeAddonData(getStringSafe(rs, "addon_data", "{}"));

                    return data;
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to load data for " + uuid, e);
            }
        }

        return new PlayerData(uuid);
    }

    public void saveTobaccoPlant(TobaccoBlock plant) {
        async(() -> saveTobaccoPlantSync(plant));
    }

    public void saveTobaccoPlantSync(TobaccoBlock plant) {
        String sql = """
            INSERT OR REPLACE INTO tobacco_plants
            (world, x, y, z, tobacco_type, stage, last_growth_time, fertilizer_bonus, interaction_id)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
        """;
        synchronized (connection) {
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                Location loc = plant.getLocation();
                ps.setString(1, loc.getWorld().getName());
                ps.setInt(2, loc.getBlockX());
                ps.setInt(3, loc.getBlockY());
                ps.setInt(4, loc.getBlockZ());
                ps.setString(5, plant.getTobaccoType().name());
                ps.setInt(6, plant.getStage());
                ps.setLong(7, 0L);
                ps.setInt(8, plant.getFertilizerBonus());
                ps.setString(9, plant.getInteractionId() != null
                        ? plant.getInteractionId().toString() : "");
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to save tobacco plant", e);
            }
        }
    }

    public void removeTobaccoPlant(Location loc) {
        String worldName = loc.getWorld().getName();
        int x = loc.getBlockX(), y = loc.getBlockY(), z = loc.getBlockZ();
        async(() -> {
            synchronized (connection) {
                String sql = "DELETE FROM tobacco_plants WHERE world = ? AND x = ? AND y = ? AND z = ?";
                try (PreparedStatement ps = connection.prepareStatement(sql)) {
                    ps.setString(1, worldName);
                    ps.setInt(2, x);
                    ps.setInt(3, y);
                    ps.setInt(4, z);
                    ps.executeUpdate();
                } catch (SQLException e) {
                    plugin.getLogger().log(Level.SEVERE, "Failed to remove tobacco plant", e);
                }
            }
        });
    }

    public List<TobaccoBlock> loadAllTobaccoPlants() {
        List<TobaccoBlock> plants = new ArrayList<>();
        String sql = "SELECT * FROM tobacco_plants";
        synchronized (connection) {
            try (Statement stmt = connection.createStatement();
                 ResultSet rs = stmt.executeQuery(sql)) {
                while (rs.next()) {
                    org.bukkit.World world = plugin.getServer().getWorld(rs.getString("world"));
                    if (world == null) continue;

                    Location loc = new Location(world, rs.getInt("x"), rs.getInt("y"), rs.getInt("z"));

                    TobaccoType tobaccoType;
                    try {
                        tobaccoType = TobaccoType.valueOf(rs.getString("tobacco_type"));
                    } catch (Exception e) {
                        tobaccoType = TobaccoType.VIRGINIA;
                    }

                    TobaccoBlock plant = new TobaccoBlock(loc, tobaccoType, rs.getInt("stage"));
                    plant.setFertilizerBonus(rs.getInt("fertilizer_bonus"));

                    String interIdStr = getStringSafe(rs, "interaction_id", "");
                    if (!interIdStr.isEmpty()) {
                        try {
                            plant.setInteractionId(UUID.fromString(interIdStr));
                        } catch (Exception ignored) {}
                    }

                    plants.add(plant);
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to load tobacco plants", e);
            }
        }
        return plants;
    }

    public void saveDryingStation(DryingStation station) {
        Location loc = station.getLocation();
        String leavesData = serializeLeafEntries(new ArrayList<>(station.getLeafEntries()));
        String displayEntities = serializeUUIDList(
                station.getDisplayEntityIds() != null ? new ArrayList<>(station.getDisplayEntityIds()) : List.of());
        String blockDisplay = station.getBlockDisplayId() != null ? station.getBlockDisplayId().toString() : null;
        async(() -> saveDryingStationRaw(loc, leavesData, displayEntities, blockDisplay));
    }

    public void saveDryingStationSync(DryingStation station) {
        Location loc = station.getLocation();
        String leavesData = serializeLeafEntries(new ArrayList<>(station.getLeafEntries()));
        String displayEntities = serializeUUIDList(
                station.getDisplayEntityIds() != null ? new ArrayList<>(station.getDisplayEntityIds()) : List.of());
        String blockDisplay = station.getBlockDisplayId() != null ? station.getBlockDisplayId().toString() : null;
        saveDryingStationRaw(loc, leavesData, displayEntities, blockDisplay);
    }

    private void saveDryingStationRaw(Location loc, String leavesData, String displayEntities, String blockDisplay) {
        String sql = """
            INSERT OR REPLACE INTO drying_stations
            (world, x, y, z, leaves_data, display_entities, block_display)
            VALUES (?, ?, ?, ?, ?, ?, ?)
        """;
        synchronized (connection) {
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setString(1, loc.getWorld().getName());
                ps.setInt(2, loc.getBlockX());
                ps.setInt(3, loc.getBlockY());
                ps.setInt(4, loc.getBlockZ());
                ps.setString(5, leavesData);
                ps.setString(6, displayEntities);
                ps.setString(7, blockDisplay);
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to save drying station", e);
            }
        }
    }

    public void removeDryingStation(Location loc) {
        async(() -> {
            synchronized (connection) {
                deleteByLocation("drying_stations", loc);
            }
        });
    }

    public List<DryingStation> loadAllDryingStations() {
        List<DryingStation> list = new ArrayList<>();
        String sql = "SELECT * FROM drying_stations";
        synchronized (connection) {
            try (Statement stmt = connection.createStatement();
                 ResultSet rs = stmt.executeQuery(sql)) {
                while (rs.next()) {
                    org.bukkit.World world = plugin.getServer().getWorld(rs.getString("world"));
                    if (world == null) continue;

                    Location loc = new Location(world, rs.getInt("x"), rs.getInt("y"), rs.getInt("z"));
                    DryingStation station = new DryingStation(loc);

                    deserializeLeafEntries(rs.getString("leaves_data"), station);

                    String displayStr = rs.getString("display_entities");
                    if (displayStr != null && !displayStr.isEmpty()) {
                        station.setDisplayEntityIds(deserializeUUIDList(displayStr));
                    }

                    String blockDisplay = rs.getString("block_display");
                    if (blockDisplay != null && !blockDisplay.isEmpty()) {
                        station.setBlockDisplayId(UUID.fromString(blockDisplay));
                    }

                    list.add(station);
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to load drying stations", e);
            }
        }
        return list;
    }

    public void saveRollingStation(RollingStation station) {
        async(() -> saveRollingStationSync(station));
    }

    public void saveRollingStationSync(RollingStation station) {
        String sql = """
            INSERT OR REPLACE INTO rolling_stations
            (world, x, y, z, has_leaf, leaf_type, elite_leaf,
             drying_level, cut_quality, has_flavor, flavor_type,
             start_time, display_entity, block_display,
             is_cigar_mode, cigar_leaves_count, blend_data)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """;
        synchronized (connection) {
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                Location loc = station.getLocation();
                ps.setString(1, loc.getWorld().getName());
                ps.setInt(2, loc.getBlockX());
                ps.setInt(3, loc.getBlockY());
                ps.setInt(4, loc.getBlockZ());
                ps.setInt(5, station.hasLeaf() ? 1 : 0);
                ps.setString(6, station.getLeafType() != null
                        ? station.getLeafType().name() : null);
                ps.setInt(7, station.isEliteLeaf() ? 1 : 0);
                ps.setString(8, station.getDryingLevel() != null
                        ? station.getDryingLevel().name() : null);
                ps.setInt(9, station.getCutQuality());
                ps.setInt(10, station.hasFlavor() ? 1 : 0);
                ps.setString(11, station.getFlavorType() != null
                        ? station.getFlavorType().name() : null);
                ps.setLong(12, station.getStartTime());
                ps.setString(13, station.getDisplayEntityId() != null
                        ? station.getDisplayEntityId().toString() : null);
                ps.setString(14, station.getBlockDisplayId() != null
                        ? station.getBlockDisplayId().toString() : null);
                ps.setInt(15, station.isCigarMode() ? 1 : 0);
                ps.setInt(16, station.getCigarLeavesCount());
                ps.setString(17, station.getBlend() != null
                        ? station.getBlend().serialize() : "");
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to save rolling station", e);
            }
        }
    }

    public void removeRollingStation(Location loc) {
        async(() -> {
            synchronized (connection) {
                deleteByLocation("rolling_stations", loc);
            }
        });
    }

    public List<RollingStation> loadAllRollingStations() {
        List<RollingStation> list = new ArrayList<>();
        String sql = "SELECT * FROM rolling_stations";
        synchronized (connection) {
            try (Statement stmt = connection.createStatement();
                 ResultSet rs = stmt.executeQuery(sql)) {
                while (rs.next()) {
                    org.bukkit.World world = plugin.getServer().getWorld(rs.getString("world"));
                    if (world == null) continue;

                    Location loc = new Location(world, rs.getInt("x"), rs.getInt("y"), rs.getInt("z"));
                    RollingStation station = new RollingStation(loc);

                    if (rs.getInt("has_leaf") == 1) {
                        String leafTypeStr = rs.getString("leaf_type");
                        String dryingStr = rs.getString("drying_level");
                        String blendStr = getStringSafe(rs, "blend_data", "");
                        BlendData blend = BlendData.deserialize(blendStr);
                        if (leafTypeStr != null) {
                            station.loadBlendedLeaf(
                                    TobaccoType.valueOf(leafTypeStr),
                                    rs.getInt("elite_leaf") == 1,
                                    dryingStr != null
                                            ? DryingLevel.valueOf(dryingStr)
                                            : DryingLevel.RAW,
                                    blend
                            );
                            station.setCutQuality(rs.getInt("cut_quality"));
                        }
                    }

                    if (rs.getInt("has_flavor") == 1) {
                        String flavorStr = rs.getString("flavor_type");
                        if (flavorStr != null) {
                            station.addFlavor(CigaretteType.valueOf(flavorStr));
                            station.setStartTime(rs.getLong("start_time"));
                        }
                    }

                    String displayStr = rs.getString("display_entity");
                    if (displayStr != null && !displayStr.isEmpty()) {
                        station.setDisplayEntityId(UUID.fromString(displayStr));
                    }

                    String blockDisplay = rs.getString("block_display");
                    if (blockDisplay != null && !blockDisplay.isEmpty()) {
                        station.setBlockDisplayId(UUID.fromString(blockDisplay));
                    }

                    station.restoreCigarMode(rs.getInt("cigar_leaves_count"));

                    list.add(station);
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to load rolling stations", e);
            }
        }
        return list;
    }

    public void saveCuttingTable(Location loc) {
        async(() -> {
            synchronized (connection) {
                saveSimpleLocation("cutting_tables", loc);
            }
        });
    }

    public void removeCuttingTable(Location loc) {
        async(() -> {
            synchronized (connection) {
                deleteByLocation("cutting_tables", loc);
            }
        });
    }

    public List<Location> loadAllCuttingTables() {
        synchronized (connection) {
            return loadSimpleLocations("cutting_tables");
        }
    }

    public void saveInfuser(Location loc) {
        async(() -> {
            synchronized (connection) {
                saveSimpleLocation("infusers", loc);
            }
        });
    }

    public void removeInfuser(Location loc) {
        async(() -> {
            synchronized (connection) {
                deleteByLocation("infusers", loc);
            }
        });
    }

    public List<Location> loadAllInfusers() {
        synchronized (connection) {
            return loadSimpleLocations("infusers");
        }
    }

    public void saveBlendingTable(Location loc) {
        async(() -> {
            synchronized (connection) {
                saveSimpleLocation("blending_tables", loc);
            }
        });
    }

    public void removeBlendingTable(Location loc) {
        async(() -> {
            synchronized (connection) {
                deleteByLocation("blending_tables", loc);
            }
        });
    }

    public List<Location> loadAllBlendingTables() {
        synchronized (connection) {
            return loadSimpleLocations("blending_tables");
        }
    }

    private void saveSimpleLocation(String table, Location loc) {
        String sql = "INSERT OR REPLACE INTO " + table + " (world, x, y, z) VALUES (?, ?, ?, ?)";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, loc.getWorld().getName());
            ps.setInt(2, loc.getBlockX());
            ps.setInt(3, loc.getBlockY());
            ps.setInt(4, loc.getBlockZ());
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save location in " + table, e);
        }
    }

    private void deleteByLocation(String table, Location loc) {
        String sql = "DELETE FROM " + table + " WHERE world = ? AND x = ? AND y = ? AND z = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, loc.getWorld().getName());
            ps.setInt(2, loc.getBlockX());
            ps.setInt(3, loc.getBlockY());
            ps.setInt(4, loc.getBlockZ());
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to delete location from " + table, e);
        }
    }

    private List<Location> loadSimpleLocations(String table) {
        List<Location> list = new ArrayList<>();
        String sql = "SELECT * FROM " + table;
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                org.bukkit.World world = plugin.getServer().getWorld(rs.getString("world"));
                if (world == null) continue;
                list.add(new Location(world, rs.getInt("x"), rs.getInt("y"), rs.getInt("z")));
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to load locations from " + table, e);
        }
        return list;
    }

    private String serializeLeafEntries(List<DryingStation.LeafEntry> entries) {
        if (entries == null || entries.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (DryingStation.LeafEntry entry : entries) {
            if (!sb.isEmpty()) sb.append(";");
            sb.append(entry.getTobaccoType().name())
                    .append(",")
                    .append(entry.isElite())
                    .append(",")
                    .append(entry.getStartTime());
        }
        return sb.toString();
    }

    private void deserializeLeafEntries(String data, DryingStation station) {
        if (data == null || data.isEmpty()) return;
        String[] parts = data.split(";");
        for (String part : parts) {
            String[] fields = part.split(",");
            if (fields.length != 3) continue;
            try {
                TobaccoType type = TobaccoType.valueOf(fields[0]);
                boolean elite = Boolean.parseBoolean(fields[1]);
                long startTime = Long.parseLong(fields[2]);
                station.getLeafEntries().add(new DryingStation.LeafEntry(type, elite, startTime));
            } catch (Exception ignored) {}
        }
    }

    private String serializeUUIDList(List<UUID> list) {
        if (list == null || list.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (UUID id : list) {
            if (!sb.isEmpty()) sb.append(",");
            sb.append(id.toString());
        }
        return sb.toString();
    }

    private List<UUID> deserializeUUIDList(String data) {
        List<UUID> list = new ArrayList<>();
        if (data == null || data.isEmpty()) return list;
        String[] parts = data.split(",");
        for (String part : parts) {
            try {
                list.add(UUID.fromString(part.trim()));
            } catch (Exception ignored) {}
        }
        return list;
    }

    private double getDoubleSafe(ResultSet rs, String col, double def) {
        try { return rs.getDouble(col); } catch (SQLException e) { return def; }
    }

    private int getIntSafe(ResultSet rs, String col, int def) {
        try { return rs.getInt(col); } catch (SQLException e) { return def; }
    }

    private long getLongSafe(ResultSet rs, String col, long def) {
        try { return rs.getLong(col); } catch (SQLException e) { return def; }
    }

    private String getStringSafe(ResultSet rs, String col, String def) {
        try {
            String v = rs.getString(col);
            return v != null ? v : def;
        } catch (SQLException e) { return def; }
    }

    public void saveAutoDryingRack(AutoDryingRack rack) {
        Location loc = rack.getLocation();
        int fuelSec = rack.getFuelSeconds();
        ItemStack[] itemsCopy = rack.getStoredItems().stream()
                .map(is -> is != null ? is.clone() : null)
                .toArray(ItemStack[]::new);
        List<AutoDryingRack.AutoDryEntry> entriesCopy = new ArrayList<>(rack.getLeafEntries());
        async(() -> saveAutoDryingRackRaw(loc, fuelSec, itemsCopy, entriesCopy));
    }

    public void saveAutoDryingRackSync(AutoDryingRack rack) {
        Location loc = rack.getLocation();
        int fuelSec = rack.getFuelSeconds();
        ItemStack[] itemsCopy = rack.getStoredItems().stream()
                .map(is -> is != null ? is.clone() : null)
                .toArray(ItemStack[]::new);
        List<AutoDryingRack.AutoDryEntry> entriesCopy = new ArrayList<>(rack.getLeafEntries());
        saveAutoDryingRackRaw(loc, fuelSec, itemsCopy, entriesCopy);
    }

    private void saveAutoDryingRackRaw(Location loc, int fuelSec, ItemStack[] itemsCopy,
                                       List<AutoDryingRack.AutoDryEntry> entriesCopy) {
        String sql = """
            INSERT OR REPLACE INTO auto_drying_racks
            (world, x, y, z, fuel_ticks, items, leaf_entries)
            VALUES (?, ?, ?, ?, ?, ?, ?)
        """;
        synchronized (connection) {
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setString(1, loc.getWorld().getName());
                ps.setInt(2, loc.getBlockX());
                ps.setInt(3, loc.getBlockY());
                ps.setInt(4, loc.getBlockZ());
                ps.setInt(5, fuelSec);
                ps.setString(6, ItemSerializer.serializeArray(itemsCopy));

                StringBuilder entriesJson = new StringBuilder("[");
                for (int i = 0; i < entriesCopy.size(); i++) {
                    var entry = entriesCopy.get(i);
                    if (i > 0) entriesJson.append(",");
                    entriesJson.append("{\"type\":\"").append(entry.getTobaccoType().name())
                            .append("\",\"elite\":").append(entry.isElite())
                            .append(",\"time\":").append(entry.getStartTime()).append("}");
                }
                entriesJson.append("]");
                ps.setString(7, entriesJson.toString());
                ps.executeUpdate();
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to save auto drying rack", e);
            }
        }
    }

    public List<AutoDryingRack> loadAllAutoDryingRacks() {
        List<AutoDryingRack> list = new ArrayList<>();
        String sql = "SELECT * FROM auto_drying_racks";
        synchronized (connection) {
            try (Statement stmt = connection.createStatement();
                 ResultSet rs = stmt.executeQuery(sql)) {
                while (rs.next()) {
                    org.bukkit.World world = plugin.getServer().getWorld(rs.getString("world"));
                    if (world == null) continue;
                    Location loc = new Location(world, rs.getInt("x"), rs.getInt("y"), rs.getInt("z"));
                    AutoDryingRack rack = new AutoDryingRack(loc);
                    rack.setFuelSeconds(rs.getInt("fuel_ticks"));

                    String itemsData = rs.getString("items");
                    if (itemsData != null && !itemsData.isEmpty()) {
                        ItemStack[] items = ItemSerializer.deserializeArray(itemsData);
                        for (int i = 0; i < Math.min(items.length, 5); i++) {
                            if (items[i] != null) {
                                rack.getStoredItems().set(i, items[i]);
                            }
                        }
                    }

                    String entriesJson = rs.getString("leaf_entries");
                    if (entriesJson != null && !entriesJson.isEmpty() && !entriesJson.equals("[]")) {
                        String cleaned = entriesJson.trim();
                        if (cleaned.startsWith("[")) cleaned = cleaned.substring(1);
                        if (cleaned.endsWith("]")) cleaned = cleaned.substring(0, cleaned.length() - 1);

                        String[] objects = cleaned.split("\\},\\{");
                        for (String obj : objects) {
                            String data = obj.replaceAll("[\\{\\}\"]", "");
                            String[] fields = data.split(",");

                            String type = null;
                            boolean elite = false;
                            long time = 0;

                            for (String field : fields) {
                                String[] kv = field.split(":", 2);
                                if (kv.length == 2) {
                                    String key = kv[0].trim();
                                    String val = kv[1].trim();
                                    if (key.equalsIgnoreCase("type")) {
                                        type = val;
                                    } else if (key.equalsIgnoreCase("elite")) {
                                        elite = Boolean.parseBoolean(val);
                                    } else if (key.equalsIgnoreCase("time")) {
                                        time = Long.parseLong(val);
                                    }
                                }
                            }
                            if (type != null) {
                                rack.getLeafEntries().add(new AutoDryingRack.AutoDryEntry(
                                        TobaccoType.valueOf(type.toUpperCase()), elite, time));
                            }
                        }
                    }

                    list.add(rack);
                }
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to load auto drying racks", e);
            }
        }
        return list;
    }

    public void removeAutoDryingRack(Location loc) {
        async(() -> {
            synchronized (connection) {
                deleteByLocation("auto_drying_racks", loc);
            }
        });
    }

    public void saveCuttingMachine(CuttingMachine cutter) {
        async(() -> saveCuttingMachineSync(cutter));
    }

    public void saveCuttingMachineSync(CuttingMachine cutter) {
        String sql = """
            INSERT OR REPLACE INTO cutting_machines
            (world, x, y, z, fuel_ticks, progress, leaf_item, fuel_item, tobacco_type, is_elite, drying_level)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """;
        synchronized (connection) {
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                Location loc = cutter.getLocation();
                ps.setString(1, loc.getWorld().getName());
                ps.setInt(2, loc.getBlockX());
                ps.setInt(3, loc.getBlockY());
                ps.setInt(4, loc.getBlockZ());
                ps.setInt(5, cutter.getFuelSeconds());
                ps.setInt(6, cutter.getProgress());
                ps.setString(7, ItemSerializer.serialize(cutter.getLeafItem()));
                ps.setString(8, ItemSerializer.serialize(cutter.getFuelItem()));
                ps.setString(9, cutter.getTobaccoType() != null ? cutter.getTobaccoType().name() : "");
                ps.setInt(10, cutter.isElite() ? 1 : 0);
                ps.setString(11, cutter.getDryingLevel() != null ? cutter.getDryingLevel().name() : "");
                ps.executeUpdate();
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to save cutting machine", e);
            }
        }
    }

    public List<CuttingMachine> loadAllCuttingMachines() {
        List<CuttingMachine> list = new ArrayList<>();
        String sql = "SELECT * FROM cutting_machines";
        synchronized (connection) {
            try (Statement stmt = connection.createStatement();
                 ResultSet rs = stmt.executeQuery(sql)) {
                while (rs.next()) {
                    org.bukkit.World world = plugin.getServer().getWorld(rs.getString("world"));
                    if (world == null) continue;
                    Location loc = new Location(world, rs.getInt("x"), rs.getInt("y"), rs.getInt("z"));
                    CuttingMachine cutter = new CuttingMachine(loc);
                    cutter.setFuelSeconds(rs.getInt("fuel_ticks"));
                    cutter.progress = rs.getInt("progress");
                    cutter.setLeafItem(ItemSerializer.deserialize(rs.getString("leaf_item")));
                    cutter.setFuelItem(ItemSerializer.deserialize(rs.getString("fuel_item")));

                    String typeStr = rs.getString("tobacco_type");
                    if (typeStr != null && !typeStr.isEmpty()) {
                        cutter.tobaccoType = TobaccoType.valueOf(typeStr);
                    }
                    cutter.elite = rs.getInt("is_elite") == 1;

                    String levelStr = rs.getString("drying_level");
                    if (levelStr != null && !levelStr.isEmpty()) {
                        cutter.dryingLevel = DryingLevel.valueOf(levelStr);
                    }

                    if (cutter.getLeafItem() != null) cutter.hasItem = true;
                    list.add(cutter);
                }
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to load cutting machines", e);
            }
        }
        return list;
    }

    public void removeCuttingMachine(Location loc) {
        async(() -> {
            synchronized (connection) {
                deleteByLocation("cutting_machines", loc);
            }
        });
    }

    public void saveRollingPress(RollingPress press) {
        async(() -> saveRollingPressSync(press));
    }

    public void saveRollingPressSync(RollingPress press) {
        String sql = """
            INSERT OR REPLACE INTO rolling_presses
            (world, x, y, z, fuel_ticks, progress, tobacco_type, tobacco_amount,
             paper_amount, tobacco_item, paper_item, fuel_item,
             operator_mastery, operator_flavor_predisposition)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """;
        synchronized (connection) {
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                Location loc = press.getLocation();
                ps.setString(1, loc.getWorld().getName());
                ps.setInt(2, loc.getBlockX());
                ps.setInt(3, loc.getBlockY());
                ps.setInt(4, loc.getBlockZ());
                ps.setInt(5, press.getFuelSeconds());
                ps.setInt(6, press.getProgress());
                ps.setString(7, press.getTobaccoType() != null ? press.getTobaccoType().name() : "");
                ps.setInt(8, press.getTobaccoAmount());
                ps.setInt(9, press.getPaperAmount());
                ps.setString(10, ItemSerializer.serialize(press.getTobaccoItem()));
                ps.setString(11, ItemSerializer.serialize(press.getPaperItem()));
                ps.setString(12, ItemSerializer.serialize(press.getFuelItem()));
                ps.setDouble(13, press.getOperatorMastery());
                ps.setInt(14, press.getOperatorFlavorPredisposition());
                ps.executeUpdate();
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to save rolling press", e);
            }
        }
    }

    public List<RollingPress> loadAllRollingPresses() {
        List<RollingPress> list = new ArrayList<>();
        String sql = "SELECT * FROM rolling_presses";
        synchronized (connection) {
            try (Statement stmt = connection.createStatement();
                 ResultSet rs = stmt.executeQuery(sql)) {
                while (rs.next()) {
                    org.bukkit.World world = plugin.getServer().getWorld(rs.getString("world"));
                    if (world == null) continue;
                    Location loc = new Location(world, rs.getInt("x"), rs.getInt("y"), rs.getInt("z"));
                    RollingPress press = new RollingPress(loc);
                    press.setFuelSeconds(rs.getInt("fuel_ticks"));
                    press.progress = rs.getInt("progress");

                    String typeStr = rs.getString("tobacco_type");
                    if (typeStr != null && !typeStr.isEmpty()) {
                        press.tobaccoType = CigaretteType.valueOf(typeStr);
                    }
                    press.tobaccoAmount = rs.getInt("tobacco_amount");
                    press.paperAmount = rs.getInt("paper_amount");
                    press.setTobaccoItem(ItemSerializer.deserialize(rs.getString("tobacco_item")));
                    press.setPaperItem(ItemSerializer.deserialize(rs.getString("paper_item")));
                    press.setFuelItem(ItemSerializer.deserialize(rs.getString("fuel_item")));
                    press.setOperatorMastery(rs.getDouble("operator_mastery"));
                    press.setOperatorFlavorPredisposition(
                            rs.getInt("operator_flavor_predisposition"));

                    if (press.getTobaccoItem() != null && press.getPaperItem() != null) press.hasItems = true;
                    list.add(press);
                }
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to load rolling presses", e);
            }
        }
        return list;
    }

    public void removeRollingPress(Location loc) {
        async(() -> {
            synchronized (connection) {
                deleteByLocation("rolling_presses", loc);
            }
        });
    }

    public void savePackingPress(PackingPress press) {
        async(() -> savePackingPressSync(press));
    }

    public void savePackingPressSync(PackingPress press) {
        String sql = """
            INSERT OR REPLACE INTO packing_presses
            (world, x, y, z, progress, cigarette_type, cigarette_count, cardboard_count, cigarette_item, cardboard_item, dye_color, dye_item)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """;
        synchronized (connection) {
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                Location loc = press.getLocation();
                ps.setString(1, loc.getWorld().getName());
                ps.setInt(2, loc.getBlockX());
                ps.setInt(3, loc.getBlockY());
                ps.setInt(4, loc.getBlockZ());
                ps.setInt(5, press.getProgress());
                ps.setString(6, press.getCigaretteType() != null ? press.getCigaretteType().name() : "");
                ps.setInt(7, press.getCigaretteCount());
                ps.setInt(8, press.getCardboardCount());
                ps.setString(9, ItemSerializer.serialize(press.getCigaretteItem()));
                ps.setString(10, ItemSerializer.serialize(press.getCardboardItem()));
                ps.setString(11, press.getDyeColor() != null ? press.getDyeColor().name() : "");
                ps.setString(12, ItemSerializer.serialize(press.getDyeItem()));
                ps.executeUpdate();
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to save packing press", e);
            }
        }
    }

    public List<PackingPress> loadAllPackingPresses() {
        List<PackingPress> list = new ArrayList<>();
        String sql = "SELECT * FROM packing_presses";
        synchronized (connection) {
            try (Statement stmt = connection.createStatement();
                 ResultSet rs = stmt.executeQuery(sql)) {
                while (rs.next()) {
                    org.bukkit.World world = plugin.getServer().getWorld(rs.getString("world"));
                    if (world == null) continue;
                    Location loc = new Location(world, rs.getInt("x"), rs.getInt("y"), rs.getInt("z"));
                    PackingPress press = new PackingPress(loc);
                    press.progress = rs.getInt("progress");

                    String typeStr = rs.getString("cigarette_type");
                    if (typeStr != null && !typeStr.isEmpty()) {
                        press.cigaretteType = CigaretteType.valueOf(typeStr);
                    }
                    press.cigaretteCount = rs.getInt("cigarette_count");
                    press.cardboardCount = rs.getInt("cardboard_count");
                    press.setCigaretteItem(ItemSerializer.deserialize(rs.getString("cigarette_item")));
                    press.setCardboardItem(ItemSerializer.deserialize(rs.getString("cardboard_item")));

                    String dyeColorStr = getStringSafe(rs, "dye_color", "");
                    if (!dyeColorStr.isEmpty()) {
                        try {
                            press.setDyeColor(org.bukkit.DyeColor.valueOf(dyeColorStr));
                        } catch (IllegalArgumentException ignored) {}
                    }
                    press.setDyeItem(ItemSerializer.deserialize(getStringSafe(rs, "dye_item", "")));

                    press.hasItems = press.isReady();
                    list.add(press);
                }
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to load packing presses", e);
            }
        }
        return list;
    }

    public void removePackingPress(Location loc) {
        async(() -> {
            synchronized (connection) {
                deleteByLocation("packing_presses", loc);
            }
        });
    }

    public void savePackingStation(PackingStation station) {
        Location loc = station.getLocation();
        ItemStack[] itemsCopy = station.getLoadedCigarettes().stream()
                .map(is -> is != null ? is.clone() : null)
                .toArray(ItemStack[]::new);
        String dye = station.getDyeColor() != null ? station.getDyeColor().name() : "";
        int cranks = station.getCranksRemaining();
        int hasItems = station.hasItems() ? 1 : 0;
        async(() -> savePackingStationRaw(loc, itemsCopy, dye, cranks, hasItems));
    }

    public void savePackingStationSync(PackingStation station) {
        Location loc = station.getLocation();
        ItemStack[] itemsCopy = station.getLoadedCigarettes().stream()
                .map(is -> is != null ? is.clone() : null)
                .toArray(ItemStack[]::new);
        String dye = station.getDyeColor() != null ? station.getDyeColor().name() : "";
        int cranks = station.getCranksRemaining();
        int hasItems = station.hasItems() ? 1 : 0;
        savePackingStationRaw(loc, itemsCopy, dye, cranks, hasItems);
    }

    private void savePackingStationRaw(Location loc, ItemStack[] itemsCopy, String dye, int cranks, int hasItems) {
        String sql = """
            INSERT OR REPLACE INTO packing_stations_manual
            (world, x, y, z, items_base64, dye_color, cranks, has_items)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
        """;
        synchronized (connection) {
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setString(1, loc.getWorld().getName());
                ps.setInt(2, loc.getBlockX());
                ps.setInt(3, loc.getBlockY());
                ps.setInt(4, loc.getBlockZ());
                ps.setString(5, ItemSerializer.serializeArray(itemsCopy));
                ps.setString(6, dye);
                ps.setInt(7, cranks);
                ps.setInt(8, hasItems);
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to save packing station", e);
            }
        }
    }

    public List<PackingStation> loadAllPackingStations() {
        List<PackingStation> list = new ArrayList<>();
        String sql = "SELECT * FROM packing_stations_manual";
        synchronized (connection) {
            try (Statement stmt = connection.createStatement();
                 ResultSet rs = stmt.executeQuery(sql)) {
                while (rs.next()) {
                    org.bukkit.World world = plugin.getServer().getWorld(rs.getString("world"));
                    if (world == null) continue;

                    Location loc = new Location(world, rs.getInt("x"), rs.getInt("y"), rs.getInt("z"));
                    PackingStation station = new PackingStation(loc);

                    String itemsData = rs.getString("items_base64");
                    if (itemsData != null && !itemsData.isEmpty()) {
                        ItemStack[] items = ItemSerializer.deserializeArray(itemsData);
                        List<ItemStack> cigList = new ArrayList<>();
                        for (ItemStack is : items) {
                            if (is != null) cigList.add(is);
                        }
                        station.getLoadedCigarettes().clear();
                        station.getLoadedCigarettes().addAll(cigList);
                    }

                    String dyeStr = rs.getString("dye_color");
                    if (dyeStr != null && !dyeStr.isEmpty()) {
                        try {
                            station.setDyeColor(org.bukkit.DyeColor.valueOf(dyeStr));
                        } catch (IllegalArgumentException ignored) {}
                    }

                    station.setCranksRemaining(rs.getInt("cranks"));
                    station.setHasItems(rs.getInt("has_items") == 1);

                    list.add(station);
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to load packing stations", e);
            }
        }
        return list;
    }

    public void removePackingStation(Location loc) {
        async(() -> {
            synchronized (connection) {
                deleteByLocation("packing_stations_manual", loc);
            }
        });
    }

    public void saveHookahStation(HookahStation station) {
        async(() -> saveHookahStationSync(station));
    }

    public void saveHookahStationSync(HookahStation station) {
        String sql = """
            INSERT OR REPLACE INTO hookah_stations
            (world, x, y, z, hookah_data, display_entity)
            VALUES (?, ?, ?, ?, ?, ?)
        """;
        synchronized (connection) {
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                Location loc = station.getLocation();
                ps.setString(1, loc.getWorld().getName());
                ps.setInt(2, loc.getBlockX());
                ps.setInt(3, loc.getBlockY());
                ps.setInt(4, loc.getBlockZ());
                ps.setString(5, station.getData().serialize());
                ps.setString(6, station.getDisplayEntityId() != null
                        ? station.getDisplayEntityId().toString() : null);
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to save hookah station", e);
            }
        }
    }

    public List<HookahStation> loadAllHookahStations() {
        List<HookahStation> list = new ArrayList<>();
        String sql = "SELECT * FROM hookah_stations";
        synchronized (connection) {
            try (Statement stmt = connection.createStatement();
                 ResultSet rs = stmt.executeQuery(sql)) {
                while (rs.next()) {
                    org.bukkit.World world = plugin.getServer().getWorld(rs.getString("world"));
                    if (world == null) continue;
                    Location loc = new Location(world, rs.getInt("x"), rs.getInt("y"), rs.getInt("z"));
                    HookahData data = HookahData.deserialize(rs.getString("hookah_data"));
                    HookahStation station = new HookahStation(loc, data);

                    String displayStr = rs.getString("display_entity");
                    if (displayStr != null && !displayStr.isEmpty()) {
                        station.setDisplayEntityId(UUID.fromString(displayStr));
                    }

                    list.add(station);
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to load hookah stations", e);
            }
        }
        return list;
    }

    public void removeHookahStation(Location loc) {
        async(() -> {
            synchronized (connection) {
                deleteByLocation("hookah_stations", loc);
            }
        });
    }

    public List<LeaderboardEntry> getTopSmokers(int limit) {
        List<LeaderboardEntry> list = new ArrayList<>();
        String sql = """
            SELECT uuid, total_smoked, addiction_stage
            FROM player_data
            WHERE total_smoked > 0
            ORDER BY total_smoked DESC
            LIMIT ?
        """;

        synchronized (connection) {
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setInt(1, limit);
                ResultSet rs = ps.executeQuery();

                int rank = 1;
                while (rs.next()) {
                    list.add(new LeaderboardEntry(
                            UUID.fromString(rs.getString("uuid")),
                            rs.getInt("total_smoked"),
                            rs.getInt("addiction_stage"),
                            rank++
                    ));
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to load leaderboard", e);
            }
        }
        return list;
    }

    public static class LeaderboardEntry {
        private final UUID uuid;
        private final int totalSmoked;
        private final int addictionStage;
        private final int rank;

        public LeaderboardEntry(UUID uuid, int totalSmoked,
                                int addictionStage, int rank) {
            this.uuid = uuid;
            this.totalSmoked = totalSmoked;
            this.addictionStage = addictionStage;
            this.rank = rank;
        }

        public UUID getUuid() { return uuid; }
        public int getTotalSmoked() { return totalSmoked; }
        public int getAddictionStage() { return addictionStage; }
        public int getRank() { return rank; }
    }

    public void close() {
        if (ioExecutor != null) {
            ioExecutor.shutdown();
            try {
                if (!ioExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                    plugin.getLogger().warning("Database I/O queue did not finish in 10s, forcing shutdown.");
                    ioExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                ioExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
            ioExecutor = null;
        }

        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
                plugin.getLogger().info("Database closed.");
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to close database", e);
        }
    }

    public Connection getConnection() {
        return connection;
    }
}

