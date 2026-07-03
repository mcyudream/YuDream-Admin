package online.yudream.base.domain.system.user.repo;

import online.yudream.base.domain.common.PageResult;
import online.yudream.base.domain.system.user.aggregate.User;
import online.yudream.base.domain.system.user.enumerate.UserStatus;

import java.util.List;
import java.util.Optional;

public interface UserRepo {
    User save(User user);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    boolean existsByQQ(String qq);

    boolean existsByPhone(String phone);

    Optional<User> findByUsername(String username);

    Optional<User> findByEmail(String email);

    Optional<User> findById(Long id);

    List<User> findByIds(List<Long> ids);

    PageResult<User> page(String keyword, Long deptId, Long roleId, Boolean emailVerified, UserStatus status, int page, int size);

    boolean existsByUsernameExcludeId(String username, Long excludeId);

    boolean existsByEmailExcludeId(String email, Long excludeId);

    boolean existsByPhoneExcludeId(String phone, Long excludeId);

    boolean existsByQQExcludeId(String qq, Long excludeId);

    long countByRoleId(Long roleId);

    long countByDeptId(Long deptId);
}
