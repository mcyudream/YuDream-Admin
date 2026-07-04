package online.yudream.base.application.system.monitor.service;

import lombok.RequiredArgsConstructor;
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
        return loginLogRepo.page(keyword, success, page, size);
    }

    public long clearApiLogs() {
        return apiLogRepo.clear();
    }

    public long clearLoginLogs() {
        return loginLogRepo.clear();
    }

    public List<OnlineUserDTO> onlineUsers(String keyword, int limit) {
        return onlineUserGateway.list(keyword, limit);
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
            }
        }
    }
}
