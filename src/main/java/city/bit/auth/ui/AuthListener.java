package city.bit.auth.ui;

import city.bit.auth.BitCityAuthPlugin;
import city.bit.auth.i18n.MessageBundle;
import city.bit.auth.i18n.Msg;
import city.bit.auth.model.AuthState;
import city.bit.auth.service.AuthService;
import city.bit.auth.service.SessionService;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.*;
import org.bukkit.plugin.Plugin;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Головний слухач подій, пов'язаних з авторизацією.
 * Він контролює кожен крок: від приєднання до сервера до успішного входу.
 */
public class AuthListener implements Listener {

    private final BitCityAuthPlugin plugin;
    private final MessageBundle msgs;
    private final AuthService auth;
    private final SessionService sessions;
    private final GuiFactory gui;
    private final Location lobby;

    // Стан гравця: авторизований/неавторизований тощо
    private final Map<UUID, AuthState> state = new ConcurrentHashMap<>();
    // Обрана мова для кожного гравця
    private final Map<UUID, String> lang = new ConcurrentHashMap<>();
    // Тимчасове збереження введеного пароля (для підтвердження)
    private final Map<UUID, String> tempPass = new ConcurrentHashMap<>();
    // Ідентифікатори завдань, що викидають гравця за бездіяльність
    private final Map<UUID, Integer> idleTask = new ConcurrentHashMap<>();

    public AuthListener(BitCityAuthPlugin plugin, MessageBundle msgs, AuthService auth, SessionService sessions, GuiFactory gui, Location lobby) {
        this.plugin = plugin;
        this.msgs = msgs;
        this.auth = auth;
        this.sessions = sessions;
        this.gui = gui;
        this.lobby = lobby;
    }

    // === Події приєднання та виходу гравця ===

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        // Визначаємо мову за замовчуванням з конфігу
        lang.put(p.getUniqueId(), msgs.getDefaultLang());
        // Телепортуємо гравця у лобі авторизації
        if (lobby.getWorld() != null) p.teleport(lobby);
        // Перевіряємо, чи є активна сесія та збіг IP для автологіну
        String ip = getIp(p);
        var sess = sessions.get(p.getName());
        boolean softIp = "soft".equalsIgnoreCase(plugin.getConfig().getString("auth.session_ip_match","soft"));
        if (sess != null && sess.lastIp != null && ip != null && ip.equals(sess.lastIp)) {
            // Якщо все співпадає — автоматично авторизуємо
            authorize(p);
            return;
        }
        // Якщо ні — блокуємо гравця та відкриваємо GUI
        state.put(p.getUniqueId(), AuthState.UNAUTH);
        openGui(p);
        startIdleKick(p);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        UUID id = e.getPlayer().getUniqueId();
        state.remove(id);
        lang.remove(id);
        tempPass.remove(id);
        cancelIdleKick(e.getPlayer());
    }

    // === Допоміжні методи ===

    private void openGui(Player p) {
        gui.openAuthGui(p, lang.get(p.getUniqueId()));
        // Виводимо невеликий заголовок
        p.sendTitle(" ", msgs.t(Msg.GUI_TITLE, lang.get(p.getUniqueId())), 10, 60, 10);
    }

    // === Блокування дій до авторизації ===

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent e) {
        // Якщо гравець не авторизований — забороняємо рухатися
        if (!isAuthed(e.getPlayer())) {
            if (e.getFrom().distanceSquared(e.getTo()) > 0) e.setTo(e.getFrom());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent e) {
        if (!isAuthed(e.getPlayer())) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageEvent e) {
        if (e.getEntity() instanceof Player p && !isAuthed(p)) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onCmd(PlayerCommandPreprocessEvent e) {
        if (!isAuthed(e.getPlayer())) {
            // Блокуємо всі команди до входу
            e.setCancelled(true);
            e.getPlayer().sendMessage("Please use GUI to login/register.");
        }
    }

    // === Обробка вводу в чаті ===

    @EventHandler
    public void onChat(AsyncPlayerChatEvent e) {
        Player p = e.getPlayer();
        if (isAuthed(p)) return; // якщо вже авторизований, не заважаємо
        e.setCancelled(true); // не показуємо його повідомлення іншим
        AuthState st = state.getOrDefault(p.getUniqueId(), AuthState.UNAUTH);
        switch (st) {
            case AWAITING_INPUT: {
                String awaiting = tempPass.get(p.getUniqueId()); // якщо null -> це перший ввід
                String txt = e.getMessage().trim();
                // Перевірка мінімальної довжини пароля
                if (txt.length() < plugin.getConfig().getInt("auth.password.min_length", 8)) {
                    p.sendMessage(msgs.t(Msg.ERROR_SIMPLE_PASS, lang.get(p.getUniqueId())));
                    return;
                }
                if (awaiting == null) {
                    // Перший ввід пароля
                    tempPass.put(p.getUniqueId(), txt);
                    p.sendMessage(msgs.t(Msg.PROMPT_REPEAT_PASS, lang.get(p.getUniqueId())));
                } else {
                    // Підтвердження
                    if (!awaiting.equals(txt)) {
                        p.sendMessage(msgs.t(Msg.ERROR_PASS_MISMATCH, lang.get(p.getUniqueId())));
                        tempPass.remove(p.getUniqueId());
                        return;
                    }
                    // Якщо користувач ще не зареєстрований — створюємо акаунт
                    boolean registered = auth.isRegistered(p.getName());
                    if (!registered) {
                        if (auth.isBlocked(p.getName())) {
                            p.sendMessage(msgs.t(Msg.ERROR_BLOCKED, lang.get(p.getUniqueId())));
                            return;
                        }
                        String res = auth.register(p.getName(), txt, lang.get(p.getUniqueId()));
                        if ("ok".equals(res)) {
                            p.sendMessage(msgs.t(Msg.SUCCESS_REGISTERED, lang.get(p.getUniqueId())));
                            sessions.createOrRefresh(p.getName(), getIp(p));
                            authorize(p);
                        } else {
                            p.sendMessage(msgs.t(Msg.ERROR_ALREADY_REGISTERED, lang.get(p.getUniqueId())));
                        }
                    } else {
                        // Якщо користувач існує — перевіряємо пароль і авторизуємо
                        if (auth.verify(p.getName(), txt)) {
                            p.sendMessage(msgs.t(Msg.SUCCESS_LOGGED_IN, lang.get(p.getUniqueId())));
                            sessions.createOrRefresh(p.getName(), getIp(p));
                            authorize(p);
                        } else {
                            p.sendMessage(msgs.t(Msg.ERROR_NOT_REGISTERED, lang.get(p.getUniqueId())));
                        }
                    }
                    tempPass.remove(p.getUniqueId());
                }
                break;
            }
            default: {
                // Якщо гравець не у стані вводу — повертаємо йому GUI
                Bukkit.getScheduler().runTask(plugin, () -> openGui(p));
            }
        }
    }

    // === Обробка кліків у інвентарі ===

    @EventHandler
    public void onInvClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (isAuthed(p)) return;
        if (e.getView().getTitle().equalsIgnoreCase(msgs.t(Msg.GUI_TITLE, lang.get(p.getUniqueId())))) {
            e.setCancelled(true); // забороняємо брати предмети
            int slot = e.getRawSlot();
            if (slot == 11) { // Кнопка "Зареєструватись"
                state.put(p.getUniqueId(), AuthState.AWAITING_INPUT);
                p.closeInventory();
                p.sendMessage(msgs.t(Msg.PROMPT_ENTER_PASS, lang.get(p.getUniqueId())));
            } else if (slot == 13) { // "Увійти" (так само, як і реєстрація)
                state.put(p.getUniqueId(), AuthState.AWAITING_INPUT);
                p.closeInventory();
                p.sendMessage(msgs.t(Msg.PROMPT_ENTER_PASS, lang.get(p.getUniqueId())));
            } else if (slot == 15) { // Change pass — поки що не реалізовано
                p.sendMessage("Change password: TODO");
            } else if (slot == 20) { // Recover — поки що не реалізовано
                p.sendMessage("Recover: TODO");
            } else if (slot == 22) { // Перемикач мови
                String L = lang.get(p.getUniqueId());
                lang.put(p.getUniqueId(), "ua".equalsIgnoreCase(L) ? "en" : "ua");
                Bukkit.getScheduler().runTask(plugin, () -> openGui(p));
            } else if (slot == 24) { // Довідка
                p.sendMessage("Use Register/Login buttons. Your chat input is private.");
            }
        }
    }

    @EventHandler
    public void onInvClose(InventoryCloseEvent e) {
        Player p = (Player) e.getPlayer();
        if (!isAuthed(p)) {
            // Якщо гравець закрив меню, одразу відкриваємо його знову
            Bukkit.getScheduler().runTask(plugin, () -> openGui(p));
        }
    }

    // === Допоміжні методи ===

    private boolean isAuthed(Player p) {
        return state.getOrDefault(p.getUniqueId(), AuthState.UNAUTH) == AuthState.AUTHENTICATED;
    }

    private void authorize(Player p) {
        state.put(p.getUniqueId(), AuthState.AUTHENTICATED);
        cancelIdleKick(p); // відміняємо таймер кіка
        p.closeInventory();
        p.sendTitle("§a✔", msgs.t(Msg.SUCCESS_LOGGED_IN, lang.get(p.getUniqueId())), 10, 40, 10);
        // За бажанням можна телепортувати гравця у світ гри
    }

    private void startIdleKick(Player p) {
        cancelIdleKick(p);
        int sec = plugin.getConfig().getInt("ui.idle_kick_seconds", 60);
        int taskId = Bukkit.getScheduler().scheduleSyncDelayedTask(plugin, () -> {
            if (!isAuthed(p) && p.isOnline()) {
                p.kickPlayer("Auth timeout");
            }
        }, sec * 20L);
        idleTask.put(p.getUniqueId(), taskId);
    }

    private void cancelIdleKick(Player p) {
        Integer id = idleTask.remove(p.getUniqueId());
        if (id != null) Bukkit.getScheduler().cancelTask(id);
    }

    private String getIp(Player p) {
        try {
            return Objects.requireNonNull(p.getAddress()).getAddress().getHostAddress();
        } catch (Exception ex) {
            return null;
        }
    }
}
