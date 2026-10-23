package online.yudream.base.application.system.template.service;

import lombok.RequiredArgsConstructor;
import online.yudream.base.application.system.menu.cmd.MenuImportCmd;
import online.yudream.base.application.system.menu.dto.MenuImportDTO;
import online.yudream.base.application.system.menu.service.MenuAppService;
import online.yudream.base.application.system.template.cmd.TemplateDeptImportCmd;
import online.yudream.base.application.system.template.cmd.TemplateMenuImportCmd;
import online.yudream.base.application.system.template.cmd.TemplateRoleImportCmd;
import online.yudream.base.application.system.template.cmd.TemplateUserImportCmd;
import online.yudream.base.application.system.template.dto.SystemTemplateImportResultDTO;
import online.yudream.base.application.system.template.dto.SystemTemplateIssueDTO;
import online.yudream.base.application.system.template.dto.TemplateDeptDTO;
import online.yudream.base.application.system.template.dto.TemplateMenuDTO;
import online.yudream.base.application.system.template.dto.TemplateRoleDTO;
import online.yudream.base.application.system.template.dto.TemplateUserDTO;
import online.yudream.base.application.system.user.cmd.TemplateDeptUpsertCmd;
import online.yudream.base.application.system.user.cmd.TemplateRoleUpsertCmd;
import online.yudream.base.application.system.user.cmd.TemplateUserUpsertCmd;
import online.yudream.base.application.system.user.dto.TemplateDeptUpsertResult;
import online.yudream.base.application.system.user.dto.TemplateRoleUpsertResult;
import online.yudream.base.application.system.user.dto.TemplateUserUpsertResult;
import online.yudream.base.application.system.user.service.DeptManageAppService;
import online.yudream.base.application.system.user.service.RoleManageAppService;
import online.yudream.base.application.system.user.service.TemplateUserAppService;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.system.menu.aggregate.Menu;
import online.yudream.base.domain.system.menu.enumerate.MenuSource;
import online.yudream.base.domain.system.menu.repo.MenuRepo;
import online.yudream.base.domain.system.user.aggregate.Dept;
import online.yudream.base.domain.system.user.aggregate.Role;
import online.yudream.base.domain.system.user.aggregate.User;
import online.yudream.base.domain.system.user.enumerate.DeptStatus;
import online.yudream.base.domain.system.user.enumerate.RoleStatus;
import online.yudream.base.domain.system.user.repo.DeptRepo;
import online.yudream.base.domain.system.user.repo.RoleRepo;
import online.yudream.base.domain.system.user.repo.UserRepo;
import online.yudream.base.domain.system.user.valobj.DeptID;
import online.yudream.base.domain.system.user.valobj.RoleID;
import online.yudream.base.domain.system.user.valobj.UserID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 系统资源 Excel 导入导出应用服务。
 * <p>
 * 这是资源页所属的可移植配置导入通道，而不是全库备份恢复：只创建或覆盖四类资源的白名单字段，
 * 不删除目标端独有记录，也不导入密码、令牌、文件、头像、身份凭据或审计数据。
 * 需要全实例无损恢复时使用 YDBA 备份中心。
 */
@Service
@RequiredArgsConstructor
public class SystemTemplateAppService {

    private final DeptRepo deptRepo;
    private final RoleRepo roleRepo;
    private final UserRepo userRepo;
    private final MenuRepo menuRepo;
    private final DeptManageAppService deptManageAppService;
    private final RoleManageAppService roleManageAppService;
    private final TemplateUserAppService templateUserAppService;
    private final MenuAppService menuAppService;

    @Transactional(readOnly = true)
    public List<TemplateDeptDTO> exportDepartments() {
        List<Dept> departments = deptRepo.findAll();
        Map<Long, Dept> departmentMap = departments.stream()
                .filter(item -> item.getId() != null)
                .collect(Collectors.toMap(Dept::getId, Function.identity(), (left, right) -> left, LinkedHashMap::new));
        Map<Long, User> users = userRepo.findByIds(departments.stream()
                        .map(Dept::getLeader)
                        .filter(Objects::nonNull)
                        .map(UserID::getValue)
                        .distinct()
                        .toList())
                .stream().collect(Collectors.toMap(User::getId, Function.identity()));
        Map<Long, String> deptPaths = departmentPaths(departmentMap);

        List<TemplateDeptDTO> deptRows = departments.stream()
                .sorted(Comparator.comparing((Dept dept) -> deptPaths.getOrDefault(dept.getId(), "")))
                .map(dept -> new TemplateDeptDTO(
                        id(dept.getId()),
                        deptPaths.get(dept.getId()),
                        dept.getName(),
                        dept.getParentId() == null ? null : deptPaths.get(dept.getParentId().getValue()),
                        dept.getLeader() == null || users.get(dept.getLeader().getValue()) == null ? null
                                : users.get(dept.getLeader().getValue()).getUsername(),
                        dept.getPhone() == null ? null : dept.getPhone().getValue(),
                        dept.getSortOrder(), dept.getDescription(), dept.getColor(), dept.getDeptType(),
                        dept.isSystem() || dept.isRoot(), dept.getStatus()))
                .toList();

        return deptRows;
    }

    /** 供角色管理页导出可回导的角色模板。 */
    @Transactional(readOnly = true)
    public List<TemplateRoleDTO> exportRoles() {
        Map<Long, String> deptPaths = departmentPaths(deptRepo.findAll().stream()
                .filter(item -> item.getId() != null)
                .collect(Collectors.toMap(Dept::getId, Function.identity(), (left, right) -> left, LinkedHashMap::new)));
        List<Role> roles = roleRepo.findAll();
        return roles.stream()
                .sorted(Comparator.comparing(Role::getCode, Comparator.nullsLast(String::compareTo)))
                .map(role -> new TemplateRoleDTO(
                        id(role.getId()), role.getCode(), role.getName(),
                        role.getDeptId() == null ? null : deptPaths.get(role.getDeptId().getValue()),
                        role.getLevel(), role.getPermissions() == null ? List.of()
                                : role.getPermissions().stream().map(permission -> permission.getCode()).toList(),
                        role.getIcon(), role.getColor(), role.isSystemRole(), role.getStatus()))
                .toList();
    }

    /** 供用户管理页导出可回导的用户模板，永不包含密码或其他凭据。 */
    @Transactional(readOnly = true)
    public List<TemplateUserDTO> exportUsers() {
        Map<Long, String> deptPaths = departmentPaths(deptRepo.findAll().stream()
                .filter(item -> item.getId() != null)
                .collect(Collectors.toMap(Dept::getId, Function.identity(), (left, right) -> left, LinkedHashMap::new)));
        Map<Long, Role> roleMap = roleRepo.findAll().stream().filter(item -> item.getId() != null)
                .collect(Collectors.toMap(Role::getId, Function.identity(), (left, right) -> left));
        return usersForTemplate().stream()
                .sorted(Comparator.comparing(User::getUsername, Comparator.nullsLast(String::compareTo)))
                .map(user -> new TemplateUserDTO(
                        id(user.getId()), user.getUsername(), user.getNickname(),
                        user.getEmail() == null ? null : user.getEmail().getValue(),
                        user.getPhone() == null ? null : user.getPhone().getValue(),
                        user.getQq() == null ? null : user.getQq().getValue(),
                        user.isEmailVerified(), user.getStatus(),
                        user.getDepts() == null ? List.of() : user.getDepts().stream()
                                .map(dept -> deptPaths.get(dept.id().getValue())).filter(Objects::nonNull).toList(),
                        user.getDefaultDeptID() == null ? null : deptPaths.get(user.getDefaultDeptID().getValue()),
                        user.getRoles() == null ? List.of() : user.getRoles().stream()
                                .map(RoleID::getValue).map(roleMap::get).filter(Objects::nonNull).map(Role::getCode).toList()))
                .toList();
    }

    /** 供菜单管理页导出可回导的 SYSTEM 菜单模板，不包含插件菜单。 */
    @Transactional(readOnly = true)
    public List<TemplateMenuDTO> exportMenus() {
        return menuRepo.findAll().stream()
                .filter(menu -> menu.getSource() != MenuSource.PLUGIN)
                .sorted(Comparator.comparing(Menu::getCode, Comparator.nullsLast(String::compareTo)))
                .map(menu -> new TemplateMenuDTO(id(menu.getId()), menu.getCode(), menu.getName(), menu.getType(),
                        menu.getParentCode(), menu.getModule(), menu.getIcon(), menu.getPath(), menu.getComponent(),
                        menu.getLink(), menu.getSort(), menu.getVisible(), menu.getPermission(), menu.getStatus()))
                .toList();
    }

    /** 资源级部门导入：允许父子乱序，按路径解析跨系统关联。 */
    public SystemTemplateImportResultDTO importDepartments(List<TemplateDeptImportCmd> departments) {
        return importResources(departments, List.of(), List.of(), List.of());
    }

    /** 资源级角色导入：按角色编码覆盖，并按部门路径解析归属。 */
    public SystemTemplateImportResultDTO importRoles(List<TemplateRoleImportCmd> roles) {
        return importResources(List.of(), roles, List.of(), List.of());
    }

    /** 资源级用户导入：按用户名覆盖，并按部门路径、角色编码解析关联。 */
    public SystemTemplateImportResultDTO importUsers(List<TemplateUserImportCmd> users) {
        return importResources(List.of(), List.of(), users, List.of());
    }

    /** 资源级菜单导入：允许父子乱序，最后再同步菜单权限目录。 */
    public SystemTemplateImportResultDTO importMenus(List<TemplateMenuImportCmd> menus) {
        return importResources(List.of(), List.of(), List.of(), menus);
    }

    /**
     * 资源级批量导入：先完整预检，再依赖顺序写入。
     * Mongo 单机部署当前不提供多集合事务，故结果明确按行报告并可安全重试，绝不假称全量原子恢复。
     */
    public SystemTemplateImportResultDTO importResources(List<TemplateDeptImportCmd> departments,
                                                  List<TemplateRoleImportCmd> roles,
                                                  List<TemplateUserImportCmd> users,
                                                  List<TemplateMenuImportCmd> menus) {
        List<TemplateDeptImportCmd> deptRows = list(departments);
        List<TemplateRoleImportCmd> roleRows = list(roles);
        List<TemplateUserImportCmd> userRows = list(users);
        List<TemplateMenuImportCmd> menuRows = list(menus);
        ImportState state = new ImportState(deptRows.size() + roleRows.size() + userRows.size() + menuRows.size());
        validateDuplicates(deptRows, roleRows, userRows, menuRows, state);
        if (!state.errors.isEmpty()) {
            state.skipUnprocessed();
            return state.result();
        }
        Preflight preflight = preflight(deptRows, roleRows, userRows, menuRows, state);
        if (!state.errors.isEmpty()) {
            state.skipUnprocessed();
            return state.result();
        }
        applyDepartments(preflight, state);
        applyRoles(preflight, state);
        applyUsers(preflight, state);
        applyDepartmentLeaders(preflight, state);
        applyRoleStatuses(preflight, state);
        applyDepartmentStatuses(preflight, state);
        applyMenus(preflight, state);
        applyMenuStatuses(preflight, state);
        if (state.menuTouched) {
            menuAppService.syncPermissionCatalog();
        }
        sendInitialPasswordEmails(state);
        return state.result();
    }

    private void validateDuplicates(List<TemplateDeptImportCmd> departments,
                                    List<TemplateRoleImportCmd> roles,
                                    List<TemplateUserImportCmd> users,
                                    List<TemplateMenuImportCmd> menus,
                                    ImportState state) {
        checkDuplicates(departments, TemplateDeptImportCmd::path, "部门", state, "部门路径");
        checkDuplicates(roles, TemplateRoleImportCmd::code, "角色", state, "角色编码");
        checkDuplicates(users, TemplateUserImportCmd::username, "用户", state, "用户名");
        checkDuplicates(menus, TemplateMenuImportCmd::code, "菜单", state, "菜单编码");
    }

    private Preflight preflight(List<TemplateDeptImportCmd> deptRows,
                                List<TemplateRoleImportCmd> roleRows,
                                List<TemplateUserImportCmd> userRows,
                                List<TemplateMenuImportCmd> menuRows,
                                ImportState state) {

        Map<Long, Dept> existingDeptsById = deptRepo.findAll().stream().filter(item -> item.getId() != null)
                .collect(Collectors.toMap(Dept::getId, Function.identity(), (left, right) -> left));
        Map<Long, String> existingPathsById = departmentPaths(existingDeptsById);
        Map<String, Dept> existingDeptByPath = existingDeptsById.values().stream()
                .collect(Collectors.toMap(dept -> existingPathsById.get(dept.getId()), Function.identity(), (left, right) -> left));
        Map<String, TemplateDeptImportCmd> deptByPath = new LinkedHashMap<>();
        for (int index = 0; index < deptRows.size(); index++) {
            TemplateDeptImportCmd row = deptRows.get(index);
            if (!hasText(row.path())) {
                continue;
            }
            String normalizedPath = normalizePath(row.path());
            if (deptByPath.putIfAbsent(normalizedPath, row) != null) {
                state.error("部门", index + 2, "部门路径重复：" + normalizedPath);
            }
        }

        validateDeptRows(deptRows, deptByPath, existingDeptsById, existingPathsById, existingDeptByPath, state);
        validateRoleRows(roleRows, deptByPath, existingDeptByPath, state);
        validateUserRows(userRows, deptByPath, existingDeptByPath, roleRows, state);
        validateMenuRows(menuRows, state);
        validateImportedIdentityConflicts(deptRows, roleRows, userRows, menuRows, existingDeptsById, existingPathsById, state);
        return new Preflight(deptRows, roleRows, userRows, menuRows, existingDeptsById, existingPathsById, existingDeptByPath);
    }

    private void validateImportedIdentityConflicts(List<TemplateDeptImportCmd> deptRows,
                                                   List<TemplateRoleImportCmd> roleRows,
                                                   List<TemplateUserImportCmd> userRows,
                                                   List<TemplateMenuImportCmd> menuRows,
                                                   Map<Long, Dept> existingDeptsById,
                                                   Map<Long, String> existingPathsById,
                                                   ImportState state) {
        for (int index = 0; index < deptRows.size(); index++) {
            TemplateDeptImportCmd row = deptRows.get(index);
            if (row.sourceId() == null) continue;
            Dept sourceTarget = existingDeptsById.get(row.sourceId());
            Dept pathTarget = existingDeptByPath(existingDeptsById, existingPathsById, normalizePath(row.path()));
            if (sourceTarget != null && pathTarget != null && !Objects.equals(sourceTarget.getId(), pathTarget.getId())) {
                state.warning("部门", index + 2, "部门ID与路径指向不同记录，将按部门路径匹配目标");
            }
        }
        for (int index = 0; index < roleRows.size(); index++) {
            TemplateRoleImportCmd row = roleRows.get(index);
            if (row.sourceId() == null) continue;
            Role byId = roleRepo.findById(row.sourceId()).orElse(null);
            Role byCode = roleRepo.findByCode(row.code()).orElse(null);
            if (byId != null && byCode != null && !Objects.equals(byId.getId(), byCode.getId())) {
                state.warning("角色", index + 2, "角色ID与编码指向不同记录，将按角色编码匹配目标");
            } else if (byId != null && byCode == null && !Objects.equals(byId.getCode(), row.code())) {
                state.error("角色", index + 2, "角色ID与编码均无法匹配目标记录");
            }
        }
        for (int index = 0; index < userRows.size(); index++) {
            TemplateUserImportCmd row = userRows.get(index);
            Long sourceId = row.sourceId();
            if (sourceId == null) continue;
            User byId = userRepo.findById(sourceId).orElse(null);
            User byUsername = userRepo.findByUsernameAll(trim(row.username())).stream().findFirst().orElse(null);
            if (byId != null && byUsername != null && !Objects.equals(byId.getId(), byUsername.getId())) {
                state.warning("用户", index + 2, "用户ID与用户名指向不同记录，将按用户名匹配目标");
            } else if (byId != null && byUsername == null && !Objects.equals(byId.getUsername(), trim(row.username()))) {
                state.error("用户", index + 2, "用户ID与用户名均无法匹配目标记录");
            }
        }
        for (int index = 0; index < menuRows.size(); index++) {
            TemplateMenuImportCmd row = menuRows.get(index);
            if (row.sourceId() == null) continue;
            Menu byId = menuRepo.findById(row.sourceId()).orElse(null);
            Menu byCode = menuRepo.findByCode(row.code()).orElse(null);
            if (byId != null && byCode != null && !Objects.equals(byId.getId(), byCode.getId())) {
                state.warning("菜单", index + 2, "菜单ID与编码指向不同记录，将按菜单编码匹配目标");
            } else if (byId != null && byCode == null && !Objects.equals(byId.getCode(), row.code())) {
                state.error("菜单", index + 2, "菜单ID与编码均无法匹配目标记录");
            }
        }
    }

    private Dept existingDeptByPath(Map<Long, Dept> existingDeptsById,
                                    Map<Long, String> existingPathsById,
                                    String path) {
        return existingDeptsById.values().stream()
                .filter(dept -> Objects.equals(existingPathsById.get(dept.getId()), path))
                .findFirst().orElse(null);
    }

    private void validateDeptRows(List<TemplateDeptImportCmd> rows, Map<String, TemplateDeptImportCmd> byPath,
                                  Map<Long, Dept> existingById, Map<Long, String> existingPaths,
                                  Map<String, Dept> existingDeptByPath, ImportState state) {
        for (int index = 0; index < rows.size(); index++) {
            TemplateDeptImportCmd row = rows.get(index);
            int excelRow = index + 2;
            if (!hasText(row.path()) || !hasText(row.name())) {
                state.error("部门", excelRow, "部门路径和部门名称不能为空");
                continue;
            }
            String path = normalizePath(row.path());
            if (!path.endsWith("/" + row.name().trim()) && !path.equals("/" + row.name().trim())) {
                state.error("部门", excelRow, "部门路径必须以部门名称结尾");
            }
            if (row.parentPath() != null && !row.parentPath().isBlank()) {
                String parentPath = normalizePath(row.parentPath());
                if (!byPath.containsKey(parentPath) && !existingPaths.containsValue(parentPath)) {
                    state.error("部门", excelRow, "上级部门路径不存在：" + parentPath);
                }
            }
            if (row.systemDept()) {
                Dept existing = existingDeptByPath.get(path);
                if (existing == null || !(existing.isSystem() || existing.isRoot())) {
                    state.error("部门", excelRow, "系统部门仅可匹配目标系统已有的系统锚点，不能由模板创建");
                }
            }
        }
        detectDeptCycles(rows, byPath, state);
    }

    private void validateRoleRows(List<TemplateRoleImportCmd> rows, Map<String, TemplateDeptImportCmd> deptByPath,
                                  Map<String, Dept> existingDeptByPath, ImportState state) {
        for (int index = 0; index < rows.size(); index++) {
            TemplateRoleImportCmd row = rows.get(index);
            int excelRow = index + 2;
            if (!hasText(row.code()) || !hasText(row.name()) || !hasText(row.deptPath())) {
                state.error("角色", excelRow, "角色编码、名称和所属部门路径不能为空");
                continue;
            }
            String path = normalizePath(row.deptPath());
            if (!deptByPath.containsKey(path) && !existingDeptByPath.containsKey(path)) {
                state.error("角色", excelRow, "所属部门路径不存在：" + path);
            }
            if (row.systemRole()) {
                Role existing = resolveExistingRoleAnchor(row);
                if (existing == null || !existing.isSystemRole()) {
                    state.error("角色", excelRow, "系统角色仅可匹配目标系统已有的系统锚点，不能由模板创建");
                }
            }
        }
    }

    private void validateUserRows(List<TemplateUserImportCmd> rows, Map<String, TemplateDeptImportCmd> deptByPath,
                                  Map<String, Dept> existingDeptByPath, List<TemplateRoleImportCmd> roleRows,
                                  ImportState state) {
        Set<String> templateRoleCodes = roleRows.stream().map(TemplateRoleImportCmd::code)
                .filter(this::hasText).collect(Collectors.toSet());
        Set<String> existingRoleCodes = roleRepo.findAll().stream().map(Role::getCode).collect(Collectors.toSet());
        for (int index = 0; index < rows.size(); index++) {
            TemplateUserImportCmd row = rows.get(index);
            int excelRow = index + 2;
            if (!hasText(row.username())) {
                state.error("用户", excelRow, "用户名不能为空");
                continue;
            }
            for (String rawPath : list(row.deptPaths())) {
                String path = normalizePath(rawPath);
                if (!deptByPath.containsKey(path) && !existingDeptByPath.containsKey(path)) {
                    state.error("用户", excelRow, "用户部门路径不存在：" + path);
                }
            }
            if (hasText(row.defaultDeptPath())) {
                String defaultPath = normalizePath(row.defaultDeptPath());
                if (!list(row.deptPaths()).stream().map(this::normalizePath).toList().contains(defaultPath)) {
                    state.error("用户", excelRow, "默认部门路径必须属于用户部门路径");
                }
            }
            for (String code : list(row.roleCodes())) {
                if (!templateRoleCodes.contains(code) && !existingRoleCodes.contains(code)) {
                    state.error("用户", excelRow, "角色编码不存在：" + code);
                }
            }
        }
    }

    private void validateMenuRows(List<TemplateMenuImportCmd> rows, ImportState state) {
        Set<String> codes = rows.stream().map(TemplateMenuImportCmd::code).filter(this::hasText).collect(Collectors.toSet());
        for (int index = 0; index < rows.size(); index++) {
            TemplateMenuImportCmd row = rows.get(index);
            int excelRow = index + 2;
            if (!hasText(row.code()) || !hasText(row.name()) || row.type() == null) {
                state.error("菜单", excelRow, "菜单编码、名称和类型不能为空");
                continue;
            }
            if (hasText(row.parentCode()) && !codes.contains(row.parentCode()) && menuRepo.findByCode(row.parentCode()).isEmpty()) {
                state.error("菜单", excelRow, "上级菜单编码不存在：" + row.parentCode());
            }
        }
        detectMenuCycles(rows, state);
    }

    private void applyDepartments(Preflight preflight, ImportState state) {
        Map<String, Long> localIdsByPath = new LinkedHashMap<>(preflight.existingDeptByPath.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().getId(), (left, right) -> left, LinkedHashMap::new)));
        List<TemplateDeptImportCmd> ordered = orderDepartments(preflight.departments);
        for (TemplateDeptImportCmd row : ordered) {
            String path = normalizePath(row.path());
            try {
                Dept target = resolveDeptTarget(row, path, preflight);
                if (target != null && (target.isSystem() || target.isRoot())) {
                    localIdsByPath.put(path, target.getId());
                    state.skipped++;
                    continue;
                }
                Long parentId = hasText(row.parentPath()) ? localIdsByPath.get(normalizePath(row.parentPath())) : null;
                if (hasText(row.parentPath()) && parentId == null) {
                    state.error("部门", rowNumber(preflight.departments, row), "上级部门未能导入，当前部门已跳过");
                    continue;
                }
                DeptStatus writeStatus = target == null && row.status() == DeptStatus.DEPRECATED
                        ? DeptStatus.ACTIVE
                        : target != null && row.status() == DeptStatus.DEPRECATED ? null : row.status();
                TemplateDeptUpsertCmd upsert = deptUpsert(row, parentId, null, writeStatus, false);
                if (target != null && !hasText(row.parentPath())) {
                    upsert.setParentSpecified(false);
                }
                // 用户页未随本批导入时由第二阶段按目标端既有用户名解析；首阶段绝不清空负责人。
                upsert.setLeaderSpecified(false);
                TemplateDeptUpsertResult result = target == null
                        ? deptManageAppService.createFromTemplate(upsert)
                        : deptManageAppService.applyTemplateUpdate(target.getId(), upsert);
                localIdsByPath.put(path, result.getDeptId());
                if (result.isCreated()) state.created++; else state.updated++;
            }
            catch (RuntimeException e) {
                state.error("部门", rowNumber(preflight.departments, row), message(e));
            }
        }
        preflight.localDeptIdsByPath = localIdsByPath;
    }

    private void applyRoles(Preflight preflight, ImportState state) {
        Map<String, Long> localIds = roleRepo.findAll().stream()
                .collect(Collectors.toMap(Role::getCode, Role::getId, (left, right) -> left, LinkedHashMap::new));
        for (TemplateRoleImportCmd row : preflight.roles) {
            try {
                Role target = resolveRoleTarget(row);
                if (target != null && target.isSystemRole()) {
                    localIds.put(row.code(), target.getId());
                    state.skipped++;
                    continue;
                }
                Long deptId = preflight.localDeptIdsByPath.get(normalizePath(row.deptPath()));
                if (deptId == null) {
                    state.error("角色", rowNumber(preflight.roles, row), "所属部门未能导入，当前角色已跳过");
                    continue;
                }
                RoleStatus writeStatus = target == null && row.status() == RoleStatus.DEPRECATED
                        ? RoleStatus.ACTIVE
                        : target != null && row.status() == RoleStatus.DEPRECATED ? null : row.status();
                TemplateRoleUpsertCmd upsert = roleUpsert(row, deptId, writeStatus, false);
                TemplateRoleUpsertResult result = target == null
                        ? roleManageAppService.createFromTemplate(upsert)
                        : roleManageAppService.applyTemplateUpdate(target.getId(), upsert);
                localIds.put(row.code(), result.getRoleId());
                if (result.isCreated()) state.created++; else state.updated++;
            }
            catch (RuntimeException e) {
                state.error("角色", rowNumber(preflight.roles, row), message(e));
            }
        }
        preflight.localRoleIdsByCode = localIds;
    }

    private void applyUsers(Preflight preflight, ImportState state) {
        Map<String, Long> localUserIds = usersForTemplate().stream()
                .filter(user -> hasText(user.getUsername()) && user.getId() != null)
                .collect(Collectors.toMap(User::getUsername, User::getId, (left, right) -> left, LinkedHashMap::new));
        for (TemplateUserImportCmd row : preflight.users) {
            try {
                TemplateUserUpsertCmd upsert = userUpsert(row, preflight.localDeptIdsByPath, preflight.localRoleIdsByCode);
                TemplateUserUpsertResult result = templateUserAppService.upsert(upsert);
                localUserIds.put(result.getUsername(), result.getUserId());
                if (result.isCreated()) state.created++; else state.updated++;
                if (result.isNeedsInitialPasswordEmail()) state.initialPasswordEmailUsers.add(result.getUserId());
            }
            catch (RuntimeException e) {
                state.error("用户", rowNumber(preflight.users, row), message(e));
            }
        }
        preflight.localUserIdsByUsername = localUserIds;
    }

    private void applyDepartmentLeaders(Preflight preflight, ImportState state) {
        Map<String, Long> users = preflight.localUserIdsByUsername;
        for (TemplateDeptImportCmd row : preflight.departments) {
            if (!hasText(row.leaderUsername())) continue;
            Long deptId = preflight.localDeptIdsByPath.get(normalizePath(row.path()));
            Long leaderId = users.get(row.leaderUsername().trim());
            if (leaderId == null) {
                leaderId = userRepo.findByUsernameAll(row.leaderUsername().trim()).stream()
                        .map(User::getId).filter(Objects::nonNull).findFirst().orElse(null);
            }
            if (deptId == null || leaderId == null) {
                state.warning("部门", rowNumber(preflight.departments, row), "负责人未能解析，保留原负责人");
                continue;
            }
            Dept target = deptRepo.findById(deptId).orElse(null);
            if (target == null || target.isSystem() || target.isRoot()) continue;
            try {
                TemplateDeptUpsertCmd update = deptUpsert(row,
                        target.getParentId() == null ? null : target.getParentId().getValue(), leaderId, null, false);
                update.setLeaderSpecified(true);
                deptManageAppService.applyTemplateUpdate(deptId, update);
            }
            catch (RuntimeException e) {
                state.warning("部门", rowNumber(preflight.departments, row), "负责人回填失败：" + message(e));
            }
        }
    }

    private void applyRoleStatuses(Preflight preflight, ImportState state) {
        for (TemplateRoleImportCmd row : preflight.roles) {
            if (row.status() != RoleStatus.DEPRECATED) continue;
            Long roleId = preflight.localRoleIdsByCode.get(row.code());
            Role target = roleId == null ? null : roleRepo.findById(roleId).orElse(null);
            if (target == null || target.isSystemRole() || target.getStatus() == RoleStatus.DEPRECATED) continue;
            try {
                roleManageAppService.applyTemplateUpdate(roleId,
                        roleUpsert(row, target.getDeptId() == null ? null : target.getDeptId().getValue(), RoleStatus.DEPRECATED, false));
            }
            catch (RuntimeException e) {
                state.warning("角色", rowNumber(preflight.roles, row), "状态未同步：" + message(e));
            }
        }
    }

    private void applyDepartmentStatuses(Preflight preflight, ImportState state) {
        List<TemplateDeptImportCmd> childrenFirst = new ArrayList<>(orderDepartments(preflight.departments));
        java.util.Collections.reverse(childrenFirst);
        for (TemplateDeptImportCmd row : childrenFirst) {
            if (row.status() != DeptStatus.DEPRECATED) continue;
            Long deptId = preflight.localDeptIdsByPath.get(normalizePath(row.path()));
            Dept target = deptId == null ? null : deptRepo.findById(deptId).orElse(null);
            if (target == null || target.isSystem() || target.isRoot() || target.getStatus() == DeptStatus.DEPRECATED) continue;
            try {
                TemplateDeptUpsertCmd update = deptUpsert(row,
                        target.getParentId() == null ? null : target.getParentId().getValue(),
                        target.getLeader() == null ? null : target.getLeader().getValue(), DeptStatus.DEPRECATED, false);
                update.setLeaderSpecified(target.getLeader() != null);
                deptManageAppService.applyTemplateUpdate(deptId, update);
            }
            catch (RuntimeException e) {
                state.warning("部门", rowNumber(preflight.departments, row), "状态未同步：" + message(e));
            }
        }
    }

    private void applyMenuStatuses(Preflight preflight, ImportState state) {
        List<TemplateMenuImportCmd> childrenFirst = new ArrayList<>(orderMenus(preflight.menus));
        java.util.Collections.reverse(childrenFirst);
        for (TemplateMenuImportCmd row : childrenFirst) {
            if (row.status() != online.yudream.base.domain.system.menu.enumerate.MenuStatus.DISABLED) continue;
            try {
                MenuImportCmd update = menuImport(row, online.yudream.base.domain.system.menu.enumerate.MenuStatus.DISABLED);
                menuAppService.importMenu(update);
            }
            catch (RuntimeException e) {
                state.warning("菜单", rowNumber(preflight.menus, row), "状态未同步：" + message(e));
            }
        }
    }

    private void applyMenus(Preflight preflight, ImportState state) {
        for (TemplateMenuImportCmd row : orderMenus(preflight.menus)) {
            try {
                var existing = menuRepo.findByCode(row.code()).orElse(null);
                MenuImportDTO result = menuAppService.importMenu(menuImport(row,
                        row.status() == online.yudream.base.domain.system.menu.enumerate.MenuStatus.DISABLED
                                && existing == null
                                ? online.yudream.base.domain.system.menu.enumerate.MenuStatus.ACTIVE
                                : existing != null && row.status() == online.yudream.base.domain.system.menu.enumerate.MenuStatus.DISABLED
                                        ? null : row.status()));
                if (Boolean.TRUE.equals(result.getCreated())) state.created++; else state.updated++;
                state.menuTouched = true;
            }
            catch (RuntimeException e) {
                state.error("菜单", rowNumber(preflight.menus, row), message(e));
            }
        }
    }

    private void sendInitialPasswordEmails(ImportState state) {
        if (state.initialPasswordEmailUsers.isEmpty()) {
            return;
        }
        int queued = 0;
        int failed = 0;
        for (Long userId : state.initialPasswordEmailUsers) {
            try {
                templateUserAppService.sendInitialPasswordSetupEmail(userId);
                queued++;
            }
            catch (RuntimeException e) {
                failed++;
            }
        }
        if (queued > 0) {
            state.warning("用户", 0, "已为 " + queued + " 个新用户提交密码初始化邮件发送任务，不代表邮件已送达");
        }
        if (failed > 0) {
            state.warning("用户", 0, failed + " 个新用户已创建，但密码初始化邮件未能提交，请管理员重发");
        }
    }

    private Dept resolveDeptTarget(TemplateDeptImportCmd row, String path, Preflight preflight) {
        Dept byPath = preflight.existingDeptByPath.get(path);
        if (byPath != null) {
            return byPath;
        }
        if (row.sourceId() != null) {
            return preflight.existingDeptsById.get(row.sourceId());
        }
        return null;
    }

    private Role resolveExistingRoleAnchor(TemplateRoleImportCmd row) {
        if (row.sourceId() != null) {
            Role byId = roleRepo.findById(row.sourceId()).orElse(null);
            if (byId != null && Objects.equals(byId.getCode(), row.code())) {
                return byId;
            }
        }
        return roleRepo.findByCode(row.code()).orElse(null);
    }

    private Role resolveRoleTarget(TemplateRoleImportCmd row) {
        if (row.sourceId() != null) {
            Role byId = roleRepo.findById(row.sourceId()).orElse(null);
            if (byId != null && Objects.equals(byId.getCode(), row.code())) {
                return byId;
            }
        }
        Role byCode = roleRepo.findByCode(row.code()).orElse(null);
        if (byCode != null) {
            return byCode;
        }
        if (row.sourceId() != null && roleRepo.findById(row.sourceId()).isPresent()) {
            throw new BizException("角色ID与编码均无法匹配目标记录");
        }
        return null;
    }

    private TemplateDeptUpsertCmd deptUpsert(TemplateDeptImportCmd row, Long parentId, Long leaderId,
                                             DeptStatus status, boolean templateSnapshot) {
        TemplateDeptUpsertCmd cmd = new TemplateDeptUpsertCmd();
        cmd.setSourceId(row.sourceId());
        cmd.setTemplateSnapshot(templateSnapshot);
        cmd.setName(row.name());
        cmd.setDescription(row.description());
        cmd.setParentSpecified(true);
        cmd.setParentId(parentId);
        cmd.setLeaderSpecified(true);
        cmd.setLeaderId(leaderId);
        cmd.setPhone(row.phone());
        cmd.setSortOrder(row.sortOrder());
        cmd.setColor(row.color());
        cmd.setStatus(status);
        return cmd;
    }

    private TemplateRoleUpsertCmd roleUpsert(TemplateRoleImportCmd row, Long deptId,
                                             RoleStatus status, boolean templateSnapshot) {
        TemplateRoleUpsertCmd cmd = new TemplateRoleUpsertCmd();
        cmd.setSourceId(row.sourceId());
        cmd.setTemplateSnapshot(templateSnapshot);
        cmd.setCode(row.code());
        cmd.setName(row.name());
        cmd.setDeptId(deptId);
        cmd.setLevel(row.level());
        cmd.setPermissions(row.permissions());
        cmd.setIcon(row.icon());
        cmd.setColor(row.color());
        cmd.setStatus(status);
        return cmd;
    }

    private TemplateUserUpsertCmd userUpsert(TemplateUserImportCmd row, Map<String, Long> deptIds, Map<String, Long> roleIds) {
        List<Long> resolvedDepts = list(row.deptPaths()).stream().map(this::normalizePath).map(deptIds::get).filter(Objects::nonNull).toList();
        if (!list(row.deptPaths()).isEmpty() && resolvedDepts.size() != list(row.deptPaths()).size()) {
            throw new BizException("用户部门未能全部导入");
        }
        List<Long> resolvedRoles = list(row.roleCodes()).stream().map(roleIds::get).filter(Objects::nonNull).toList();
        if (!list(row.roleCodes()).isEmpty() && resolvedRoles.size() != list(row.roleCodes()).size()) {
            throw new BizException("用户角色未能全部导入");
        }
        Long defaultDeptId = hasText(row.defaultDeptPath()) ? deptIds.get(normalizePath(row.defaultDeptPath())) : null;
        if (hasText(row.defaultDeptPath()) && defaultDeptId == null) {
            throw new BizException("用户默认部门未能导入");
        }
        TemplateUserUpsertCmd cmd = new TemplateUserUpsertCmd();
        cmd.setTemplateSnapshot(false);
        cmd.setSourceId(row.sourceId() == null ? null : String.valueOf(row.sourceId()));
        cmd.setUsername(row.username());
        cmd.setNickname(row.nickname());
        cmd.setEmail(row.email());
        cmd.setPhone(row.phone());
        cmd.setQq(row.qq());
        cmd.setEmailVerified(row.emailVerified());
        cmd.setStatus(row.status());
        cmd.setDeptIds(resolvedDepts);
        cmd.setDefaultDeptId(defaultDeptId);
        cmd.setRoleIds(resolvedRoles);
        return cmd;
    }

    private MenuImportCmd menuImport(TemplateMenuImportCmd row,
                                     online.yudream.base.domain.system.menu.enumerate.MenuStatus status) {
        MenuImportCmd cmd = new MenuImportCmd();
        cmd.setSourceId(row.sourceId());
        cmd.setCode(row.code());
        cmd.setName(row.name());
        cmd.setType(row.type());
        cmd.setParentCode(row.parentCode());
        cmd.setModule(row.module());
        cmd.setIcon(row.icon());
        cmd.setPath(row.path());
        cmd.setComponent(row.component());
        cmd.setLink(row.link());
        cmd.setSort(row.sort());
        cmd.setVisible(row.visible());
        cmd.setPermission(row.permission());
        cmd.setStatus(status);
        return cmd;
    }

    private List<TemplateDeptImportCmd> orderDepartments(List<TemplateDeptImportCmd> rows) {
        Map<String, TemplateDeptImportCmd> byPath = new LinkedHashMap<>();
        for (TemplateDeptImportCmd row : rows) {
            byPath.putIfAbsent(normalizePath(row.path()), row);
        }
        List<TemplateDeptImportCmd> ordered = new ArrayList<>();
        Set<String> emitted = new HashSet<>();
        for (TemplateDeptImportCmd row : rows) visitDept(row, byPath, emitted, ordered, new HashSet<>());
        return ordered;
    }

    private void visitDept(TemplateDeptImportCmd row, Map<String, TemplateDeptImportCmd> byPath, Set<String> emitted,
                           List<TemplateDeptImportCmd> ordered, Set<String> visiting) {
        String path = normalizePath(row.path());
        if (emitted.contains(path)) return;
        if (!visiting.add(path)) return;
        if (hasText(row.parentPath())) {
            TemplateDeptImportCmd parent = byPath.get(normalizePath(row.parentPath()));
            if (parent != null) visitDept(parent, byPath, emitted, ordered, visiting);
        }
        visiting.remove(path);
        emitted.add(path);
        ordered.add(row);
    }

    private List<TemplateMenuImportCmd> orderMenus(List<TemplateMenuImportCmd> rows) {
        Map<String, TemplateMenuImportCmd> byCode = rows.stream().collect(Collectors.toMap(TemplateMenuImportCmd::code, Function.identity()));
        List<TemplateMenuImportCmd> ordered = new ArrayList<>();
        Set<String> emitted = new HashSet<>();
        for (TemplateMenuImportCmd row : rows) visitMenu(row, byCode, emitted, ordered, new HashSet<>());
        return ordered;
    }

    private void visitMenu(TemplateMenuImportCmd row, Map<String, TemplateMenuImportCmd> byCode, Set<String> emitted,
                           List<TemplateMenuImportCmd> ordered, Set<String> visiting) {
        if (emitted.contains(row.code())) return;
        if (!visiting.add(row.code())) return;
        if (hasText(row.parentCode())) {
            TemplateMenuImportCmd parent = byCode.get(row.parentCode());
            if (parent != null) visitMenu(parent, byCode, emitted, ordered, visiting);
        }
        visiting.remove(row.code());
        emitted.add(row.code());
        ordered.add(row);
    }

    private void detectDeptCycles(List<TemplateDeptImportCmd> rows, Map<String, TemplateDeptImportCmd> byPath, ImportState state) {
        for (TemplateDeptImportCmd row : rows) {
            String cursor = normalizePath(row.path());
            Set<String> visited = new LinkedHashSet<>();
            while (cursor != null) {
                if (!visited.add(cursor)) {
                    state.error("部门", rowNumber(rows, row), "部门层级存在循环");
                    break;
                }
                TemplateDeptImportCmd current = byPath.get(cursor);
                cursor = current == null || !hasText(current.parentPath()) ? null : normalizePath(current.parentPath());
            }
        }
    }

    private void detectMenuCycles(List<TemplateMenuImportCmd> rows, ImportState state) {
        Map<String, TemplateMenuImportCmd> byCode = rows.stream().filter(row -> hasText(row.code()))
                .collect(Collectors.toMap(TemplateMenuImportCmd::code, Function.identity()));
        for (TemplateMenuImportCmd row : rows) {
            String cursor = row.code();
            Set<String> visited = new LinkedHashSet<>();
            while (hasText(cursor) && byCode.containsKey(cursor)) {
                if (!visited.add(cursor)) {
                    state.error("菜单", rowNumber(rows, row), "菜单层级存在循环");
                    break;
                }
                cursor = byCode.get(cursor).parentCode();
            }
        }
    }

    private Map<Long, String> departmentPaths(Map<Long, Dept> departments) {
        Map<Long, String> paths = new HashMap<>();
        for (Dept dept : departments.values()) resolveDeptPath(dept, departments, paths, new HashSet<>());
        return paths;
    }

    private String resolveDeptPath(Dept dept, Map<Long, Dept> all, Map<Long, String> paths, Set<Long> visiting) {
        if (dept == null || dept.getId() == null) return null;
        if (paths.containsKey(dept.getId())) return paths.get(dept.getId());
        if (!visiting.add(dept.getId())) return "/" + dept.getName();
        String parent = dept.getParentId() == null ? null : resolveDeptPath(all.get(dept.getParentId().getValue()), all, paths, visiting);
        String path = (parent == null || parent.equals("/")) ? "/" + dept.getName() : parent + "/" + dept.getName();
        visiting.remove(dept.getId());
        paths.put(dept.getId(), path);
        return path;
    }

    private List<User> usersForTemplate() {
        // UserRepo intentionally has no findAll because normal users are paginated. Template export must not silently cap records.
        // Roles and departments expose all; users are retrieved through the known unique identities represented by active roles/departments
        // only when the repository can page. Until a dedicated stream export port is introduced, page all records deterministically.
        List<User> users = new ArrayList<>();
        int page = 1;
        while (true) {
            var batch = userRepo.page(null, null, null, null, null, page++, 200);
            users.addAll(batch.getRecords());
            if (batch.getRecords().size() < 200 || users.size() >= batch.getTotal()) break;
        }
        return users;
    }

    private <T> void checkDuplicates(List<T> rows, Function<T, String> identity, String sheet, ImportState state, String label) {
        Set<String> seen = new HashSet<>();
        List<T> safeRows = list(rows);
        for (int index = 0; index < safeRows.size(); index++) {
            String value = identity.apply(safeRows.get(index));
            if (!hasText(value)) continue;
            String key = value.trim();
            if (!seen.add(key)) state.error(sheet, index + 2, label + "重复：" + key);
        }
    }

    private <T> int rowNumber(List<T> rows, T row) {
        int index = rows.indexOf(row);
        return index < 0 ? 0 : index + 2;
    }

    private static <T> List<T> list(List<T> value) {
        return value == null ? List.of() : value;
    }

    private boolean hasText(String value) {
        return StringUtils.hasText(value);
    }

    private String normalizePath(String path) {
        if (!hasText(path)) return null;
        String normalized = path.trim().replaceAll("/+", "/");
        if (!normalized.startsWith("/")) normalized = "/" + normalized;
        return normalized.endsWith("/") && normalized.length() > 1 ? normalized.substring(0, normalized.length() - 1) : normalized;
    }

    private String trim(String value) {
        return value == null ? null : value.trim();
    }

    private String id(Long value) {
        return value == null ? null : String.valueOf(value);
    }

    private String message(Throwable error) {
        String message = error.getMessage();
        return hasText(message) ? message : "导入失败";
    }

    private static final class Preflight {
        private final List<TemplateDeptImportCmd> departments;
        private final List<TemplateRoleImportCmd> roles;
        private final List<TemplateUserImportCmd> users;
        private final List<TemplateMenuImportCmd> menus;
        private final Map<Long, Dept> existingDeptsById;
        private final Map<Long, String> existingPathsById;
        private final Map<String, Dept> existingDeptByPath;
        private Map<String, Long> localDeptIdsByPath = Map.of();
        private Map<String, Long> localRoleIdsByCode = Map.of();
        private Map<String, Long> localUserIdsByUsername = Map.of();

        private Preflight(List<TemplateDeptImportCmd> departments, List<TemplateRoleImportCmd> roles,
                          List<TemplateUserImportCmd> users, List<TemplateMenuImportCmd> menus,
                          Map<Long, Dept> existingDeptsById, Map<Long, String> existingPathsById,
                          Map<String, Dept> existingDeptByPath) {
            this.departments = departments;
            this.roles = roles;
            this.users = users;
            this.menus = menus;
            this.existingDeptsById = existingDeptsById;
            this.existingPathsById = existingPathsById;
            this.existingDeptByPath = existingDeptByPath;
        }
    }

    private static final class ImportState {
        private final int total;
        private int created;
        private int updated;
        private int skipped;
        private boolean menuTouched;
        private final List<Long> initialPasswordEmailUsers = new ArrayList<>();
        private final List<SystemTemplateIssueDTO> errors = new ArrayList<>();
        private final List<SystemTemplateIssueDTO> warnings = new ArrayList<>();

        private ImportState(int total) {
            this.total = total;
        }

        private void error(String sheet, int row, String message) {
            errors.add(new SystemTemplateIssueDTO(sheet, row, message));
        }

        private void warning(String sheet, int row, String message) {
            warnings.add(new SystemTemplateIssueDTO(sheet, row, message));
        }

        private SystemTemplateImportResultDTO result() {
            return new SystemTemplateImportResultDTO(total, created + updated, failedRows(), created, updated, skipped,
                    List.copyOf(errors), List.copyOf(warnings));
        }

        private int failedRows() {
            return (int) errors.stream().map(issue -> issue.sheet() + "#" + issue.row()).distinct().count();
        }

        private void skipUnprocessed() {
            skipped = Math.max(skipped, total - failedRows());
        }
    }
}
