package online.yudream.base.domain.system.security;

import java.util.Objects;

/**
 * 权限元数据。
 */
public record PermissionMeta(String code, String name, String module, String desc) {
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        return Objects.equals(code, ((PermissionMeta) o).code);
    }

    @Override
    public int hashCode() {
        return Objects.hash(code);
    }
}
