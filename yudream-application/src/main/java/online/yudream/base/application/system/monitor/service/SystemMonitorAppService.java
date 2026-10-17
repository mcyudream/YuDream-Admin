package online.yudream.base.application.system.monitor.service;

import lombok.RequiredArgsConstructor;
import online.yudream.base.application.system.file.service.FileAppService;
import online.yudream.base.domain.common.PageResult;
import online.yudream.base.domain.system.monitor.dto.ApiLogDTO;
import online.yudream.base.domain.system.monitor.dto.LoginLogDTO;
import online.yudream.base.domain.system.monitor.dto.OnlineUserDTO;
import online.yudream.base.domain.system.monitor.repo.ApiLogRepo;
import online.yudream.base.domain.system.monitor.repo.LoginLogRepo;
import online.yudream.base.domain.system.monitor.service.OnlineUserGateway;
import online.yudream.base.domain.system.user.aggregate.User;
import online.yudream.base.domain.system.user.repo.UserRepo;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SystemMonitorAppService {

    private final ApiLogRepo apiLogRepo;
    private final LoginLogRepo loginLogRepo;
    private final OnlineUserGateway onlineUserGateway;
    private final UserRepo userRepo;
    private final FileAppService fileAppService;

    public void recordApiLog(ApiLogDTO log) {
        apiLogRepo.save(log);
    }

    public void recordLoginLog(LoginLogDTO log) {
        loginLogRepo.save(log);
    }

    public PageResult<ApiLogDTO> pageApiLogs(String keyword, Boolean success, int page, int size) {
        PageResult<ApiLogDTO> result = apiLogRepo.page(keyword, success, page, size);
        enrichApiLogUsers(result.getRecords());
        return result;
    }

    public PageResult<LoginLogDTO> pageLoginLogs(String keyword, Boolean success, int page, int size) {
        PageResult<LoginLogDTO> result = loginLogRepo.page(keyword, success, page, size);
        enrichLoginLogAvatars(result.getRecords());
        return result;
    }

    public long clearApiLogs() {
        return apiLogRepo.clear();
    }

    public long clearLoginLogs() {
        return loginLogRepo.clear();
    }

    public PageResult<OnlineUserDTO> pageOnlineUsers(String keyword, int page, int size) {
        PageResult<OnlineUserDTO> result = onlineUserGateway.page(keyword, page, size);
        enrichOnlineUserAvatars(result.getRecords());
        return result;
    }

    /** 导出等一次性取数场景：需要全量会话而非单页。 */
    public List<OnlineUserDTO> onlineUsers(String keyword, int limit) {
        List<OnlineUserDTO> users = onlineUserGateway.list(keyword, limit);
        enrichOnlineUserAvatars(users);
        return users;
    }

    /** 会话用户头像：按 userId 批量补齐，取不到给 null，不影响在线列表本身。 */
    private void enrichOnlineUserAvatars(List<OnlineUserDTO> users) {
        if (users == null || users.isEmpty()) {
            return;
        }
        List<Long> userIds = users.stream()
                .map(OnlineUserDTO::getUserId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (userIds.isEmpty()) {
            return;
        }
        Map<Long, User> userMap = userRepo.findByIds(userIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity(), (left, right) -> left));
        for (OnlineUserDTO user : users) {
            User entity = userMap.get(user.getUserId());
            user.setAvatar(entity == null ? null : fileAppService.fileUrl(entity.getAvatarFileId()));
        }
    }

    public void kickout(String token) {
        onlineUserGateway.kickout(token);
    }

    private void enrichApiLogUsers(List<ApiLogDTO> logs) {
        if (logs == null || logs.isEmpty()) {
            return;
        }
        List<Long> userIds = logs.stream()
                .map(ApiLogDTO::getLoginId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (userIds.isEmpty()) {
            return;
        }
        Map<Long, User> userMap = userRepo.findByIds(userIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity(), (left, right) -> left));
        for (ApiLogDTO log : logs) {
            User user = userMap.get(log.getLoginId());
            if (user != null) {
                log.setUsername(user.getUsername());
                log.setNickname(user.getNickname());
                log.setAvatarUrl(fileAppService.fileUrl(user.getAvatarFileId()));
            }
        }
    }

    /** 登录日志会话用户头像：按 userId 批量补齐，取不到给 null。 */
    private void enrichLoginLogAvatars(List<LoginLogDTO> logs) {
        if (logs == null || logs.isEmpty()) {
            return;
        }
        List<Long> userIds = logs.stream()
                .map(LoginLogDTO::getUserId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (userIds.isEmpty()) {
            return;
        }
        Map<Long, User> userMap = userRepo.findByIds(userIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity(), (left, right) -> left));
        for (LoginLogDTO log : logs) {
            User user = userMap.get(log.getUserId());
            if (user != null) {
                log.setAvatarUrl(fileAppService.fileUrl(user.getAvatarFileId()));
            }
        }
    }
}
