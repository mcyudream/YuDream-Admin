package online.yudream.base.application.system.user.service;

import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.chat.repo.ChatMessageRepo;
import online.yudream.base.domain.platform.chat.repo.ChatQuotaRepo;
import online.yudream.base.domain.platform.chat.repo.ChatSessionRepo;
import online.yudream.base.domain.platform.mobile.repo.MobileDeviceRepo;
import online.yudream.base.domain.system.monitor.service.OnlineUserGateway;
import online.yudream.base.domain.system.notification.repo.NotificationRepo;
import online.yudream.base.domain.system.security.repo.ApiKeyCredentialRepo;
import online.yudream.base.domain.system.security.repo.ExternalAccountRepo;
import online.yudream.base.domain.system.security.repo.OAuthAccessTokenRepo;
import online.yudream.base.domain.system.security.repo.OAuthAuthorizationCodeRepo;
import online.yudream.base.domain.system.security.repo.PasskeyCredentialRepo;
import online.yudream.base.domain.system.security.repo.RefreshTokenCredentialRepo;
import online.yudream.base.domain.system.user.aggregate.Role;
import online.yudream.base.domain.system.user.aggregate.User;
import online.yudream.base.domain.system.user.enumerate.RoleLevel;
import online.yudream.base.domain.system.user.repo.MessagingIdentityRepo;
import online.yudream.base.domain.system.user.repo.RoleRepo;
import online.yudream.base.domain.system.user.repo.UserRepo;
import online.yudream.base.domain.system.user.valobj.RoleID;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;

import static org.junit.jupiter.api.Assertions.*;

class UserDeletionAppServiceTest {

    @Test
    void deletesAccountWithFullCascadeAndSessionKickoutWhenOperatorOutranksTarget() {
        Fixture fixture = new Fixture();
        fixture.service().delete(1L, 55L);

        assertEquals(List.of(55L), fixture.deletedUserIds);
        assertEquals(1, fixture.kickouts);
        assertEquals(USER_SCOPED_DELETES, fixture.cascadeCalls);
    }

    @Test
    void rejectsDeletingSelfBeforeAnyLookupOrWrite() {
        Fixture fixture = new Fixture();
        assertEquals("不能删除自己", assertThrows(BizException.class, () -> fixture.service().delete(1L, 1L)).getMessage());
        assertTrue(fixture.cascadeCalls.isEmpty());
        assertTrue(fixture.deletedUserIds.isEmpty());
        assertEquals(0, fixture.kickouts);
    }

    @Test
    void rejectsSameOrHigherLevelTargetsWithoutCascade() {
        Fixture same = new Fixture();
        same.users.put(55L, userWithRole(55L, 10L));
        assertEquals("不能删除同级或更高权限的用户",
                assertThrows(BizException.class, () -> same.service().delete(1L, 55L)).getMessage());
        assertTrue(same.cascadeCalls.isEmpty());
        assertTrue(same.deletedUserIds.isEmpty());
        assertEquals(0, same.kickouts);

        Fixture higher = new Fixture();
        higher.users.put(55L, userWithRole(55L, 30L));
        assertThrows(BizException.class, () -> higher.service().delete(1L, 55L));
        assertTrue(higher.cascadeCalls.isEmpty());
    }

    @Test
    void rejectsMissingTargetOperatorAndBlankIdentity() {
        Fixture fixture = new Fixture();
        assertEquals("用户不存在", assertThrows(BizException.class, () -> fixture.service().delete(1L, 99L)).getMessage());
        assertEquals("无法识别操作者身份", assertThrows(BizException.class, () -> fixture.service().delete(7L, 55L)).getMessage());
        assertEquals("无法识别操作者身份", assertThrows(BizException.class, () -> fixture.service().delete(null, 55L)).getMessage());
        assertEquals("用户不存在", assertThrows(BizException.class, () -> fixture.service().delete(1L, null)).getMessage());
        assertTrue(fixture.deletedUserIds.isEmpty());
    }

    @Test
    void rolelessOperatorCannotDeleteARolelessOrAnyRankedUser() {
        Fixture fixture = new Fixture();
        fixture.users.put(1L, userWithRole(1L));
        assertThrows(BizException.class, () -> fixture.service().delete(1L, 55L));
        assertTrue(fixture.deletedUserIds.isEmpty());
    }

    /** 级联清理的固定顺序：身份 → 凭据 → 设备 → 通知 → 聊天。 */
    private static final List<String> USER_SCOPED_DELETES = List.of(
            "MessagingIdentityRepo", "PasskeyCredentialRepo", "RefreshTokenCredentialRepo",
            "OAuthAccessTokenRepo", "OAuthAuthorizationCodeRepo", "ExternalAccountRepo", "ApiKeyCredentialRepo",
            "MobileDeviceRepo", "NotificationRepo", "ChatMessageRepo", "ChatSessionRepo", "ChatQuotaRepo");

    private static User userWithRole(Long id, Long... roleIds) {
        return User.builder().id(id)
                .roles(List.of(roleIds).stream().map(RoleID::of).toList())
                .build();
    }

    @SuppressWarnings("unchecked")
    private static <T> T stub(Class<T> type, BiFunction<String, Object[], Object> action) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type},
                (proxy, method, args) -> {
                    Object result = action.apply(method.getName(), args);
                    if (result != null) {
                        return result;
                    }
                    Class<?> returnType = method.getReturnType();
                    if (returnType == int.class) {
                        return 0;
                    }
                    if (returnType == long.class) {
                        return 0L;
                    }
                    if (returnType == boolean.class) {
                        return false;
                    }
                    return null;
                });
    }

    private static class Fixture {
        final List<String> cascadeCalls = new ArrayList<>();
        final List<Long> deletedUserIds = new ArrayList<>();
        final Map<Long, User> users = new HashMap<>();
        final Map<Long, RoleLevel> roleLevels = Map.of(10L, RoleLevel.ADMIN, 20L, RoleLevel.USER, 30L, RoleLevel.SUPER_ADMIN);
        int kickouts;

        Fixture() {
            users.put(1L, userWithRole(1L, 10L));
            users.put(55L, userWithRole(55L, 20L));
        }

        UserDeletionAppService service() {
            UserRepo userRepo = stub(UserRepo.class, (method, args) -> switch (method) {
                case "findById" -> Optional.ofNullable(users.get((Long) args[0]));
                case "deleteByIds" -> {
                    deletedUserIds.addAll((List<Long>) args[0]);
                    yield null;
                }
                default -> null;
            });
            RoleRepo roleRepo = stub(RoleRepo.class, (method, args) -> ((List<Long>) args[0]).stream()
                    .map(id -> Role.builder().id(id).level(roleLevels.get(id)).build())
                    .toList());
            OnlineUserGateway gateway = stub(OnlineUserGateway.class, (method, args) -> switch (method) {
                case "kickoutByUserId" -> {
                    kickouts++;
                    yield null;
                }
                default -> null;
            });
            return new UserDeletionAppService(userRepo, roleRepo,
                    cascade(MessagingIdentityRepo.class), cascade(PasskeyCredentialRepo.class),
                    cascade(RefreshTokenCredentialRepo.class), cascade(OAuthAccessTokenRepo.class),
                    cascade(OAuthAuthorizationCodeRepo.class), cascade(ExternalAccountRepo.class),
                    cascade(ApiKeyCredentialRepo.class), cascade(MobileDeviceRepo.class),
                    cascade(NotificationRepo.class), cascade(ChatMessageRepo.class),
                    cascade(ChatSessionRepo.class), cascade(ChatQuotaRepo.class), gateway);
        }

        private <T> T cascade(Class<T> type) {
            return stub(type, (method, args) -> {
                cascadeCalls.add(type.getSimpleName());
                return null;
            });
        }
    }
}
