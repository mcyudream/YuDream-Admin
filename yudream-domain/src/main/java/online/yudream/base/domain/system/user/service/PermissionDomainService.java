package online.yudream.base.domain.system.user.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import online.yudream.base.domain.system.security.PermissionMeta;
import online.yudream.base.domain.system.user.aggregate.Permission;
import online.yudream.base.domain.system.user.repo.PermissionRepo;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 权限领域服务。
 */
@Slf4j
@RequiredArgsConstructor
public class PermissionDomainService {

    private final PermissionRepo permissionRepo;

    /**
     * 同步扫描到的权限到数据库。
     * <p>
     * 已存在则更新并激活；不存在则创建；扫描集合中不存在的数据库权限标记为 DEPRECATED。
     *
     * @param scanned 本次扫描到的权限元数据
     */
    public void syncPermissions(Collection<PermissionMeta> scanned) {
        if (scanned == null || scanned.isEmpty()) {
            log.warn("No permission scanned, skip sync");
            return;
        }

        List<Permission> existingPermissions = permissionRepo.findAll();
        Map<String, Permission> existingMap = existingPermissions.stream()
                .collect(Collectors.toMap(
                        p -> p.getId().getCode(),
                        p -> p
                ));

        int created = 0;
        int updated = 0;
        for (PermissionMeta meta : scanned) {
            Permission existing = existingMap.get(meta.code());
            if (existing == null) {
                Permission permission = Permission.create(meta.code(), meta.name(), meta.module(), meta.desc());
                permissionRepo.save(permission);
                created++;
            } else {
                existing.update(meta.name(), meta.module(), meta.desc());
                existing.activate();
                permissionRepo.save(existing);
                updated++;
            }
        }

        Set<String> scannedCodes = scanned.stream()
                .map(PermissionMeta::code)
                .collect(Collectors.toSet());
        permissionRepo.deprecateByCodesNotIn(scannedCodes);

        long deprecated = existingPermissions.stream()
                .map(p -> p.getId().getCode())
                .filter(code -> !scannedCodes.contains(code))
                .count();
        log.info("Permission sync completed, created={}, updated={}, deprecated={}", created, updated, deprecated);
    }
}
