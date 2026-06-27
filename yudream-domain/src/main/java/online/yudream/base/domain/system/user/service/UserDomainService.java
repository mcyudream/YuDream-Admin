package online.yudream.base.domain.system.user.service;

import lombok.RequiredArgsConstructor;
import online.yudream.base.domain.system.user.repo.UserRepo;

@RequiredArgsConstructor
public class UserDomainService {
    private final UserRepo userRepo;


    public void ensureUsernameUnique(String username) {
        if (userRepo.existsByUsername(username)) {
            throw new IllegalArgumentException("用户名已存在: " + username);
        }
    }

    public void ensureEmailUnique(String email) {
        if (userRepo.existsByEmail(email)) {
            throw new IllegalArgumentException("邮箱已存在: " + email);
        }
    }

    public void ensurePhone(String phone) {
        if (userRepo.existsByPhone(phone)) {
            throw new IllegalArgumentException("电话已存在: " + phone);
        }
    }

    public void ensureQQ(String qq) {
        if (userRepo.existsByQQ(qq)) {
            throw new IllegalArgumentException("QQ已存在: " + qq);
        }
    }

}
