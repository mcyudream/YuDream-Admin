package online.yudream.base.application.system.user.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import online.yudream.base.application.system.user.cmd.UserLoginCmd;
import online.yudream.base.application.system.user.assembler.UserAssembler;
import online.yudream.base.application.system.user.dto.UserDTO;
import online.yudream.base.domain.common.service.PasswordEncoder;
import online.yudream.base.domain.system.user.aggregate.User;
import online.yudream.base.domain.system.user.repo.UserRepo;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@RequiredArgsConstructor
@Slf4j
@Service
public class UserAppService {
    private final UserRepo userRepo;
    private final PasswordEncoder passwordEncoder;

    @Transactional(readOnly = true)
    public Optional<UserDTO> login(UserLoginCmd cmd) {
        Optional<User> userOpt = userRepo.findByUsername(cmd.getUsername());
        if (userOpt.isEmpty()) {
            return Optional.empty();
        }
        User user = userOpt.get();
        if (user.getPassword().matches(cmd.getPassword(), passwordEncoder)) {
            return Optional.empty();
        }
        log.info("用户登录成功: id={}, username={}", user.getId(), user.getUsername());
        return Optional.of(UserAssembler.toDTO(user));
    }
}
