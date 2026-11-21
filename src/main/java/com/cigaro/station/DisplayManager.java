package com.cigaro.station;

import com.cigaro.Cigaro;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

public class DisplayManager {

    private final Cigaro plugin;
    private final Random random = new Random();

    private static final float[][] LEAF_POSITIONS = {
            { 0.0f,  0.0f},
            {-0.2f, -0.2f},
            { 0.2f, -0.2f},
            {-0.2f,  0.2f},
            { 0.2f,  0.2f},
            { 0.0f, -0.25f},
            { 0.0f,  0.25f},
            {-0.25f, 0.0f},
            { 0.25f, 0.0f},
            {-0.1f, -0.1f},
            { 0.1f,  0.1f},
            {-0.15f, 0.15f},
            { 0.15f,-0.15f},
            { 0.0f, -0.1f},
            {-0.1f,  0.0f},
            { 0.1f,  0.0f},
    };

    public DisplayManager(Cigaro plugin) {
        this.plugin = plugin;
    }

    public UUID spawnDisplay(Location blockLocation, ItemStack item) {
        Location spawnLoc = blockLocation.clone().add(0.5, 1.05, 0.5);

        ItemDisplay display = blockLocation.getWorld().spawn(
                spawnLoc, ItemDisplay.class, entity -> {
                    entity.setItemStack(item);
                    entity.setPersistent(true);

                    Transformation transform = new Transformation(
                            new Vector3f(0, 0, 0),
                            new AxisAngle4f(
                                    (float) Math.toRadians(90), 1, 0, 0
                            ),
                            new Vector3f(0.5f, 0.5f, 0.5f),
                            new AxisAngle4f(0, 0, 0, 1)
                    );
                    entity.setTransformation(transform);
                    entity.setItemDisplayTransform(
                            ItemDisplay.ItemDisplayTransform.FIXED
                    );
                }
        );

        return display.getUniqueId();
    }

    public UUID spawnBlockDisplay(Location blockLocation, ItemStack modelItem) {
        Location spawnLoc = blockLocation.clone().add(0.5, 0.5, 0.5);

        ItemDisplay display = blockLocation.getWorld().spawn(
                spawnLoc, ItemDisplay.class, entity -> {
                    entity.setItemStack(modelItem);
                    entity.setPersistent(true);
                    entity.setItemDisplayTransform(
                            ItemDisplay.ItemDisplayTransform.FIXED
                    );
                    Transformation transform = new Transformation(
                            new Vector3f(0, 0, 0),
                            new AxisAngle4f(0, 0, 0, 1),
                            new Vector3f(1.0f, 1.0f, 1.0f),
                            new AxisAngle4f(0, 0, 0, 1)
                    );
                    entity.setTransformation(transform);
                }
        );

        return display.getUniqueId();
    }

    public List<UUID> spawnLeafDisplays(
            Location blockLocation, int count, ItemStack leafItem
    ) {
        List<UUID> ids = new ArrayList<>();
        int toSpawn = Math.min(count, LEAF_POSITIONS.length);

        for (int i = 0; i < toSpawn; i++) {
            float offsetX = LEAF_POSITIONS[i][0];
            float offsetZ = LEAF_POSITIONS[i][1];
            float offsetY = 1.02f + (i * 0.008f);
            float rotation = random.nextFloat() * 360f;

            Location spawnLoc = blockLocation.clone().add(
                    0.5 + offsetX, offsetY, 0.5 + offsetZ
            );

            ItemDisplay display = blockLocation.getWorld().spawn(
                    spawnLoc, ItemDisplay.class, entity -> {
                        entity.setItemStack(leafItem);
                        entity.setPersistent(true);
                        Transformation transform = new Transformation(
                                new Vector3f(0, 0, 0),
                                new AxisAngle4f(
                                        (float) Math.toRadians(90), 1, 0, 0
                                ),
                                new Vector3f(0.25f, 0.25f, 0.25f),
                                new AxisAngle4f(
                                        (float) Math.toRadians(rotation), 0, 1, 0
                                )
                        );
                        entity.setTransformation(transform);
                        entity.setItemDisplayTransform(
                                ItemDisplay.ItemDisplayTransform.FIXED
                        );
                    }
            );

            ids.add(display.getUniqueId());
        }

        return ids;
    }

    public void updateItemDisplay(UUID entityId, ItemStack newItem) {
        if (entityId == null) return;
        Entity entity = plugin.getServer().getEntity(entityId);
        if (entity instanceof ItemDisplay display) {
            display.setItemStack(newItem);
        }
    }

    public void removeDisplay(UUID entityId) {
        if (entityId == null) return;
        Entity entity = plugin.getServer().getEntity(entityId);
        if (entity != null) entity.remove();
    }

    public void removeDisplays(List<UUID> entityIds) {
        if (entityIds == null) return;
        for (UUID id : entityIds) {
            removeDisplay(id);
        }
    }
}