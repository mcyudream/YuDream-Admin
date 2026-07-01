package online.yudream.base.infra.system.user.bootstrap;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import online.yudream.base.domain.system.user.aggregate.Dept;
import online.yudream.base.domain.system.user.aggregate.Role;
import online.yudream.base.domain.system.user.enumerate.SystemDeptType;
import online.yudream.base.domain.system.user.enumerate.SystemRoleType;
import online.yudream.base.domain.system.user.repo.DeptRepo;
import online.yudream.base.domain.system.user.repo.RoleRepo;
import online.yudream.base.domain.system.user.valobj.DeptID;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 系统基础数据初始化器。
 * <p>
 * 应用启动时自动初始化系统部门（系统虚拟部门、系统管理部门、根部门）
 * 和系统角色（超级管理员、管理员、普通用户、访客）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Order(Ordered.HIGHEST_PRECEDENCE)
public class SystemDataInitializer implements ApplicationListener<ApplicationReadyEvent> {

    private static final Long SYSTEM_DEPT_ID = 2L;
    private static final Long SYSTEM_ADMIN_DEPT_ID = 3L;
    private static final Long ROOT_DEPT_ID = 1L;

    private final DeptRepo deptRepo;
    private final RoleRepo roleRepo;

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        log.info("Initializing system data...");

        Dept systemDept = initDept(SYSTEM_DEPT_ID, "系统", null, SystemDeptType.SYSTEM);
        Dept systemAdminDept = initDept(SYSTEM_ADMIN_DEPT_ID, "系统管理", DeptID.of(systemDept.getId()), SystemDeptType.SYSTEM_ADMIN);
        Dept rootDept = initRootDept(systemDept);

        initRole(SystemRoleType.SUPER_ADMIN, systemAdminDept);
        initRole(SystemRoleType.ADMIN, systemAdminDept);
        initRole(SystemRoleType.USER, rootDept);
        initRole(SystemRoleType.GUEST, rootDept);

        log.info("System data initialized. systemDeptId={}, systemAdminDeptId={}, rootDeptId={}",
                systemDept.getId(), systemAdminDept.getId(), rootDept.getId());
    }

    private Dept initRootDept(Dept systemDept) {
        return deptRepo.findById(ROOT_DEPT_ID)
                .map(existing -> {
                    if (existing.getParentId() == null && systemDept != null) {
                        existing.setParentId(DeptID.of(systemDept.getId()));
                        Dept updated = deptRepo.save(existing);
                        log.info("Updated root dept parent: id={}, parentId={}", updated.getId(), updated.getParentId());
                        return updated;
                    }
                    log.debug("Root dept already exists: id={}", existing.getId());
                    return existing;
                })
                .orElseGet(() -> {
                    Dept root = Dept.create(ROOT_DEPT_ID, "根部门",
                            systemDept == null ? null : DeptID.of(systemDept.getId()),
                            SystemDeptType.ROOT);
                    Dept saved = deptRepo.save(root);
                    log.info("Created root dept: id={}", saved.getId());
                    return saved;
                });
    }

    private Dept initDept(Long id, String name, DeptID parentId, SystemDeptType type) {
        return deptRepo.findByType(type)
                .map(existing -> {
                    log.debug("Dept already exists: type={}, id={}", type, existing.getId());
                    return existing;
                })
                .orElseGet(() -> {
                    Dept dept = Dept.create(id, name, parentId, type);
                    Dept saved = deptRepo.save(dept);
                    log.info("Created dept: type={}, id={}, name={}", type, saved.getId(), name);
                    return saved;
                });
    }

    private void initRole(SystemRoleType type, Dept dept) {
        roleRepo.findBySystemType(type).ifPresentOrElse(
                existing -> log.debug("Role already exists: type={}, id={}", type, existing.getId()),
                () -> {
                    Role role = Role.createSystemRole(type, DeptID.of(dept.getId()));
                    Role saved = roleRepo.save(role);
                    log.info("Created role: type={}, id={}, name={}", type, saved.getId(), saved.getName());
                }
        );
    }
}
