package online.yudream.base.plugin.spi.system.user;

import java.util.List;
import java.util.Optional;

public interface PluginUserService {

    Optional<PluginUserProfile> authenticate(String usernameOrEmail, String password);

    PluginUserProfile create(PluginUserCreate create);

    Optional<PluginUserProfile> findById(Long userId);

    Optional<PluginUserProfile> findByUsername(String username);

    Optional<PluginUserProfile> findByEmail(String email);

    Optional<PluginUserProfile> findByQq(String qq);

    void bindQqOnce(Long userId, String qq);

    /**
     * 按协议身份查找系统用户。默认实现把 {@code identity} 当作 QQ 号走 {@link #findByQq(String)}，
     * 宿主会覆盖为按 milky QQ / 官方 user_openid / member_openid 精确匹配。
     */
    default Optional<PluginUserProfile> findByMessagingIdentity(PluginMessagingIdentity identity) {
        if (identity == null || identity.identity() == null || identity.identity().isBlank()) {
            return Optional.empty();
        }
        return findByQq(identity.identity());
    }

    /**
     * 绑定当前消息协议身份。默认实现走 {@link #bindQqOnce(Long, String)}，
     * 宿主会按当前事件的协议/场景写入身份表，官方 openid 不会覆盖 {@code User.qq}。
     */
    default void bindMessagingIdentityOnce(Long userId, PluginMessagingIdentity identity) {
        bindQqOnce(userId, identity == null ? null : identity.identity());
    }

    /**
     * 列出用户已绑定的消息身份。默认只回退 {@code PluginUserProfile.qq} 作为 milky QQ。
     */
    default List<PluginMessagingIdentity> listMessagingIdentities(Long userId) {
        return findById(userId)
                .filter(profile -> profile.qq() != null && !profile.qq().isBlank())
                .map(profile -> List.of(new PluginMessagingIdentity("milky", "qq", profile.qq(), null, null, null)))
                .orElse(List.of());
    }

    List<PluginUserOption> searchUsers(String keyword, Long deptId, int page, int size);

    List<PluginDeptOption> listDepartments(String keyword);

    List<PluginUserRole> listRoles(Long userId);

    List<PluginUserDept> listDepartments(Long userId);

    void updateProfile(Long userId, PluginUserProfileUpdate update);

    /**
     * 读取用户全部标签（所有命名空间）。用户不存在时返回空列表。
     */
    List<PluginUserTag> listTags(Long userId);

    /**
     * 按命名空间整体替换标签。传入的 tag.namespace 会被强制写成参数 namespace，
     * 插件无法写入其他插件的命名空间。tags 为 null 或空则清空该命名空间。
     */
    void replaceTags(Long userId, String namespace, List<PluginUserTag> tags);

    /**
     * 按命名空间整体替换人员扩展字段。宿主将字段持久化为动态 Mongo 映射，
     * 插件不能读写其他插件的命名空间。
     */
    void replaceFields(Long userId, String namespace, List<PluginUserField> fields);
}
