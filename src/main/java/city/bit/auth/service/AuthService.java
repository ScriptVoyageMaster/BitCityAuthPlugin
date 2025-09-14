package city.bit.auth.service;

import city.bit.auth.BitCityAuthPlugin;
import city.bit.auth.model.UserRecord;
import city.bit.auth.sec.PasswordHasher;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * Сервіс, що відповідає за роботу з користувачами:
 * реєстрацію, перевірку паролів, блокування та збереження даних у файл.
 */
public class AuthService {

    private final BitCityAuthPlugin plugin;
    private final PasswordHasher hasher;

    // Усі користувачі тримаються у пам'яті у вигляді мапи "нік -> запис"
    private final Map<String, UserRecord> users = new HashMap<>();
    // Файл, у який зберігатиметься база користувачів
    private final File file;

    public AuthService(BitCityAuthPlugin plugin, PasswordHasher hasher) {
        this.plugin = plugin;
        this.hasher = hasher;
        this.file = new File(plugin.getDataFolder(), "users.yml");
        load(); // Завантажуємо існуючі дані при старті
    }

    /** Чи зареєстрований користувач із даним ніком? */
    public synchronized boolean isRegistered(String nick) {
        return users.containsKey(nick.toLowerCase(Locale.ROOT));
    }

    /** Перевіряє, чи заблоковано користувача адміністратором. */
    public synchronized boolean isBlocked(String nick) {
        UserRecord u = users.get(nick.toLowerCase(Locale.ROOT));
        return u != null && u.blocked;
    }

    /** Реєстрація нового користувача. Повертає "ok" або "exists". */
    public synchronized String register(String nick, String password, String lang) {
        String key = nick.toLowerCase(Locale.ROOT);
        if (users.containsKey(key)) return "exists";
        String h = hasher.hash(password);
        users.put(key, new UserRecord(nick, h, "pbkdf2", lang));
        saveNow();
        return "ok";
    }

    /** Перевіряє пароль користувача. */
    public synchronized boolean verify(String nick, String password) {
        UserRecord u = users.get(nick.toLowerCase(Locale.ROOT));
        if (u == null) return false;
        if (u.blocked) return false;
        boolean ok = hasher.verify(password, u.passHash);
        if (ok) {
            // Оновлюємо час останнього входу
            u.lastLoginAt = System.currentTimeMillis();
        }
        return ok;
    }

    /** Встановлює прапорець блокування для користувача. */
    public synchronized void setBlocked(String nick, boolean v) {
        UserRecord u = users.get(nick.toLowerCase(Locale.ROOT));
        if (u != null) {
            u.blocked = v;
            saveNow();
        }
    }

    /**
     * Адміністратор може скинути пароль.
     * Якщо користувач не існує — він буде створений із тимчасовим паролем.
     * Метод повертає цей тимчасовий пароль.
     */
    public synchronized String adminResetPassword(String nick) {
        String key = nick.toLowerCase(Locale.ROOT);
        UserRecord u = users.get(key);
        if (u == null) {
            // Автоматично створимо користувача з тимчасовим паролем
            String tmp = genTmp();
            String h = hasher.hash(tmp);
            u = new UserRecord(nick, h, "pbkdf2", plugin.messages().getDefaultLang());
            users.put(key, u);
            saveNow();
            return tmp;
        } else {
            String tmp = genTmp();
            u.passHash = hasher.hash(tmp);
            saveNow();
            return tmp;
        }
    }

    /** Повертає базову інформацію про користувача для команди /bcauth whois. */
    public synchronized String debugWhois(String nick) {
        UserRecord u = users.get(nick.toLowerCase(Locale.ROOT));
        if (u == null) return "No user";
        return "nick=" + u.nickname + " blocked=" + u.blocked + " lastLoginAt=" + u.lastLoginAt + " lang=" + u.lang;
    }

    /** Генерує випадковий тимчасовий пароль. */
    private String genTmp() {
        String chars = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789";
        Random r = new Random();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 10; i++) sb.append(chars.charAt(r.nextInt(chars.length())));
        return sb.toString();
    }

    /** Завантажує користувачів із файлу users.yml. */
    private void load() {
        users.clear();
        if (!file.exists()) return;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        if (!y.isConfigurationSection("users")) return;
        for (String key : y.getConfigurationSection("users").getKeys(false)) {
            String base = "users." + key + ".";
            UserRecord u = new UserRecord();
            u.nickname = y.getString(base + "nickname", key);
            u.passHash = y.getString(base + "passHash", "");
            u.algo = y.getString(base + "algo", "pbkdf2");
            u.email = y.getString(base + "email", null);
            u.lang = y.getString(base + "lang", "ua");
            u.createdAt = y.getLong(base + "createdAt", System.currentTimeMillis());
            u.lastLoginAt = y.getLong(base + "lastLoginAt", 0);
            u.blocked = y.getBoolean(base + "blocked", false);
            users.put(key, u);
        }
    }

    /** Зберігає актуальні дані користувачів у файл users.yml. */
    public synchronized void saveNow() {
        YamlConfiguration y = new YamlConfiguration();
        for (Map.Entry<String, UserRecord> e : users.entrySet()) {
            String base = "users." + e.getKey() + ".";
            UserRecord u = e.getValue();
            y.set(base + "nickname", u.nickname);
            y.set(base + "passHash", u.passHash);
            y.set(base + "algo", u.algo);
            y.set(base + "email", u.email);
            y.set(base + "lang", u.lang);
            y.set(base + "createdAt", u.createdAt);
            y.set(base + "lastLoginAt", u.lastLoginAt);
            y.set(base + "blocked", u.blocked);
        }
        try {
            y.save(file);
        } catch (IOException ex) {
            plugin.getLogger().warning("Save users.yml failed: " + ex);
        }
    }
}
