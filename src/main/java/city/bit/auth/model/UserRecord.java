package city.bit.auth.model;

/**
 * Запис користувача, який зберігається у файлі users.yml.
 * Тут зберігається мінімальна інформація про акаунт без використання БД.
 */
public class UserRecord {
    // Нікнейм гравця
    public String nickname;
    // Хеш пароля у форматі algo:iterations:salt:hashBase64
    public String passHash;
    // Назва алгоритму, наприклад "pbkdf2"
    public String algo;
    // Електронна пошта (необов'язкове поле)
    public String email;
    // Обрана мова інтерфейсу, "ua" або "en"
    public String lang;
    // Час створення акаунта в мілісекундах
    public long createdAt;
    // Час останнього входу
    public long lastLoginAt;
    // Чи заблокований акаунт адміністратором
    public boolean blocked;

    public UserRecord() {}

    public UserRecord(String nickname, String passHash, String algo, String lang) {
        this.nickname = nickname;
        this.passHash = passHash;
        this.algo = algo;
        this.lang = lang;
        this.createdAt = System.currentTimeMillis();
        this.blocked = false;
    }
}
