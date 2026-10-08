package dev.practicebot;

import org.bukkit.ChatColor;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Translations. Lookup order for every key:
 * plugins/PracticeBot/lang/&lt;code&gt;.yml (editable) -> bundled &lt;code&gt;.yml -> bundled en.yml -> the key itself.
 */
public final class Lang {
    public static final List<String> CODES = List.of(
            "en", "cs", "sk", "de", "es", "fr", "it", "pt", "pl", "ru",
            "uk", "nl", "sv", "tr", "ja", "ko", "zh", "hu", "ro", "da");

    private static final Map<String, String> ALIASES = Map.ofEntries(
            Map.entry("cz", "cs"), Map.entry("jp", "ja"), Map.entry("kr", "ko"), Map.entry("ua", "uk"),
            Map.entry("se", "sv"), Map.entry("dk", "da"), Map.entry("br", "pt"), Map.entry("cn", "zh"),
            Map.entry("gb", "en"), Map.entry("us", "en"), Map.entry("sp", "es"), Map.entry("ge", "de"));

    private static YamlConfiguration custom = new YamlConfiguration();
    private static YamlConfiguration bundled = new YamlConfiguration();
    private static YamlConfiguration english = new YamlConfiguration();
    private static String code = "en";

    private Lang() {}

    public static String code() { return code; }

    public static void init(PracticeBotPlugin plugin) {
        for (String c : CODES) {
            File f = new File(plugin.getDataFolder(), "lang/" + c + ".yml");
            if (!f.exists()) plugin.saveResource("lang/" + c + ".yml", false);
        }
        String want = plugin.getConfig().getString("language", "en").toLowerCase(Locale.ROOT).trim().replace('-', '_');
        if (want.contains("_")) want = want.substring(0, want.indexOf('_')); // en_US -> en
        want = ALIASES.getOrDefault(want, want);
        if (!CODES.contains(want)) {
            plugin.getLogger().warning("Unknown language '" + want + "', using English. Available: " + String.join(", ", CODES));
            want = "en";
        }
        code = want;
        english = fromJar(plugin, "en");
        bundled = fromJar(plugin, code);
        File f = new File(plugin.getDataFolder(), "lang/" + code + ".yml");
        custom = f.exists() ? YamlConfiguration.loadConfiguration(f) : new YamlConfiguration();
        plugin.getLogger().info("Language: " + code);
    }

    private static YamlConfiguration fromJar(PracticeBotPlugin plugin, String c) {
        InputStream in = plugin.getResource("lang/" + c + ".yml");
        if (in == null) return new YamlConfiguration();
        return YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
    }

    private static String raw(String key) {
        String s = custom.getString(key);
        if (s == null) s = bundled.getString(key);
        if (s == null) s = english.getString(key);
        return s;
    }

    private static String fill(String s, Object... kv) {
        for (int i = 0; i + 1 < kv.length; i += 2) {
            s = s.replace("{" + kv[i] + "}", String.valueOf(kv[i + 1]));
        }
        return ChatColor.translateAlternateColorCodes('&', s);
    }

    /** Translated, colored text. Placeholders: pass key/value pairs, e.g. t("x", "name", "Bob"). */
    public static String t(String key, Object... kv) {
        String s = raw(key);
        return s == null ? key : fill(s, kv);
    }

    public static List<String> list(String key, Object... kv) {
        List<String> l = custom.getStringList(key);
        if (l.isEmpty()) l = bundled.getStringList(key);
        if (l.isEmpty()) l = english.getStringList(key);
        List<String> out = new ArrayList<>();
        for (String s : l) out.add(fill(s, kv));
        return out;
    }
}
