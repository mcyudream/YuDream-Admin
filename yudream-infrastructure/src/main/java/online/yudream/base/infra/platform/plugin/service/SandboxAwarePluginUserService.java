package online.yudream.base.infra.platform.plugin.service;

import online.yudream.base.application.platform.plugin.service.PluginUserFrameworkService;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.milky.sandbox.QqSandboxSession;
import online.yudream.base.domain.system.user.aggregate.Role;
import online.yudream.base.domain.system.user.repo.RoleRepo;
import online.yudream.base.plugin.spi.system.user.PluginDeptOption;
import online.yudream.base.plugin.spi.system.user.PluginUserCreate;
import online.yudream.base.plugin.spi.system.user.PluginUserDept;
import online.yudream.base.plugin.spi.system.user.PluginUserOption;
import online.yudream.base.plugin.spi.system.user.PluginUserProfile;
import online.yudream.base.plugin.spi.system.user.PluginUserProfileUpdate;
import online.yudream.base.plugin.spi.system.user.PluginUserRole;
import online.yudream.base.plugin.spi.system.user.PluginUserService;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 沙盒身份模拟装饰器：仅当 QQ 沙盒执行作用域激活时改写身份判定，
 * 生产链路与沙盒共用同一 SPI 实现，保证插件观察到的行为一致。
 */
@Service
@Primary
public class SandboxAwarePluginUserService implements PluginUserService {

    private final PluginUserFrameworkService delegate;
    private final RoleRepo roleRepo;

    public SandboxAwarePluginUserService(PluginUserFrameworkService delegate, RoleRepo roleRepo) {
        this.delegate = delegate;
        this.roleRepo = roleRepo;
    }

    @Override
    public Optional<PluginUserProfile> authenticate(String usernameOrEmail, String password) {
        return delegate.authenticate(usernameOrEmail, password);
    }

    @Override
    public PluginUserProfile create(PluginUserCreate create) {
        rejectSandboxWrite("create");
        return delegate.create(create);
    }

    @Override
    public Optional<PluginUserProfile> findById(Long userId) {
        return delegate.findById(userId);
    }

    @Override
    public Optional<PluginUserProfile> findByUsername(String username) {
        return delegate.findByUsername(username);
    }

    @Override
    public Optional<PluginUserProfile> findByEmail(String email) {
        return delegate.findByEmail(email);
    }

    @Override
    public Optional<PluginUserProfile> findByQq(String qq) {
        QqSandboxSession session = QqSandboxExecutionScope.current();
        if (session != null && session.forceUnbound()) {
            appendIdentityOverride(session, Map.of("type", "forceUnbound", "qq", qq == null ? "" : qq));
            return Optional.empty();
        }
        return delegate.findByQq(qq);
    }

    @Override
    public void bindQqOnce(Long userId, String qq) {
        rejectSandboxWrite("bindQqOnce");
        delegate.bindQqOnce(userId, qq);
    }

    @Override
    public List<PluginUserOption> searchUsers(String keyword, Long deptId, int page, int size) {
        return delegate.searchUsers(keyword, deptId, page, size);
    }

    @Override
    public List<PluginDeptOption> listDepartments(String keyword) {
        return delegate.listDepartments(keyword);
    }

    @Override
    public List<PluginUserRole> listRoles(Long userId) {
        QqSandboxSession session = QqSandboxExecutionScope.current();
        List<String> simulated = session == null ? null : session.simulateRoles();
        if (session == null || simulated == null) {
            return delegate.listRoles(userId);
        }
        Map<String, Role> byCode = roleRepo.findAll().stream()
                .collect(Collectors.toMap(Role::getCode, Function.identity(), (left, right) -> left));
        List<String> unknown = simulated.stream().filter(code -> !byCode.containsKey(code)).toList();
        List<PluginUserRole> roles = simulated.stream()
                .map(byCode::get)
                .filter(role -> role != null)
                .map(role -> new PluginUserRole(role.getId(), role.getCode(), role.getName()))
                .toList();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", "simulateRoles");
        payload.put("userId", String.valueOf(userId));
        payload.put("roles", simulated);
        payload.put("unknownRoles", unknown);
        appendIdentityOverride(session, payload);
        return roles;
    }

    @Override
    public List<PluginUserDept> listDepartments(Long userId) {
        return delegate.listDepartments(userId);
    }

    @Override
    public void updateProfile(Long userId, PluginUserProfileUpdate update) {
        rejectSandboxWrite("updateProfile");
        delegate.updateProfile(userId, update);
    }

    private void rejectSandboxWrite(String operation) {
        QqSandboxSession session = QqSandboxExecutionScope.current();
        if (session == null) {
            return;
        }
        appendIdentityOverride(session, Map.of("type", "writeBlocked", "operation", operation));
        throw new BizException("QQ 沙箱会话禁止写入系统用户数据：" + operation);
    }

    private void appendIdentityOverride(QqSandboxSession session, Map<String, Object> payload) {
        if (session.acceptsCaptures()) {
            session.append("sandbox", "identity.override", session.pluginCode(), payload);
        }
    }
}
