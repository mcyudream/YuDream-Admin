package online.yudream.base.domain.system.user.repo;

import online.yudream.base.domain.system.user.aggregate.User;

import java.util.Optional;

public interface UserRepo {
    User save(User user);
    boolean existsByUsername(String username);
    boolean existsByEmail(String email);
    boolean existsByQQ(String email);
    boolean existsByPhone(String email);
    Optional<User> findByUsername(String username);
}
