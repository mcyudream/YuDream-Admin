package online.yudream.base.domain.system.user.repo;

import online.yudream.base.domain.system.user.aggregate.Permission;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PermissionRepo {

    Permission save(Permission permission);

    Optional<Permission> findByCode(String code);

    List<Permission> findAll();

    List<Permission> findActive();

    /**
     * 将不在指定 code 集合中、且来源为注解扫描的权限标记为已弃用。
     *
     * @param codes 当前有效的权限编码集合
     */
    void deprecateAnnotationByCodesNotIn(Collection<String> codes);
}
