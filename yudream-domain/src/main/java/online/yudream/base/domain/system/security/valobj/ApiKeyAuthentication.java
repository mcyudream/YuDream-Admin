package online.yudream.base.domain.system.security.valobj;

import java.util.List;

public record ApiKeyAuthentication(Long credentialId, Long userId, List<String> permissions) {

    public boolean hasPermission(String permission) {
        return permissions != null && (permissions.contains("*") || permissions.contains(permission));
    }
}
