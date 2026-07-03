package online.yudream.base.interfaces.system.monitor.assembler;

import online.yudream.base.domain.common.PageResult;
import online.yudream.base.domain.system.monitor.dto.ApiLogDTO;
import online.yudream.base.domain.system.monitor.dto.LoginLogDTO;
import online.yudream.base.domain.system.monitor.dto.OnlineUserDTO;
import online.yudream.base.domain.system.monitor.dto.RedisKeySampleDTO;
import online.yudream.base.domain.system.monitor.dto.RedisMonitorDTO;
import online.yudream.base.interfaces.system.monitor.res.ApiLogRes;
import online.yudream.base.interfaces.system.monitor.res.LoginLogRes;
import online.yudream.base.interfaces.system.monitor.res.OnlineUserRes;
import online.yudream.base.interfaces.system.monitor.res.RedisKeySampleRes;
import online.yudream.base.interfaces.system.monitor.res.RedisMonitorRes;

import java.util.List;

public class MonitorWebAssembler {

    private MonitorWebAssembler() {
    }

    public static List<OnlineUserRes> toOnlineUserResList(List<OnlineUserDTO> items) {
        return items == null ? List.of() : items.stream().map(MonitorWebAssembler::toRes).toList();
    }

    public static PageResult<ApiLogRes> toApiLogPage(PageResult<ApiLogDTO> page) {
        return new PageResult<>(page.getRecords().stream().map(MonitorWebAssembler::toRes).toList(), page.getTotal(), page.getPage(), page.getSize());
    }

    public static PageResult<LoginLogRes> toLoginLogPage(PageResult<LoginLogDTO> page) {
        return new PageResult<>(page.getRecords().stream().map(MonitorWebAssembler::toRes).toList(), page.getTotal(), page.getPage(), page.getSize());
    }

    public static RedisMonitorRes toRes(RedisMonitorDTO dto) {
        return RedisMonitorRes.builder()
                .connected(dto.isConnected())
                .version(dto.getVersion())
                .dbSize(dto.getDbSize())
                .uptime(dto.getUptime())
                .usedMemory(dto.getUsedMemory())
                .maxMemory(dto.getMaxMemory())
                .connectedClients(dto.getConnectedClients())
                .totalCommands(dto.getTotalCommands())
                .opsPerSecond(dto.getOpsPerSecond())
                .keyspaceHits(dto.getKeyspaceHits())
                .keyspaceMisses(dto.getKeyspaceMisses())
                .hitRate(dto.getHitRate())
                .keyspace(dto.getKeyspace())
                .keys(dto.getKeys() == null ? List.of() : dto.getKeys().stream().map(MonitorWebAssembler::toRes).toList())
                .message(dto.getMessage())
                .build();
    }

    private static OnlineUserRes toRes(OnlineUserDTO dto) {
        return OnlineUserRes.builder()
                .token(dto.getToken())
                .userId(dto.getUserId())
                .username(dto.getUsername())
                .nickname(dto.getNickname())
                .email(dto.getEmail())
                .timeout(dto.getTimeout())
                .activeTimeout(dto.getActiveTimeout())
                .device(dto.getDevice())
                .build();
    }

    private static ApiLogRes toRes(ApiLogDTO dto) {
        return ApiLogRes.builder()
                .id(dto.getId())
                .method(dto.getMethod())
                .path(dto.getPath())
                .query(dto.getQuery())
                .requestBody(dto.getRequestBody())
                .status(dto.getStatus())
                .costMs(dto.getCostMs())
                .success(dto.getSuccess())
                .loginId(dto.getLoginId())
                .username(dto.getUsername())
                .nickname(dto.getNickname())
                .ip(dto.getIp())
                .userAgent(dto.getUserAgent())
                .errorMessage(dto.getErrorMessage())
                .createTime(dto.getCreateTime())
                .build();
    }

    private static LoginLogRes toRes(LoginLogDTO dto) {
        return LoginLogRes.builder()
                .id(dto.getId())
                .username(dto.getUsername())
                .userId(dto.getUserId())
                .success(dto.getSuccess())
                .message(dto.getMessage())
                .ip(dto.getIp())
                .userAgent(dto.getUserAgent())
                .token(dto.getToken())
                .createTime(dto.getCreateTime())
                .build();
    }

    private static RedisKeySampleRes toRes(RedisKeySampleDTO dto) {
        return RedisKeySampleRes.builder()
                .key(dto.getKey())
                .type(dto.getType())
                .ttl(dto.getTtl())
                .build();
    }
}
