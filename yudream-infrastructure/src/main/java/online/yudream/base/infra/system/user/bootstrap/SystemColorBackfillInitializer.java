package online.yudream.base.infra.system.user.bootstrap;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import online.yudream.base.domain.system.user.aggregate.Dept;
import online.yudream.base.domain.system.user.aggregate.Role;
import online.yudream.base.domain.system.user.repo.DeptRepo;
import online.yudream.base.domain.system.user.repo.RoleRepo;
import online.yudream.base.domain.system.user.valobj.DeptColorDefaults;
import online.yudream.base.domain.system.user.valobj.RoleColorDefaults;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 角色代表色与部门主色的启动回填。
 * <p>
 * 仅补空值：系统角色按 code 语义、部门按名称语义取默认色，已有值一律不覆盖，可重复执行。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class SystemColorBackfillInitializer implements ApplicationListener<ApplicationReadyEvent> {

    private final RoleRepo roleRepo;
    private final DeptRepo deptRepo;

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        try {
            int backfilledRoles = backfillRoleColors();
            int backfilledDepts = backfillDeptColors();
            log.info("System color backfill completed: roles={}, depts={}", backfilledRoles, backfilledDepts);
        }
        catch (RuntimeException e) {
            // 回填失败不阻断启动：颜色未设置的记录保持原样，下次启动再补
            log.warn("System color backfill failed: {}", e.getMessage(), e);
        }
    }

    private int backfillRoleColors() {
        int filled = 0;
        for (Role role : roleRepo.findAll()) {
            if (!role.isSystemRole()) {
                continue;
            }
            if (role.fillColorIfAbsent(RoleColorDefaults.forCode(role.getCode()))) {
                roleRepo.save(role);
                filled++;
            }
        }
        return filled;
    }

    private int backfillDeptColors() {
        int filled = 0;
        for (Dept dept : deptRepo.findAll()) {
            if (dept.fillColorIfAbsent(DeptColorDefaults.forName(dept.getName()))) {
                deptRepo.save(dept);
                filled++;
            }
        }
        return filled;
    }
}
