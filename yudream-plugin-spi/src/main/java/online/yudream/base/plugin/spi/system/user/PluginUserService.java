package online.yudream.base.plugin.spi.system.user;

import java.util.List;
import java.util.Optional;

public interface PluginUserService {

    Optional<PluginUserProfile> findById(Long userId);

    Optional<PluginUserProfile> findByUsername(String username);

    List<PluginUserRole> listRoles(Long userId);

    List<PluginUserDept> listDepartments(Long userId);

    void updateProfile(Long userId, PluginUserProfileUpdate update);
}
