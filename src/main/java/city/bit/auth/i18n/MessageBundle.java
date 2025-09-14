package city.bit.auth.i18n;

import org.bukkit.ChatColor;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;

/**
 * Клас, що відповідає за завантаження та видачу локалізованих повідомлень.
 * Файли messages_ua.yml та messages_en.yml копіюються у папку плагіна
 * при першому запуску та зчитуються у вигляді YamlConfiguration.
 */
public class MessageBundle {
    private final Plugin plugin;
    // Конфігурації для української та англійської мов
    private YamlConfiguration ua;
    private YamlConfiguration en;
    // Мова, яка використовується за замовчуванням
    private String defaultLang;

    public MessageBundle(Plugin plugin, String defaultLang) {
        this.plugin = plugin;
        this.defaultLang = defaultLang;
        // Завантажуємо yaml-файли з папки плагіна
        ua = YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), "messages_ua.yml"));
        en = YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), "messages_en.yml"));
    }

    /**
     * Повертає переклад рядка за вказаним ключем та мовою.
     * Якщо переклад відсутній, використовується англійська версія
     * або назва ключа у вигляді запасного варіанта.
     */
    public String t(Msg key, String lang) {
        // В enum ключі записані як GUI_TITLE, тому переводимо їх у вигляд "gui.title"
        String path = key.name().toLowerCase().replace('_', '.');
        YamlConfiguration y = "en".equalsIgnoreCase(lang) ? en : ua;
        String s = y.getString(path);
        if (s == null) s = en.getString(path, key.name());
        // Підтримуємо кольорові коди у стилі &6
        return ChatColor.translateAlternateColorCodes('&', s);
    }

    /**
     * Переклад із мовою за замовчуванням.
     */
    public String t(Msg key) { return t(key, defaultLang); }

    public String getDefaultLang() { return defaultLang; }
    public void setDefaultLang(String defaultLang) { this.defaultLang = defaultLang; }
}
