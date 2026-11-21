package com.cigaro.config;

import com.cigaro.Cigaro;
import com.cigaro.CigaroColors;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.minimessage.tag.standard.StandardTags;
import net.kyori.adventure.text.minimessage.tag.Tag;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class LanguageManager {
    private final Cigaro plugin;
    private final Map<String, JsonElement> messages = new HashMap<>();
    private final Map<String, JsonElement> fallback = new HashMap<>();
    private final MiniMessage miniMessage;

    public LanguageManager(Cigaro plugin) {
        this.plugin = plugin;

        this.miniMessage = MiniMessage.builder()
                .tags(TagResolver.builder()
                        .resolver(StandardTags.defaults())
                        .tag("c_dark", Tag.styling(CigaroColors.DARK))
                        .tag("c_gold", Tag.styling(CigaroColors.GOLD))
                        .tag("c_smoke", Tag.styling(CigaroColors.SMOKE))
                        .tag("c_ember", Tag.styling(CigaroColors.EMBER))
                        .tag("c_ash", Tag.styling(CigaroColors.ASH))
                        .tag("c_success", Tag.styling(CigaroColors.SUCCESS))
                        .tag("c_warning", Tag.styling(CigaroColors.WARNING))
                        .tag("c_error", Tag.styling(CigaroColors.ERROR))
                        .tag("c_info", Tag.styling(CigaroColors.INFO))
                        .build())
                .build();
    }

    public void load() {
        messages.clear();
        fallback.clear();
        File langFolder = new File(plugin.getDataFolder(), "lang");
        if (!langFolder.exists()) {
            langFolder.mkdirs();
        }

        saveDefaultLangFile("en_us.json");
        saveDefaultLangFile("ru_ru.json");

        String langCode = plugin.getConfigManager().getLanguage();
        File langFile = new File(langFolder, langCode + ".json");

        if (!langFile.exists()) {
            plugin.getLogger().warning("Language file " + langCode + ".json not found! Falling back to en_us.json");
            langFile = new File(langFolder, "en_us.json");
        }

        File fallbackFile = new File(langFolder, "en_us.json");
        if (!fallbackFile.equals(langFile)) {
            readInto(fallbackFile, fallback);
        }

        if (readInto(langFile, messages)) {
            plugin.getLogger().info("Loaded language: " + langFile.getName() + " (" + messages.size() + " keys)");
            int missing = 0;
            for (String key : fallback.keySet()) {
                if (!messages.containsKey(key)) missing++;
            }
            if (missing > 0) {
                plugin.getLogger().warning(missing + " key(s) missing from " + langFile.getName()
                        + ", falling back to en_us for those.");
            }
        }
    }

    private boolean readInto(File file, Map<String, JsonElement> target) {
        try (Reader reader = new InputStreamReader(Files.newInputStream(file.toPath()), StandardCharsets.UTF_8)) {
            JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
            for (Map.Entry<String, JsonElement> entry : json.entrySet()) {
                target.put(entry.getKey(), entry.getValue());
            }
            return true;
        } catch (IOException | IllegalStateException e) {
            plugin.getLogger().log(java.util.logging.Level.SEVERE,
                    "Failed to load language file: " + file.getName() + ". Check for JSON syntax errors!", e);
            return false;
        }
    }

    private JsonElement lookup(String key) {
        JsonElement el = messages.get(key);
        return el != null ? el : fallback.get(key);
    }

    private void saveDefaultLangFile(String fileName) {
        File file = new File(plugin.getDataFolder(), "lang/" + fileName);
        if (!file.exists()) {
            plugin.saveResource("lang/" + fileName, false);
        }
    }

    public Component get(String key, TagResolver... placeholders) {
        JsonElement el = lookup(key);
        String text = (el != null && el.isJsonPrimitive()) ? el.getAsString() : "<red>Missing: " + key;
        return miniMessage.deserialize(text, placeholders);
    }

    public Component getWithPrefix(String key, TagResolver... placeholders) {
        JsonElement prefixEl = lookup("prefix");
        JsonElement msgEl = lookup(key);

        String prefix = (prefixEl != null && prefixEl.isJsonPrimitive()) ? prefixEl.getAsString() : "";
        String text = (msgEl != null && msgEl.isJsonPrimitive()) ? msgEl.getAsString() : "<red>Missing: " + key;

        return miniMessage.deserialize(prefix + text, placeholders);
    }

    public List<Component> getLore(String key, TagResolver... placeholders) {
        List<Component> lore = new ArrayList<>();
        JsonElement el = lookup(key);

        if (el != null && el.isJsonArray()) {
            JsonArray arr = el.getAsJsonArray();
            for (JsonElement line : arr) {
                lore.add(miniMessage.deserialize(line.getAsString(), placeholders));
            }
        } else if (el != null && el.isJsonPrimitive()) {
            lore.add(miniMessage.deserialize(el.getAsString(), placeholders));
        } else {
            lore.add(miniMessage.deserialize("<red>Missing lore: " + key));
        }
        return lore;
    }

    public String getRaw(String key) {
        JsonElement el = lookup(key);
        return (el != null && el.isJsonPrimitive()) ? el.getAsString() : key;
    }
}