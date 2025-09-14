package city.bit.auth.sec;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Клас, що відповідає за хешування та перевірку паролів.
 * Використовується алгоритм PBKDF2WithHmacSHA256 із "pepper" та випадковою "sаlt".
 * Формат збереження: algo:iterations:salt:hashBase64
 */
public class PasswordHasher {
    // Довжина випадкової "солі" у байтах
    private static final int SALT_LEN = 16;
    // Кількість ітерацій алгоритму PBKDF2
    private static final int ITER = 120_000;
    // Довжина отриманого ключа (у бітах)
    private static final int KEY_LEN = 256;

    private final SecureRandom rng = new SecureRandom();
    private final String pepper; // Додатковий секрет, зчитаний із конфігурації
    private final String algo;   // Назва алгоритму (поки що завжди "pbkdf2")

    public PasswordHasher(String pepper, String algo) {
        this.pepper = pepper == null ? "" : pepper;
        // У цій реалізації ми ігноруємо параметр algo та завжди використовуємо PBKDF2
        this.algo = "pbkdf2";
    }

    /**
     * Створює хеш для заданого пароля. Результат містить усі необхідні дані
     * для подальшої перевірки (алгоритм, ітерації, сіль).
     */
    public String hash(String password) {
        byte[] salt = new byte[SALT_LEN];
        rng.nextBytes(salt); // генеруємо випадкову сіль
        byte[] dk = pbkdf2(password, salt, ITER, KEY_LEN);
        return algo + ":" + ITER + ":" + b64(salt) + ":" + b64(dk);
    }

    /**
     * Перевіряє, чи відповідає пароль збереженому хешу.
     * Повертає true, якщо все добре, і false у разі невідповідності або помилки.
     */
    public boolean verify(String password, String stored) {
        try {
            String[] parts = stored.split(":");
            if (parts.length != 4) return false; // неправильний формат
            int iter = Integer.parseInt(parts[1]);
            byte[] salt = b64d(parts[2]);
            byte[] expected = b64d(parts[3]);
            // Генеруємо ключ для введеного пароля
            byte[] dk = pbkdf2(password, salt, iter, expected.length * 8);
            if (dk.length != expected.length) return false;
            // Порівнюємо масиви байтів у "постійному" часі, щоб уникнути атак по часу
            int diff = 0;
            for (int i = 0; i < dk.length; i++) diff |= dk[i] ^ expected[i];
            return diff == 0;
        } catch (Exception e) {
            // У разі будь-якої помилки вважаємо перевірку неуспішною
            return false;
        }
    }

    private byte[] pbkdf2(String password, byte[] salt, int iter, int keyLen) {
        try {
            // До пароля додаємо "pepper" для додаткового захисту
            PBEKeySpec spec = new PBEKeySpec((password + pepper).toCharArray(), salt, iter, keyLen);
            SecretKeyFactory skf = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            return skf.generateSecret(spec).getEncoded();
        } catch (Exception e) {
            throw new RuntimeException("PBKDF2 error", e);
        }
    }

    private static String b64(byte[] a) { return Base64.getEncoder().encodeToString(a); }
    private static byte[] b64d(String s) { return Base64.getDecoder().decode(s); }
}
