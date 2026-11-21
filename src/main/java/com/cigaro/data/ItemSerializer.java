package com.cigaro.data;

import com.cigaro.Cigaro;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

import java.io.ByteArrayInputStream;
import java.util.Base64;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ItemSerializer {

    private static final String ITEM_PREFIX = "v2:";
    private static final String ARRAY_PREFIX = "v2a:";

    private static Logger logger() {
        Cigaro plugin = Cigaro.getInstance();
        return plugin != null ? plugin.getLogger() : Logger.getLogger(ItemSerializer.class.getName());
    }

    public static String serialize(ItemStack item) {
        if (item == null) return "";
        try {
            return ITEM_PREFIX + Base64.getEncoder()
                    .encodeToString(item.serializeAsBytes());
        } catch (Exception e) {
            logger().log(Level.SEVERE, "Failed to serialize item", e);
            return "";
        }
    }

    public static ItemStack deserialize(String data) {
        if (data == null || data.isEmpty()) return null;

        if (data.startsWith(ITEM_PREFIX)) {
            try {
                byte[] bytes = Base64.getDecoder()
                        .decode(data.substring(ITEM_PREFIX.length()));
                return ItemStack.deserializeBytes(bytes);
            } catch (Exception e) {
                logger().log(Level.SEVERE, "Failed to deserialize item", e);
                return null;
            }
        }

        return deserializeLegacy(data);
    }

    public static String serializeArray(ItemStack[] items) {
        if (items == null) return "";
        try {
            return ARRAY_PREFIX + Base64.getEncoder()
                    .encodeToString(ItemStack.serializeItemsAsBytes(items));
        } catch (Exception e) {
            logger().log(Level.SEVERE, "Failed to serialize item array", e);
            return "";
        }
    }

    public static ItemStack[] deserializeArray(String data) {
        if (data == null || data.isEmpty()) return new ItemStack[0];

        if (data.startsWith(ARRAY_PREFIX)) {
            try {
                byte[] bytes = Base64.getDecoder()
                        .decode(data.substring(ARRAY_PREFIX.length()));
                ItemStack[] items = ItemStack.deserializeItemsFromBytes(bytes);
                for (int i = 0; i < items.length; i++) {
                    if (items[i] != null && items[i].isEmpty()) {
                        items[i] = null;
                    }
                }
                return items;
            } catch (Exception e) {
                logger().log(Level.SEVERE, "Failed to deserialize item array", e);
                return new ItemStack[0];
            }
        }

        return deserializeArrayLegacy(data);
    }

    @SuppressWarnings({"deprecation", "removal"})
    private static ItemStack deserializeLegacy(String data) {
        try {
            ByteArrayInputStream inputStream = new ByteArrayInputStream(
                    Base64.getDecoder().decode(data));
            BukkitObjectInputStream dataInput = new BukkitObjectInputStream(inputStream);
            ItemStack item = (ItemStack) dataInput.readObject();
            dataInput.close();
            return item;
        } catch (Exception e) {
            logger().log(Level.SEVERE, "Failed to deserialize legacy item", e);
            return null;
        }
    }

    @SuppressWarnings({"deprecation", "removal"})
    private static ItemStack[] deserializeArrayLegacy(String data) {
        try {
            ByteArrayInputStream inputStream = new ByteArrayInputStream(
                    Base64.getDecoder().decode(data));
            BukkitObjectInputStream dataInput = new BukkitObjectInputStream(inputStream);
            int length = dataInput.readInt();
            ItemStack[] items = new ItemStack[length];
            for (int i = 0; i < length; i++) {
                items[i] = (ItemStack) dataInput.readObject();
            }
            dataInput.close();
            return items;
        } catch (Exception e) {
            logger().log(Level.SEVERE, "Failed to deserialize legacy item array", e);
            return new ItemStack[0];
        }
    }
}
