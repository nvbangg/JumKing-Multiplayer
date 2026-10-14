package jumpking.identity;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import jumpking.network.ApiException;

public final class AccountCredentials {
    private AccountCredentials() {}
    public record Registration(@NotNull String username, @NotBlank @Email @Size(max = 254) String email, @NotNull String password) {
        public Registration { username = username == null ? null : username.strip(); email = normalizeEmail(email); }
        public void validate() {
            int count = username.codePointCount(0, username.length());
            if (count < 3 || count > 32 || !username.matches("[\\p{L}\\p{N}_-]+")) {
                throw new ApiException(400, "VALIDATION", "Username must contain 3–32 letters, numbers, underscores or hyphens");
            }
            if (password.codePointCount(0, password.length()) < 10 || !passwordWithinBcrypt(password)) {
                throw new ApiException(400, "VALIDATION", "Password must have at least 10 characters and no more than 72 UTF-8 bytes");
            }
        }
    }
    public record Login(@NotBlank @Email @Size(max = 254) String email, @NotNull String password) {
        public Login { email = normalizeEmail(email); }
    }
    public static boolean passwordWithinBcrypt(String password) {
        return password.getBytes(StandardCharsets.UTF_8).length <= 72 && password.codePoints().noneMatch(point -> point >= 0xD800 && point <= 0xDFFF);
    }
    private static String normalizeEmail(String email) { return email == null ? null : email.strip().toLowerCase(Locale.ROOT); }
}
