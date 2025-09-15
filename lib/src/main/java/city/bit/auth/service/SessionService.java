package city.bit.auth.service;

import city.bit.auth.BitCityAuthPlugin;
import city.bit.auth.model.SessionRecord;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * Сервіс для керування сесіями користувачів.
 * Зберігає інформацію про останній вхід у sessions.yml
 * та дозволяє автоматично авторизувати гравця за збігом IP.
 */
public class SessionService {

    private final BitCityAuthPlugin plugin;
    // Мапа активних сесій: нік -> сесія
    private final Map<String, SessionRecord> sessions = new HashMap<>();
    // Файл для збереження сесій
    private final File file;
    // Термін життя сесії у днях
    private final int ttlDays;

    public SessionService(BitCityAuthPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "sessions.yml");
        this.ttlDays = plugin.getConfig().getInt("auth.session_ttl_days", 14);
        load(); // Після створення одразу завантажуємо існуючі сесії
    }

    /** Повертає сесію користувача або null, якщо вона відсутня чи протермінована. */
    public synchronized SessionRecord get(String nick) {
        SessionRecord s = sessions.get(nick.toLowerCase(Locale.ROOT));
        if (s == null) return null;
        if (System.currentTimeMillis() > s.expiresAt) {
            // Сесія прострочена — видаляємо її
            sessions.remove(nick.toLowerCase(Locale.ROOT));
            saveNow();
            return null;
        }
        return s;
    }

    /** Створює нову сесію або оновлює наявну, встановлюючи нову дату завершення. */
    public synchronized void createOrRefresh(String nick, String ip) {
        String key = nick.toLowerCase(Locale.ROOT);
        SessionRecord s = sessions.get(key);
        if (s == null) {
            s = new SessionRecord();
            s.nickname = nick;
            s.token = UUID.randomUUID().toString();
            s.createdAt = System.currentTimeMillis();
        }
        s.lastIp = ip;
        s.expiresAt = System.currentTimeMillis() + ttlDays * 24L * 3600_000L;
        sessions.put(key, s);
        saveNow();
    }

    /** Повністю анулює сесію користувача. */
    public synchronized void invalidate(String nick) {
        sessions.remove(nick.toLowerCase(Locale.ROOT));
        saveNow();
    }

    /** Невелика допоміжна команда для адміністратора. */
    public synchronized String debugSessions(String nick) {
        SessionRecord s = sessions.get(nick.toLowerCase(Locale.ROOT));
        if (s == null) return "No active session";
        return "nick=" + s.nickname + " lastIp=" + shortIp(s.lastIp) + " expiresAt=" + s.expiresAt;
    }

    /** Повертає IP у скороченому вигляді (наприклад, 192.168.0.*). */
    private String shortIp(String ip) {
        if (ip == null) return "null";
        int i = ip.lastIndexOf('.');
        return i > 0 ? ip.substring(0, i) + ".*" : ip;
    }

    /** Завантажує сесії з файлу sessions.yml. */
    private void load() {
        sessions.clear();
        if (!file.exists()) return;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        if (!y.isConfigurationSection("sessions")) return;
        for (String key : y.getConfigurationSection("sessions").getKeys(false)) {
            String base = "sessions." + key + ".";
            SessionRecord s = new SessionRecord();
            s.nickname = y.getString(base + "nickname", key);
            s.token = y.getString(base + "token", "");
            s.lastIp = y.getString(base + "lastIp", null);
            s.createdAt = y.getLong(base + "createdAt", System.currentTimeMillis());
            s.expiresAt = y.getLong(base + "expiresAt", 0);
            sessions.put(key, s);
        }
    }

    /** Зберігає актуальні сесії у файл sessions.yml. */
    public synchronized void saveNow() {
        YamlConfiguration y = new YamlConfiguration();
        for (Map.Entry<String, SessionRecord> e : sessions.entrySet()) {
            String base = "sessions." + e.getKey() + ".";
            SessionRecord s = e.getValue();
            y.set(base + "nickname", s.nickname);
            y.set(base + "token", s.token);
            y.set(base + "lastIp", s.lastIp);
            y.set(base + "createdAt", s.createdAt);
            y.set(base + "expiresAt", s.expiresAt);
        }
        try {
            y.save(file);
        } catch (IOException ex) {
            plugin.getLogger().warning("Save sessions.yml failed: " + ex);
        }
    }
}
