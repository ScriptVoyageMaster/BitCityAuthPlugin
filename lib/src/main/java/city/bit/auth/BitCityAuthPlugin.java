package city.bit.auth;

import city.bit.auth.config.ConfigKeys;
import city.bit.auth.i18n.MessageBundle;
import city.bit.auth.model.AuthState;
import city.bit.auth.sec.PasswordHasher;
import city.bit.auth.service.AuthService;
import city.bit.auth.service.SessionService;
import city.bit.auth.ui.AuthListener;
import city.bit.auth.ui.GuiFactory;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Головний клас плагіна. Саме він підвантажується Spigot'ом при запуску сервера.
 * Тут ми створюємо всі необхідні сервіси, завантажуємо налаштування та
 * підключаємо слухачів подій.
 */
public class BitCityAuthPlugin extends JavaPlugin {

    // Статична змінна, що дозволяє отримати екземпляр плагіна з будь-якого місця
    private static BitCityAuthPlugin INSTANCE;

    // Сервіси авторизації та роботи з сесіями
    private AuthService authService;
    private SessionService sessionService;

    // Фабрика для створення графічного інтерфейсу (інвентарів)
    private GuiFactory guiFactory;

    // Обгортка для мовних файлів (локалізації)
    private MessageBundle messages;

    // Локація, куди телепортуються гравці під час авторизації
    private Location lobbySpawn;

    /**
     * Зручний метод для доступу до плагіна з інших класів.
     */
    public static BitCityAuthPlugin inst() { return INSTANCE; }

    @Override
    public void onEnable() {
        // Зберігаємо посилання на себе у статичній змінній
        INSTANCE = this;

        // 1) Створюємо/оновлюємо конфіг і мовні файли
        // saveDefaultConfig() записує config.yml у папку плагіна, якщо його ще немає
        saveDefaultConfig();
        // saveResource копіює файли з resources у папку плагіна. Другий параметр=false
        // означає "не перезаписувати, якщо файл вже існує".
        saveResource("messages_ua.yml", false);
        saveResource("messages_en.yml", false);

        // Читаємо налаштування з config.yml
        FileConfiguration cfg = getConfig();

        // 2) Ініціалізуємо локалізацію
        // Користувач може задати мову за замовчуванням у config.yml, якщо ні — беремо "ua"
        String defaultLang = cfg.getString("i18n.default_lang", "ua");
        messages = new MessageBundle(this, defaultLang);

        // 3) Налаштовуємо лобі авторизації
        // Звідси будуть починати всі гравці, поки не увійдуть в акаунт
        String world = cfg.getString("ui.lobby.world", "AuthLobby");
        double x = cfg.getDouble("ui.lobby.spawn.x", 0);
        double y = cfg.getDouble("ui.lobby.spawn.y", 80);
        double z = cfg.getDouble("ui.lobby.spawn.z", 0);
        float yaw = (float) cfg.getDouble("ui.lobby.spawn.yaw", 0);
        float pitch = (float) cfg.getDouble("ui.lobby.spawn.pitch", 0);
        lobbySpawn = new Location(Bukkit.getWorld(world), x, y, z, yaw, pitch);

        // 4) Створюємо сервіси
        // "pepper" — додатковий секретний рядок, який ускладнює підбір пароля
        String pepper = cfg.getString("auth.crypto.pepper", "CHANGE_ME");
        // Алгоритм хешування паролів. Поки що підтримується лише pbkdf2.
        String algo = cfg.getString("auth.crypto.algo", "pbkdf2");
        PasswordHasher hasher = new PasswordHasher(pepper, algo);
        authService = new AuthService(this, hasher);
        sessionService = new SessionService(this);

        // 5) Створюємо фабрику GUI та реєструємо слухача подій авторизації
        guiFactory = new GuiFactory(messages);
        Bukkit.getPluginManager().registerEvents(
                new AuthListener(this, messages, authService, sessionService, guiFactory, lobbySpawn),
                this
        );

        // 6) Реєструємо адміністративну команду /bcauth
        getCommand("bcauth").setExecutor((sender, cmd, label, args) -> {
            // Перевіряємо права користувача
            if (!sender.hasPermission("bca.admin")) {
                sender.sendMessage("No permission");
                return true;
            }
            if (args.length < 1) {
                sender.sendMessage("/bcauth <whois|sessions|reset|block|unblock> <player>");
                return true;
            }
            String sub = args[0].toLowerCase();
            switch (sub) {
                case "whois":
                    if (args.length < 2) { sender.sendMessage("Usage: /bcauth whois <player>"); return true; }
                    sender.sendMessage(authService.debugWhois(args[1]));
                    return true;
                case "sessions":
                    if (args.length < 2) { sender.sendMessage("Usage: /bcauth sessions <player>"); return true; }
                    sender.sendMessage(sessionService.debugSessions(args[1]));
                    return true;
                case "reset":
                    if (args.length < 2) { sender.sendMessage("Usage: /bcauth reset <player>"); return true; }
                    String tmp = authService.adminResetPassword(args[1]);
                    sender.sendMessage("Temporary password for " + args[1] + ": " + tmp);
                    return true;
                case "block":
                    if (args.length < 2) { sender.sendMessage("Usage: /bcauth block <player>"); return true; }
                    authService.setBlocked(args[1], true);
                    sender.sendMessage("Blocked: " + args[1]);
                    return true;
                case "unblock":
                    if (args.length < 2) { sender.sendMessage("Usage: /bcauth unblock <player>"); return true; }
                    authService.setBlocked(args[1], false);
                    sender.sendMessage("Unblocked: " + args[1]);
                    return true;
                default:
                    sender.sendMessage("Unknown subcommand.");
                    return true;
            }
        });

        getLogger().info("BitCityAuth enabled. GUI auth ready.");
    }

    @Override
    public void onDisable() {
        // При зупинці сервера гарантуємо, що всі дані будуть збережені на диск
        if (authService != null) authService.saveNow();
        if (sessionService != null) sessionService.saveNow();
    }

    // Далі йдуть гетери для зручного доступу до сервісів та ресурсів плагіна
    public AuthService auth() { return authService; }
    public SessionService sessions() { return sessionService; }
    public GuiFactory gui() { return guiFactory; }
    public MessageBundle messages() { return messages; }
    public Location lobbySpawn() { return lobbySpawn; }
}
