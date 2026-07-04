package online.yudream.base.plugin.blessing.infrastructure.service;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

public class SkinPasswordService {

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    public String hash(String rawPassword) {
        return encoder.encode(rawPassword);
    }

    public boolean matches(String rawPassword, String passwordHash) {
        if (rawPassword == null || passwordHash == null || passwordHash.isBlank()) {
            return false;
        }
        String compatibleHash = passwordHash.startsWith("$2y$")
                ? "$2a$" + passwordHash.substring(4)
                : passwordHash;
        try {
            return encoder.matches(rawPassword, compatibleHash);
        } catch (RuntimeException ignored) {
            return false;
        }
    }
}
