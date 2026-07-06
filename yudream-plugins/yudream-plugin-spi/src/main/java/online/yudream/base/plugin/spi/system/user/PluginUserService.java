package online.yudream.base.plugin.spi.system.user;

import java.util.List;
import java.util.Optional;

public interface PluginUserService {

    Optional<PluginUserProfile> authenticate(String usernameOrEmail, String password);

    PluginUserProfile create(PluginUserCreate create);

    Optional<PluginUserProfile> findById(Long userId);

    Optional<PluginUserProfile> findByUsername(String username);

    Optional<PluginUserProfile> findByEmail(String email);

    List<PluginUserRole> listRoles(Long userId);

    List<PluginUserDept> listDepartments(Long userId);

    void updateProfile(Long userId, PluginUserProfileUpdate update);
}
