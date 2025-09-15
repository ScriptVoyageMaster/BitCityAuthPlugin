package city.bit.auth.ui;

import city.bit.auth.i18n.MessageBundle;
import city.bit.auth.i18n.Msg;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * Відповідає за створення графічного інтерфейсу авторизації.
 * У Spigot GUI реалізований як інвентар з предметами-кнопками.
 */
public class GuiFactory {

    private final MessageBundle msgs;

    public GuiFactory(MessageBundle msgs) {
        this.msgs = msgs;
    }

    /**
     * Відкриває головне меню авторизації для гравця.
     * Інвентар має розмір 3x9 клітинок. У другому рядку розташовані
     * кнопки реєстрації/входу/зміни пароля, у третьому — відновлення,
     * вибір мови та довідка.
     */
    public Inventory openAuthGui(Player p, String lang) {
        Inventory inv = Bukkit.createInventory(null, 27, msgs.t(Msg.GUI_TITLE, lang));
        // Ряд 2: Register/Login/ChangePass
        inv.setItem(9 + 2, button(Material.LIME_WOOL, msgs.t(Msg.GUI_REGISTER, lang)));
        inv.setItem(9 + 4, button(Material.IRON_DOOR, msgs.t(Msg.GUI_LOGIN, lang)));
        inv.setItem(9 + 6, button(Material.ANVIL, msgs.t(Msg.GUI_CHANGE_PASS, lang)));
        // Ряд 3: Recover/Language/Help
        inv.setItem(18 + 2, button(Material.PAPER, msgs.t(Msg.GUI_RECOVER, lang)));
        inv.setItem(18 + 4, button(Material.BOOK, msgs.t(Msg.GUI_LANG, lang)));
        inv.setItem(18 + 6, button(Material.COMPASS, msgs.t(Msg.GUI_HELP, lang)));
        p.openInventory(inv);
        return inv;
    }

    /**
     * Створює предмет-кнопку з вказаним матеріалом та назвою.
     */
    private ItemStack button(Material mat, String name) {
        ItemStack it = new ItemStack(mat); // створюємо предмет з потрібного матеріалу
        ItemMeta im = it.getItemMeta(); // отримуємо мета-дані, що дозволяють змінювати властивості
        im.setDisplayName(name); // встановлюємо назву, яку побачить гравець
        it.setItemMeta(im); // записуємо змінені мета-дані назад у предмет
        return it; // повертаємо готову «кнопку» для розміщення в інвентарі
    }
}
