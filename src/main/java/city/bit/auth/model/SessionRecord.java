package city.bit.auth.model;

/**
 * Запис сесії користувача, який зберігається у sessions.yml.
 * Сесія дозволяє гравцю входити без повторного введення пароля
 * протягом певного часу, якщо IP не змінюється.
 */
public class SessionRecord {
    public String nickname; // Нік гравця, до якого належить ця сесія
    public String token;    // Випадковий токен (наразі не використовується, але може знадобитись)
    public String lastIp;   // Остання IP-адреса, з якої заходив гравець
    public long createdAt;  // Час створення сесії
    public long expiresAt;  // Час, коли сесія стане недійсною

    public SessionRecord() {}
}
