package com.cigaro.listener;

import com.cigaro.Cigaro;
import com.cigaro.CigaroColors;
import com.cigaro.api.CigaroPuffEvent;
import com.cigaro.config.LanguageManager;
import com.cigaro.data.*;
import com.cigaro.mechanic.PassiveSmokingManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.tag.Tag;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.*;

public class SmokingListener implements Listener {

    private final Cigaro plugin;
    private final Map<UUID, Long> cooldowns = new HashMap<>();
    private final Map<UUID, BukkitTask> smokeTrailTasks = new HashMap<>();
    private final Set<UUID> lightingCigar = new HashSet<>();
    private final Map<UUID, Object> cigarActions = new HashMap<>();
    private final Map<UUID, SmokingSession> activeSessions = new HashMap<>();

    private static final long PUFF_COOLDOWN_FALLBACK = 2500;

    public SmokingListener(Cigaro plugin) {
        this.plugin = plugin;
    }

    private long puffCooldown() {
        long cd = plugin.getConfigManager().getPuffCooldownMs();
        return cd > 0 ? cd : PUFF_COOLDOWN_FALLBACK;
    }

    private LanguageManager lang() {
        return plugin.getLanguageManager();
    }

    private static class SmokingSession {
        final CigaretteType type;
        final CigaretteQuality quality;
        int puffsLeft;
        final int puffsMax;
        final int heldSlot;
        final String specialType;
        final FlavorHarmony harmony;
        boolean inAnimation;
        long lastActivity;

        BlendData blend;
        ItemStack expectedItem;
        TobaccoType sourceTobacco;

        SmokingSession(CigaretteType type, CigaretteQuality quality,
                       int puffsLeft, int puffsMax, int heldSlot, String specialType,
                       FlavorHarmony harmony) {
            this.type = type;
            this.quality = quality;
            this.puffsLeft = puffsLeft;
            this.puffsMax = puffsMax;
            this.heldSlot = heldSlot;
            this.specialType = specialType;
            this.harmony = harmony != null ? harmony : FlavorHarmony.NEUTRAL;
            this.inAnimation = false;
            this.lastActivity = System.currentTimeMillis();
        }
    }

    public boolean isSmoking(UUID uuid) {
        SmokingSession session = activeSessions.get(uuid);
        if (session == null) return false;
        Player player = plugin.getServer().getPlayer(uuid);
        if (player == null || !isSessionValid(player, session)) {
            dropSession(uuid);
            return false;
        }
        return true;
    }

    public boolean isInAnimation(UUID uuid) {
        SmokingSession s = activeSessions.get(uuid);
        return (s != null && s.inAnimation) || lightingCigar.contains(uuid);
    }

    private boolean isWet(Player player) {
        Location loc = player.getLocation();
        if (player.isInWater() || player.getEyeLocation().getBlock().getType().name().contains("WATER")) return true;
        if (loc.getWorld().hasStorm() && loc.getWorld().getHighestBlockYAt(loc) <= loc.getBlockY() + 1) return true;
        return false;
    }

    private boolean isHotMaterial(Material mat) {
        return switch (mat) {
            case LAVA_BUCKET, LAVA, MAGMA_BLOCK, FIRE, SOUL_FIRE,
                    TORCH, WALL_TORCH, SOUL_TORCH, SOUL_WALL_TORCH,
                    LANTERN, SOUL_LANTERN, BLAZE_ROD -> true;
            default -> false;
        };
    }

    private boolean isHotBlock(Block block) {
        if (block == null) return false;
        if (block.getBlockData() instanceof org.bukkit.block.data.Lightable lightable) {
            Material type = block.getType();
            String name = type.name();
            if (type == Material.CAMPFIRE || type == Material.SOUL_CAMPFIRE
                    || type == Material.FURNACE || type == Material.BLAST_FURNACE || type == Material.SMOKER
                    || name.endsWith("_CANDLE") || name.endsWith("_CANDLE_CAKE")
                    || type == Material.CANDLE || type == Material.CANDLE_CAKE) return lightable.isLit();
        }
        return isHotMaterial(block.getType());
    }

    private boolean hasFireSource(Player player, Block clickedBlock) {
        ItemStack offhand = player.getInventory().getItemInOffHand();
        Material offMat = offhand.getType();

        if (offMat == Material.FLINT_AND_STEEL || offMat == Material.FIRE_CHARGE) {
            if (player.getGameMode() != GameMode.CREATIVE) {
                if (offMat == Material.FLINT_AND_STEEL) {
                    Damageable meta = (Damageable) offhand.getItemMeta();
                    meta.setDamage(meta.getDamage() + 1);
                    offhand.setItemMeta(meta);
                    if (meta.getDamage() >= offMat.getMaxDurability()) {
                        player.getInventory().setItemInOffHand(null);
                        player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 1f, 1f);
                    }
                } else {
                    offhand.setAmount(offhand.getAmount() - 1);
                }
            }
            return true;
        }
        if (isHotMaterial(offMat)) return true;
        if (isHotBlock(clickedBlock)) return true;
        return false;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDropWhileSmoking(PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        cancelCigarAction(player.getUniqueId());
        SmokingSession session = activeSessions.get(player.getUniqueId());
        if (session != null) {
            dropSession(player.getUniqueId());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onSwapWhileSmoking(PlayerSwapHandItemsEvent event) {
        Player player = event.getPlayer();
        cancelCigarAction(player.getUniqueId());
        SmokingSession session = activeSessions.get(player.getUniqueId());
        if (session != null) {
            dropSession(player.getUniqueId());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onSlotChangeWhileSmoking(PlayerItemHeldEvent event) {
        Player player = event.getPlayer();
        cancelCigarAction(player.getUniqueId());
        SmokingSession session = activeSessions.get(player.getUniqueId());
        if (session != null) {
            dropSession(player.getUniqueId());
        }
    }

    private void cancelCigarAction(UUID uuid) {
        cigarActions.remove(uuid);
        lightingCigar.remove(uuid);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryMoveWhileSmoking(org.bukkit.event.inventory.InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player player) {
            dropSession(player.getUniqueId());
            cancelCigarAction(player.getUniqueId());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryDragWhileSmoking(org.bukkit.event.inventory.InventoryDragEvent event) {
        if (event.getWhoClicked() instanceof Player player) {
            dropSession(player.getUniqueId());
            cancelCigarAction(player.getUniqueId());
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        handleDisconnect(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onPlayerDeathWhileSmoking(org.bukkit.event.entity.PlayerDeathEvent event) {
        handleDisconnect(event.getEntity().getUniqueId());
    }

    @EventHandler
    public void onSmoke(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack mainItem = player.getInventory().getItemInMainHand();

        if (plugin.getItemManager().isCigarette(mainItem) || plugin.getItemManager().isCigar(mainItem)) {
            if (event.getHand() == EquipmentSlot.OFF_HAND) {
                event.setCancelled(true);
                return;
            }
        }

        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getHand() != EquipmentSlot.HAND) return;

        UUID uuid = player.getUniqueId();
        ItemStack item = mainItem;
        if (lightingCigar.contains(uuid)) { event.setCancelled(true); return; }

        SmokingSession session = activeSessions.get(uuid);
        if (session != null && !isSessionValid(player, session)) {
            dropSession(uuid);
            session = null;
        }

        if (session != null) {
            event.setCancelled(true);
            if (session.inAnimation) return;

            if (isWet(player)) {
                plugin.getItemManager().setExtinguished(item, true);
                player.playSound(player.getLocation(), Sound.BLOCK_FIRE_EXTINGUISH, 0.5f, 1.2f);
                player.sendActionBar(lang().get("smoke_extinguished_water"));
                dropSession(uuid);
                return;
            }

            long now = System.currentTimeMillis();
            Long last = cooldowns.get(uuid);
            if (last != null && (now - last) < puffCooldown()) {
                sendPuffCooldownFeedback(player, item, puffCooldown() - (now - last));
                return;
            }
            cooldowns.put(uuid, now);
            performPuff(player, session);
            return;
        }

        boolean isCigar = plugin.getItemManager().isCigar(item);
        boolean isCigarette = plugin.getItemManager().isCigarette(item);
        if (!isCigar && !isCigarette) return;
        event.setCancelled(true);

        if (item.getAmount() > 1) {
            player.sendMessage(lang().get("smoke_separate_first"));
            return;
        }

        int puffsLeft = plugin.getItemManager().getPuffsLeft(item);
        int puffsMax = plugin.getItemManager().getPuffsMax(item);

        if (isCigarette && puffsLeft <= 0) {
            player.getInventory().setItemInMainHand(null);
            return;
        }

        if (isWet(player)) {
            if (puffsLeft < puffsMax && !plugin.getItemManager().isExtinguished(item)) {
                plugin.getItemManager().setExtinguished(item, true);
                player.playSound(player.getLocation(), Sound.BLOCK_FIRE_EXTINGUISH, 0.5f, 1.2f);
                player.sendActionBar(lang().get("smoke_went_out_wet"));
            } else {
                player.sendActionBar(lang().get("smoke_cant_light_here"));
            }
            return;
        }

        long remainingCooldown = puffCooldown() - (System.currentTimeMillis() - cooldowns.getOrDefault(uuid, 0L));
        if (remainingCooldown > 0) {
            sendPuffCooldownFeedback(player, item, remainingCooldown);
            return;
        }

        boolean needsFire = (puffsLeft == puffsMax) || plugin.getItemManager().isExtinguished(item);
        if (needsFire && !hasFireSource(player, event.getClickedBlock())) {
            player.sendActionBar(lang().get("smoke_need_fire"));
            return;
        }

        if (isCigar) {
            if (lightingCigar.contains(uuid)) return;
            long now = System.currentTimeMillis();
            Long lastCigar = cooldowns.get(uuid);
            if (lastCigar != null && (now - lastCigar) < puffCooldown()) {
                sendPuffCooldownFeedback(player, item, puffCooldown() - (now - lastCigar));
                return;
            }
            CigaretteType type = plugin.getItemManager().getCigaretteType(item);
            CigaretteQuality quality = plugin.getItemManager().getCigaretteQuality(item);
            if (type == null || quality == null) return;
            startCigarLighting(player, item, uuid, type, quality);
            return;
        }

        startCigaretteSession(player, item, puffsLeft, puffsMax);
    }

    private void startCigaretteSession(Player player, ItemStack item, int puffsLeft, int puffsMax) {
        UUID uuid = player.getUniqueId();
        CigaretteType type = plugin.getItemManager().getCigaretteType(item);
        CigaretteQuality quality = plugin.getItemManager().getCigaretteQuality(item);
        if (type == null || quality == null) return;

        int slot = player.getInventory().getHeldItemSlot();
        String specialType = plugin.getItemManager().getSpecialType(item);
        FlavorHarmony harmony = plugin.getItemManager().getFlavorHarmony(item);

        SmokingSession session = new SmokingSession(type, quality, puffsLeft, puffsMax, slot,
                specialType, harmony);

        session.blend = plugin.getItemManager().getBlendData(item);
        session.sourceTobacco = plugin.getItemManager().getProductTobacco(item);

        plugin.getItemManager().setExtinguished(item, false);
        session.expectedItem = item.clone();
        activeSessions.put(uuid, session);

        if (!"smokescreen".equals(specialType)) {
            PlayerData data = plugin.getPlayerDataManager().getData(uuid);
            if (!data.isPredispositionGenerated()) {
                plugin.getPlayerDataManager().generatePredisposition(data);
                player.sendMessage(lang().get("smoke_predisposition_generated"));
            }
        }

        spawnLightingFlame(player);

        cooldowns.put(uuid, System.currentTimeMillis());
        performPuff(player, session);
    }

    private void startCigarLighting(Player player, ItemStack item, UUID uuid,
                                    CigaretteType type, CigaretteQuality quality) {
        lightingCigar.add(uuid);
        Object action = new Object();
        cigarActions.put(uuid, action);
        int heldSlot = player.getInventory().getHeldItemSlot();
        plugin.getItemManager().setExtinguished(item, false);
        ItemStack expected = item.clone();
        player.playSound(player.getLocation(), Sound.ITEM_FLINTANDSTEEL_USE, 0.5f, 0.8f);

        String lightingText = lang().getRaw("smoke_cigar_lighting");
        BlendData blend = plugin.getItemManager().getBlendData(item);
        FlavorHarmony harmony = plugin.getItemManager().getFlavorHarmony(item);
        TobaccoType origin = plugin.getItemManager().getProductTobacco(item);

        spawnEmber(player);
        cooldowns.put(uuid, System.currentTimeMillis());
        player.setCooldown(Material.BLAZE_ROD, (int) (puffCooldown() / 50));

        final int maxTicks = Math.max(5, plugin.getConfigManager().getCigarLightingTicks());

        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (cigarActions.get(uuid) != action || !player.isOnline() || player.isDead()
                        || player.getInventory().getHeldItemSlot() != heldSlot
                        || !expected.equals(player.getInventory().getItem(heldSlot))) {
                    if (cigarActions.get(uuid) == action) cancelCigarAction(uuid);
                    this.cancel(); return;
                }
                if (isWet(player)) {
                    plugin.getItemManager().setExtinguished(player.getInventory().getItem(heldSlot), true);
                    cancelCigarAction(uuid);
                    player.sendActionBar(lang().get("smoke_cigar_water_cut_short"));
                    this.cancel(); return;
                }

                ticks++;

                if (ticks < maxTicks) {
                    if (ticks % 15 == 0) {
                        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_BREATH, 0.2f, 1.5f);
                        Location emberLoc = player.getEyeLocation().add(player.getLocation().getDirection().multiply(0.4)).add(0, -0.15, 0);
                        player.getWorld().spawnParticle(Particle.FLAME, emberLoc, 1, 0.01, 0.01, 0.01, 0.001);
                    }
                    int bars = (ticks * 5) / maxTicks;
                    String progress = "  [" + "■".repeat(bars) + "□".repeat(5 - bars) + "] " + lightingText + "  ";
                    player.sendActionBar(Component.text(progress).color(CigaroColors.EMBER));
                } else {
                    cancelCigarAction(uuid);
                    consumeCigar(player);
                    applyCigarEffects(player, type, quality, blend, harmony, origin);
                    plugin.getAnalyticsManager().recordSmoke("cigar", type);
                    plugin.getServer().getPluginManager().callEvent(new com.cigaro.api.CigaroTobaccoEvent(
                            com.cigaro.api.CigaroTobaccoEvent.Action.CIGAR_SMOKED, player, player.getLocation(), expected));
                    spawnFinalExhale(player);
                    spawnEmber(player);
                    player.playSound(player.getLocation(), Sound.BLOCK_FIRE_EXTINGUISH, 0.4f, 0.8f);
                    startSmokeTrail(player);
                    this.cancel();
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    private void consumeCigar(Player player) {
        if (player.getGameMode() == GameMode.CREATIVE) return;
        ItemStack inHand = player.getInventory().getItemInMainHand();
        if (!plugin.getItemManager().isCigar(inHand)) return;
        int left = inHand.getAmount() - 1;
        if (left <= 0) {
            player.getInventory().setItemInMainHand(null);
        } else {
            inHand.setAmount(left);
            player.getInventory().setItemInMainHand(inHand);
        }
    }

    private void performPuff(Player player, SmokingSession session) {
        UUID uuid = player.getUniqueId();
        if (!isSessionValid(player, session) || player.getInventory().getHeldItemSlot() != session.heldSlot) {
            dropSession(uuid); return;
        }
        session.inAnimation = true;
        session.lastActivity = System.currentTimeMillis();
        spawnEmber(player);
        player.playSound(player.getLocation(), Sound.BLOCK_FIRE_EXTINGUISH, 0.15f, 2.0f);
        player.setCooldown(Material.STICK, (int) (puffCooldown() / 50));
        player.sendActionBar(Component.text(buildPuffBar(session.puffsLeft, session.puffsMax))
                .color(CigaroColors.EMBER).decoration(TextDecoration.ITALIC, false));

        int animationTicks = Math.max(1, plugin.getConfigManager().getCigaretteAnimationTicks());
        new BukkitRunnable() {
            int ticks;
            @Override public void run() {
                if (activeSessions.get(uuid) != session || !player.isOnline() || player.isDead()
                        || player.getInventory().getHeldItemSlot() != session.heldSlot
                        || !isSessionValid(player, session)) {
                    if (activeSessions.get(uuid) == session) dropSession(uuid);
                    cancel(); return;
                }
                if (isWet(player)) {
                    plugin.getItemManager().setExtinguished(player.getInventory().getItem(session.heldSlot), true);
                    dropSession(uuid);
                    player.sendActionBar(lang().get("smoke_extinguished_water"));
                    cancel(); return;
                }
                if (++ticks < animationTicks) return;
                ItemStack snapshot = player.getInventory().getItem(session.heldSlot).clone();
                session.puffsLeft--;
                session.inAnimation = false;
                session.lastActivity = System.currentTimeMillis();
                boolean last = session.puffsLeft <= 0;
                if (last) {
                    player.getInventory().setItem(session.heldSlot, null);
                    activeSessions.remove(uuid);
                } else updateHandItem(player, session);
                plugin.getServer().getPluginManager().callEvent(new CigaroPuffEvent(player, snapshot, Math.max(0, session.puffsLeft)));
                plugin.getFireHazardManager().checkFireRisk(player);
                spawnExhale(player);
                player.playSound(player.getLocation(), Sound.BLOCK_FIRE_EXTINGUISH, 0.2f, 1.2f);
                if (last) completeCigaretteSmoke(player, session);
                plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                    if (player.isOnline()) spawnAshParticle(player);
                }, 40L);
                cancel();
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    private void completeCigaretteSmoke(Player player, SmokingSession session) {
        UUID uuid = player.getUniqueId();
        spawnFinalExhale(player);

        if (!"smokescreen".equals(session.specialType)) {
            applySmokingEffects(player, session);
            PlayerData checkData = plugin.getPlayerDataManager().getData(uuid);
            if (checkData.getPredisposition(session.type) >= 0) {
                plugin.getVisualEffectsManager().applyPostSmokeWarmth(player);
            }
            plugin.getAnalyticsManager().recordSmoke("cigarette", session.type);
        }

        startSmokeTrail(player);
        player.getWorld().dropItemNaturally(player.getLocation(),
                plugin.getItemManager().createCigaretteButt());
    }

    private void sendPuffCooldownFeedback(Player player, ItemStack item, long msLeft) {
        player.setCooldown(item.getType(), (int) Math.max(1, msLeft / 50));
        player.sendActionBar(lang().get("smoke_puff_cooldown",
                Placeholder.unparsed("time", String.format(Locale.ROOT, "%.1f", msLeft / 1000.0))));
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.2f, 1.6f);
    }

    private boolean isSessionValid(Player player, SmokingSession session) {
        long timeoutMs = plugin.getConfigManager().getSessionTimeoutSeconds() * 1000L;
        if (timeoutMs > 0 && System.currentTimeMillis() - session.lastActivity > timeoutMs) return false;
        ItemStack inSlot = player.getInventory().getItem(session.heldSlot);
        return inSlot != null && inSlot.equals(session.expectedItem);
    }

    private void dropSession(UUID uuid) {
        activeSessions.remove(uuid);
    }

    private void spawnAshParticle(Player player) {
        Location hand = player.getEyeLocation().clone();
        Vector dir = hand.getDirection().normalize();
        hand.add(dir.multiply(0.35)).add(0, -0.2, 0);
        player.getWorld().spawnParticle(Particle.ASH, hand, 5, 0.02, 0.05, 0.02, 0.01);
    }

    private void updateHandItem(Player player, SmokingSession session) {
        ItemStack currentItem = player.getInventory().getItem(session.heldSlot);
        if (currentItem != null && plugin.getItemManager().isCigarette(currentItem)) {
            ItemStack updated;
            if ("smokescreen".equals(session.specialType)) {
                updated = plugin.getItemManager().createSmokescreenCigarette(1, session.puffsLeft, session.puffsMax);
            } else if (session.blend != null) {
                updated = plugin.getItemManager().createBlendedCigarette(
                        session.blend, session.quality, 1, session.puffsLeft, session.puffsMax,
                        session.harmony);
            } else {
                updated = plugin.getItemManager().createCigarette(session.type, session.quality,
                        1, session.puffsLeft, session.puffsMax, session.harmony);
            }
            plugin.getItemManager().setProductTobacco(updated, session.sourceTobacco);
            player.getInventory().setItem(session.heldSlot, updated);
            session.expectedItem = updated.clone();
        }
    }

    public void handleDisconnect(UUID uuid) {
        activeSessions.remove(uuid);
        BukkitTask trail = smokeTrailTasks.remove(uuid);
        if (trail != null) trail.cancel();
        cancelCigarAction(uuid);
    }

    @EventHandler
    public void onLightFromPlayer(PlayerInteractEntityEvent event) {
        if (!(event.getRightClicked() instanceof Player target)) return;
        if (event.getHand() != EquipmentSlot.HAND) return;

        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        if (activeSessions.containsKey(uuid) || lightingCigar.contains(uuid)) return;

        ItemStack item = player.getInventory().getItemInMainHand();

        boolean isCig = plugin.getItemManager().isCigarette(item);
        boolean isCigar = plugin.getItemManager().isCigar(item);
        if (!isCig && !isCigar) return;

        int puffsLeft = plugin.getItemManager().getPuffsLeft(item);
        int puffsMax = plugin.getItemManager().getPuffsMax(item);
        if (isCig && puffsLeft <= 0) {
            player.getInventory().setItemInMainHand(null);
            return;
        }

        boolean needsFire = (puffsLeft == puffsMax) || plugin.getItemManager().isExtinguished(item);
        if (!needsFire) return;

        ItemStack targetItem = target.getInventory().getItemInMainHand();
        if (!plugin.getItemManager().isCigarette(targetItem) && !plugin.getItemManager().isCigar(targetItem)) return;

        int targetPuffs = plugin.getItemManager().getPuffsLeft(targetItem);
        int targetMax = plugin.getItemManager().getPuffsMax(targetItem);

        if (targetPuffs < targetMax && !plugin.getItemManager().isExtinguished(targetItem)) {
            event.setCancelled(true);

            if (item.getAmount() > 1) {
                player.sendMessage(lang().get("smoke_separate_first"));
                return;
            }

            if (isWet(player)) {
                player.sendActionBar(lang().get("smoke_too_wet"));
                return;
            }

            long now = System.currentTimeMillis();
            Long last = cooldowns.get(uuid);
            if (last != null && (now - last) < puffCooldown()) {
                sendPuffCooldownFeedback(player, item, puffCooldown() - (now - last));
                return;
            }

            player.sendActionBar(lang().get("smoke_lit_from_player",
                    Placeholder.unparsed("name", target.getName())));
            target.sendActionBar(lang().get("smoke_player_lit_from_you",
                    Placeholder.unparsed("name", player.getName())));

            if (isCigar) {
                CigaretteType type = plugin.getItemManager().getCigaretteType(item);
                CigaretteQuality quality = plugin.getItemManager().getCigaretteQuality(item);
                if (type != null && quality != null) {
                    startCigarLighting(player, item, uuid, type, quality);
                }
            } else {
                startCigaretteSession(player, item, puffsLeft, puffsMax);
            }
        }
    }

    private void applySmokingEffects(Player player, SmokingSession session) {
        UUID uuid = player.getUniqueId();
        PlayerData data = plugin.getPlayerDataManager().getData(uuid);

        int predisposition = session.blend != null
                ? session.blend.getBlendedPredisposition(data)
                : data.getPredisposition(session.type);
        double tolerance = session.blend != null
                ? session.blend.getBlendedTolerance(data)
                : data.getTolerance(session.type);
        double toleranceMultiplier = 1.0 - (tolerance * 0.7);
        double qualityMultiplier = session.quality.getMultiplier();

        long timeSinceSmoke = System.currentTimeMillis() - data.getLastSmokeTime();
        double sessionBonus = 1.0;
        if (timeSinceSmoke > 7200000) sessionBonus = 1.6;
        else if (timeSinceSmoke > 1800000) sessionBonus = 1.3;
        else if (timeSinceSmoke >= 600000 && (session.sourceTobacco == TobaccoType.ANCIENT
                || (session.blend != null && ((session.blend.getPrimaryTobacco() == TobaccoType.ANCIENT && session.blend.getPrimaryRatio() >= 0.3)
                || (session.blend.getSecondaryTobacco() == TobaccoType.ANCIENT && session.blend.getSecondaryRatio() >= 0.3))))) sessionBonus = 1.15;

        if (predisposition <= -2) {
            applyCatastrophicReaction(player, session.quality);
            updatePlayerDataFull(player, data, session.type, session.quality, uuid);
            return;
        }

        if (predisposition == -1) {
            applyBadReaction(player, session.type, session.quality, toleranceMultiplier, sessionBonus);
            updatePlayerDataFull(player, data, session.type, session.quality, uuid);
            return;
        }

        boolean isFavorite = predisposition >= 2;
        double predMultiplier = getPredispositionMultiplier(predisposition);
        double finalMult = predMultiplier * toleranceMultiplier * qualityMultiplier * sessionBonus
                * session.harmony.getEffectMultiplier();

        if (session.blend != null) {
            applyBlendEffects(player, session.blend, finalMult, isFavorite);
        } else {
            applyConfigEffects(player, session.type, finalMult, isFavorite);
        }

        if (sessionBonus > 1.3) {
            if (isFavorite) {
                player.sendMessage(lang().get("smoke_msg_fav_long_break"));
            } else {
                TextColor mc = predisposition >= 1 ? CigaroColors.SUCCESS : CigaroColors.SMOKE;
                player.sendMessage(lang().get("smoke_msg_long_break",
                        TagResolver.resolver("msg_color", Tag.styling(mc))));
            }
        } else {
            if (isFavorite) {
                player.sendMessage(lang().get("smoke_msg_fav"));
            } else if (predisposition >= 1) {
                player.sendMessage(lang().get("smoke_msg_good"));
            } else {
                player.sendMessage(lang().get("smoke_msg_neutral"));
            }
        }
        if (session.quality == CigaretteQuality.VINTAGE || session.quality == CigaretteQuality.GOLDEN) {
            applyDisorientation(player);
        }
        updatePlayerDataFull(player, data, session.type, session.quality, uuid);
    }

    private void applyBlendEffects(Player player, BlendData blend, double durationMult, boolean isFavorite) {
        applyConfigEffects(player, blend.getPrimary(), durationMult * 0.5, isFavorite);
        applyConfigEffects(player, blend.getSecondary(), durationMult * 0.5, isFavorite);
    }

    private void applyCatastrophicReaction(Player player, CigaretteQuality quality) {
        double qMult = quality.getMultiplier();

        List<SmokeEffect> debuffs = plugin.getConfigManager().getCatastrophicSmokeDebuffs();
        for (SmokeEffect effect : debuffs) {
            int ticks = Math.max(20, (int) (effect.getBaseDuration() * 20 * qMult));
            player.addPotionEffect(new PotionEffect(effect.getEffectType(), ticks,
                    effect.getAmplifier(), true, false, false));
        }
        player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 60, 0, true, false, false));

        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_HURT, 0.6f, 0.4f);

        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_HURT, 0.5f, 0.3f);
        }, 15L);

        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;
            plugin.getCoughManager().forceCough(player, 4);
        }, 5L);

        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;
            plugin.getCoughManager().forceCough(player, 4);
        }, 45L);

        player.sendMessage(lang().get("smoke_catastrophic_reject"));

        plugin.getAchievementManager().grant(player, CigaroAchievement.BAD_CHOICE);

        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;
            player.sendMessage(lang().get("smoke_catastrophic_sick"));
        }, 40L);

        for (int i = 0; i < 6; i++) {
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (!player.isOnline()) return;
                player.getWorld().spawnParticle(Particle.DUST,
                        player.getEyeLocation().add(0, 0.2, 0),
                        3, 0.3, 0.2, 0.3, 0,
                        new Particle.DustOptions(Color.fromRGB(50, 120, 50), 0.8f));
            }, i * 10L);
        }
    }

    private void applyBadReaction(Player player, CigaretteType type, CigaretteQuality quality,
                                  double toleranceMult, double sessionBonus) {
        double weakMult = 0.3 * toleranceMult * quality.getMultiplier() * sessionBonus;
        applyConfigEffects(player, type, weakMult, false);

        for (SmokeEffect effect : plugin.getConfigManager().getBadSmokeDebuffs()) {
            int ticks = effect.getBaseDuration() * 20;
            player.addPotionEffect(new PotionEffect(effect.getEffectType(), ticks,
                    effect.getAmplifier(), true, false, false));
        }

        if (Math.random() < 0.4) {
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (!player.isOnline()) return;
                forceCough(player);
            }, 10L);
        }

        player.sendMessage(lang().get("smoke_bad_reaction"));
    }

    private void forceCough(Player player) {
        Vector push = player.getLocation().getDirection().normalize().multiply(0.05);
        push.setY(-0.08);
        player.setVelocity(player.getVelocity().add(push));
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_HURT, 0.4f, 0.5f);

        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_BREATH, 0.3f, 0.4f);
        }, 3L);

        Location eyeLoc = player.getEyeLocation().clone();
        Vector dir = eyeLoc.getDirection().normalize();
        player.getWorld().spawnParticle(Particle.CAMPFIRE_COSY_SMOKE,
                eyeLoc.add(dir.multiply(0.3)), 4, 0.06, 0.04, 0.06, 0.012);
    }

    private void updatePlayerDataFull(Player player, PlayerData data,
                                      CigaretteType type, CigaretteQuality quality, UUID uuid) {
        boolean wasInWithdrawal = !plugin.getWithdrawalTask().isInGracePeriod(data)
                && data.getAddictionStage() > 0;
        long soberSeconds = data.getOnlineSoberSeconds();
        boolean isRelapse = soberSeconds > 7200;

        data.addTolerance(type, plugin.getConfigManager().getTolerancePerSmoke());
        data.incrementTotalSmoked();
        data.incrementSmoked(type);
        data.setLastSmokeTime(System.currentTimeMillis());
        data.damageLungRecovery();

        data.setPreWarningSent(false);

        if (player != null) {
            if (player.getWorld().hasStorm()) {
                data.setRainSmokes(data.getRainSmokes() + 1);
            }
            long teammatesCount = player.getNearbyEntities(8.0, 8.0, 8.0).stream()
                    .filter(e -> e instanceof Player)
                    .count();
            if (teammatesCount >= 2) {
                data.setGroupSmokes(data.getGroupSmokes() + 1);
            }
        }

        if (data.getAddictionStage() == 0 && data.wasAddicted() && data.getTotalSmoked() >= 1) {
            data.setAddictionStage(2);
            plugin.getAchievementManager().grant(player, CigaroAchievement.RELAPSE);

            Player playerRec = plugin.getServer().getPlayer(uuid);
            if (playerRec != null) {
                plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                    if (!playerRec.isOnline()) return;
                    playerRec.sendMessage(Component.empty());
                    playerRec.sendMessage(lang().get("smoke_relapse_rushing"));
                    playerRec.sendMessage(lang().get("smoke_relapse_hooked"));
                    playerRec.sendMessage(Component.empty());
                    playerRec.playSound(playerRec.getLocation(), Sound.ENTITY_WARDEN_HEARTBEAT, 0.8f, 0.8f);
                }, 60L);
            }
        }

        plugin.getAddictionCalculator().onSmoked(data, type, quality, wasInWithdrawal);
        data.addMastery(0.002);

        if (player != null) {
            plugin.getLungDamageManager().updateLungHealth(player, data);
            applyGradualRelief(player, data);
        }

        if (isRelapse && player != null) handleRelapse(player, data, soberSeconds);

        data.resetOnlineSoberSeconds();
        plugin.getPlayerDataManager().save(uuid);

        if (player != null) {
            plugin.getAchievementManager().checkSmokingAchievements(player, data);
            plugin.getAchievementManager().checkPredispositionAchievements(player, data, type);
            plugin.getAchievementManager().checkAddictionAchievements(player, data);
        }
    }

    private void applyGradualRelief(Player player, PlayerData data) {
        int stage = data.getAddictionStage();
        if (stage <= 1) { removeWithdrawalDebuffs(player); return; }

        player.removePotionEffect(PotionEffectType.NAUSEA);
        player.removePotionEffect(PotionEffectType.DARKNESS);

        int phase2Delay = switch (stage) { case 2 -> 0; case 3 -> 300; case 4 -> 600; default -> 0; };
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;
            player.removePotionEffect(PotionEffectType.SLOWNESS);
            player.removePotionEffect(PotionEffectType.HUNGER);
        }, phase2Delay);

        int phase3Delay = switch (stage) { case 2 -> 600; case 3 -> 1200; case 4 -> 2400; default -> 0; };
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;
            player.removePotionEffect(PotionEffectType.WEAKNESS);
            player.removePotionEffect(PotionEffectType.MINING_FATIGUE);
        }, phase3Delay);
    }

    private void handleRelapse(Player player, PlayerData data, long soberSeconds) {
        double soberHours = soberSeconds / 3600.0;
        int stageJump;
        if (soberHours >= 24) stageJump = 2;
        else if (soberHours >= 8) stageJump = 1;
        else stageJump = 0;

        if (stageJump > 0 && data.getAddictionStage() > 0) {
            int newStage = Math.min(4, data.getAddictionStage() + stageJump);
            if (newStage > data.getAddictionStage()) {
                data.setAddictionStage(newStage);
                player.sendMessage(Component.empty());
                player.sendMessage(lang().get("smoke_relapse_relief"));

                plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                    if (!player.isOnline()) return;
                    player.sendMessage(lang().get("smoke_relapse_deeper"));
                }, 60L);
            }
        }
    }

    private void applyConfigEffects(Player player, CigaretteType type,
                                    double durationMult, boolean isFavorite) {
        List<SmokeEffect> effects = plugin.getConfigManager().getSmokeEffects(type);
        for (SmokeEffect effect : effects) {
            int durationTicks;
            int amplifier = effect.getAmplifier();
            if (effect.isSideEffect()) {
                durationTicks = effect.getBaseDuration() * 20;
            } else {
                durationTicks = Math.max(60, (int) (effect.getBaseDuration() * 20 * durationMult));
                if (isFavorite) amplifier += effect.getFavoriteBonusAmplifier();
            }
            player.addPotionEffect(new PotionEffect(effect.getEffectType(),
                    durationTicks, amplifier, true, false, !effect.isSideEffect()));
        }
    }

    private void removeWithdrawalDebuffs(Player player) {
        PotionEffectType[] effects = {
                PotionEffectType.SLOWNESS, PotionEffectType.WEAKNESS,
                PotionEffectType.MINING_FATIGUE, PotionEffectType.NAUSEA,
                PotionEffectType.HUNGER, PotionEffectType.DARKNESS
        };
        for (PotionEffectType e : effects) {
            if (player.hasPotionEffect(e)) player.removePotionEffect(e);
        }
    }

    private double getPredispositionMultiplier(int pred) {
        return switch (pred) { case 2 -> 1.5; case 1 -> 1.2; case 0 -> 1.0; case -1 -> 0.3; default -> 0.0; };
    }

    private void spawnEmber(Player player) {
        Location hand = player.getEyeLocation().clone();
        Vector dir = hand.getDirection().normalize();
        hand.add(dir.multiply(0.35)).add(0, -0.15, 0);
        player.getWorld().spawnParticle(Particle.DUST, hand, 2, 0.01, 0.01, 0.01, 0,
                new Particle.DustOptions(Color.fromRGB(255, 60, 0), 0.5f));
        player.getWorld().spawnParticle(Particle.SMOKE, hand, 1, 0.01, 0.01, 0.01, 0.005);
    }

    private void spawnLightingFlame(Player player) {
        Location hand = player.getEyeLocation().clone();
        Vector dir = hand.getDirection().normalize();
        hand.add(dir.multiply(0.35)).add(0, -0.15, 0);
        player.getWorld().spawnParticle(Particle.FLAME, hand, 3, 0.02, 0.02, 0.02, 0.01);
        player.playSound(player.getLocation(), Sound.ITEM_FLINTANDSTEEL_USE, 0.5f, 1.2f);
    }

    private void spawnExhale(Player player) {
        Location eyeLoc = player.getEyeLocation().clone();
        Vector dir = eyeLoc.getDirection().normalize();
        Location p1 = eyeLoc.clone().add(dir.clone().multiply(0.3));
        player.getWorld().spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, p1, 5, 0.03, 0.02, 0.03, 0.008);

        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;
            Location p2 = player.getEyeLocation().clone().add(dir.clone().multiply(0.6)).add(0, 0.1, 0);
            player.getWorld().spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, p2, 4, 0.05, 0.04, 0.05, 0.006);
        }, 3L);

        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;
            Location p3 = player.getEyeLocation().clone().add(dir.clone().multiply(0.9)).add(0, 0.2, 0);
            player.getWorld().spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, p3, 3, 0.08, 0.06, 0.08, 0.004);
        }, 6L);
    }

    private void spawnFinalExhale(Player player) {
        Vector dir = player.getEyeLocation().getDirection().normalize();
        for (int i = 0; i < 6; i++) {
            final int tick = i;
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (!player.isOnline()) return;
                Location loc = player.getEyeLocation().clone().add(dir.clone().multiply(0.25 + tick * 0.2));
                loc.add(0, tick * 0.07, 0);
                player.getWorld().spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, loc, 7 - tick,
                        0.05 + tick * 0.02, 0.03 + tick * 0.015, 0.05 + tick * 0.02, 0.004);
            }, i * 3L);
        }
    }

    private void startSmokeTrail(Player player) {
        UUID uuid = player.getUniqueId();
        BukkitTask existing = smokeTrailTasks.remove(uuid);
        if (existing != null) existing.cancel();

        final int[] ticksLeft = {600};
        BukkitTask task = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            if (!player.isOnline() || ticksLeft[0] <= 0) {
                BukkitTask t = smokeTrailTasks.remove(uuid);
                if (t != null) t.cancel();
                return;
            }
            if (player.isInWater() || player.getEyeLocation().getBlock().getType().name().contains("WATER")) {
                ticksLeft[0] -= 10;
                return;
            }
            Location head = player.getEyeLocation().add(0, 0.3, 0);
            double ox = (Math.random() - 0.5) * 0.15;
            double oz = (Math.random() - 0.5) * 0.15;
            player.getWorld().spawnParticle(Particle.CAMPFIRE_COSY_SMOKE,
                    head.add(ox, 0, oz), 1, 0.02, 0.05, 0.02, 0.003);
            ticksLeft[0] -= 10;
        }, 0L, 10L);
        smokeTrailTasks.put(uuid, task);
    }

    private String buildPuffBar(int remaining, int max) {
        StringBuilder sb = new StringBuilder("  ");
        for (int i = 0; i < max; i++) sb.append(i < remaining ? "▌" : "░");
        sb.append("  ");
        return sb.toString();
    }

    private void applyDisorientation(Player player) {
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;

            Vector push = new Vector(
                    (Math.random() - 0.5) * 1.2,
                    0.1,
                    (Math.random() - 0.5) * 1.2
            );
            player.setVelocity(player.getVelocity().add(push));

            player.sendMessage(lang().get("smoke_disorientation"));
        }, 10L);
    }

    private void applyCigarEffects(Player player, CigaretteType type, CigaretteQuality quality,
                                   BlendData blend, FlavorHarmony harmony, TobaccoType origin) {
        if (harmony == null) harmony = FlavorHarmony.NEUTRAL;
        UUID uuid = player.getUniqueId();
        PlayerData data = plugin.getPlayerDataManager().getData(uuid);

        data.setPreWarningSent(false);

        if (!data.isPredispositionGenerated()) plugin.getPlayerDataManager().generatePredisposition(data);

        int pred = blend != null ? blend.getBlendedPredisposition(data) : data.getPredisposition(type);
        double tol = blend != null ? blend.getBlendedTolerance(data) : data.getTolerance(type);
        double tolMult = 1.0 - (tol * 0.7);
        double qualMult = quality.getMultiplier();
        double cigarMult = SmokableType.CIGAR.getEffectMultiplier();

        if (pred <= -2) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, 400, 1, true, false, false));
            player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 100, 0, true, false, false));
            player.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 200, 0, true, false, false));
            player.sendMessage(lang().get("smoke_cigar_catastrophic"));
        } else {
            double finalMult = cigarMult * tolMult * qualMult * (pred >= 0 ? 1.0 : 0.3)
                    * harmony.getEffectMultiplier();
            long sinceSmoke = System.currentTimeMillis() - data.getLastSmokeTime();
            boolean ancient = origin == TobaccoType.ANCIENT || (blend != null
                    && (blend.getPrimaryTobacco() == TobaccoType.ANCIENT || blend.getSecondaryTobacco() == TobaccoType.ANCIENT));
            if (ancient && sinceSmoke >= 600000 && sinceSmoke <= 1800000) finalMult *= 1.15;
            boolean isFav = pred >= 2;

            if (blend != null) {
                applyBlendEffects(player, blend, finalMult, isFav);
            } else {
                applyConfigEffects(player, type, finalMult, isFav);
            }

            player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 600, 0, true, false, false));

            String key = isFav ? "smoke_cigar_fav" : pred >= 1 ? "smoke_cigar_good" : "smoke_cigar_neutral";
            player.sendMessage(lang().get(key));
        }
        if (quality == CigaretteQuality.VINTAGE || quality == CigaretteQuality.GOLDEN) {
            applyDisorientation(player);
        }
        data.addTolerance(type, plugin.getConfigManager().getTolerancePerSmoke() * 2);
        data.incrementTotalSmoked();
        data.incrementSmoked(type);
        data.setLastSmokeTime(System.currentTimeMillis());
        data.resetOnlineSoberSeconds();
        data.damageLungRecovery();
        data.addMastery(0.005);

        plugin.getPassiveSmokingManager().notifyNearby(
                player, PassiveSmokingManager.SmokeSource.CIGAR);

        boolean wasInWithdrawal = !plugin.getWithdrawalTask().isInGracePeriod(data)
                && data.getAddictionStage() > 0;

        if (player.getWorld().hasStorm()) {
            data.setRainSmokes(data.getRainSmokes() + 2);
        }
        long teammatesCount = player.getNearbyEntities(8.0, 8.0, 8.0).stream()
                .filter(e -> e instanceof Player)
                .count();
        if (teammatesCount >= 2) {
            data.setGroupSmokes(data.getGroupSmokes() + 2);
        }

        plugin.getAddictionCalculator().onSmoked(data, type, quality, wasInWithdrawal);
        plugin.getLungDamageManager().updateLungHealth(player, data);

        if (pred >= 0) plugin.getVisualEffectsManager().applyPostSmokeWarmth(player);

        plugin.getPlayerDataManager().save(uuid);
        plugin.getAchievementManager().checkSmokingAchievements(player, data);
        plugin.getAchievementManager().checkAddictionAchievements(player, data);
    }
}

