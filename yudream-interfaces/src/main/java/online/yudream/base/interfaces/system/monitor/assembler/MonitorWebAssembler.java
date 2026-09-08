package online.yudream.base.interfaces.system.monitor.assembler;

import online.yudream.base.domain.common.PageResult;
import online.yudream.base.domain.system.monitor.dto.ApiLogDTO;
import online.yudream.base.domain.system.monitor.dto.HostDiskDTO;
import online.yudream.base.domain.system.monitor.dto.HostNetworkDTO;
import online.yudream.base.domain.system.monitor.dto.HostProcessDTO;
import online.yudream.base.domain.system.monitor.dto.HostResourceSnapshotDTO;
import online.yudream.base.domain.system.monitor.dto.HostThreadHotspotDTO;
import online.yudream.base.domain.system.monitor.dto.JvmGcDTO;
import online.yudream.base.domain.system.monitor.dto.JvmMemoryPoolDTO;
import online.yudream.base.domain.system.monitor.dto.LoginLogDTO;
import online.yudream.base.domain.system.monitor.dto.OnlineUserDTO;
import online.yudream.base.domain.system.monitor.dto.RedisKeySampleDTO;
import online.yudream.base.domain.system.monitor.dto.RedisMonitorDTO;
import online.yudream.base.domain.system.monitor.dto.ResourceMetricPointDTO;
import online.yudream.base.interfaces.system.monitor.res.ApiLogRes;
import online.yudream.base.interfaces.system.monitor.res.HostDiskRes;
import online.yudream.base.interfaces.system.monitor.res.HostNetworkRes;
import online.yudream.base.interfaces.system.monitor.res.HostProcessRes;
import online.yudream.base.interfaces.system.monitor.res.HostResourceSnapshotRes;
import online.yudream.base.interfaces.system.monitor.res.HostThreadHotspotRes;
import online.yudream.base.interfaces.system.monitor.res.JvmGcRes;
import online.yudream.base.interfaces.system.monitor.res.JvmMemoryPoolRes;
import online.yudream.base.interfaces.system.monitor.res.LoginLogRes;
import online.yudream.base.interfaces.system.monitor.res.OnlineUserRes;
import online.yudream.base.interfaces.system.monitor.res.RedisKeySampleRes;
import online.yudream.base.interfaces.system.monitor.res.RedisMonitorRes;
import online.yudream.base.interfaces.system.monitor.res.ResourceMetricPointRes;

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

    public static HostResourceSnapshotRes toRes(HostResourceSnapshotDTO dto) {
        if (dto == null) {
            return HostResourceSnapshotRes.builder()
                    .disks(List.of())
                    .networks(List.of())
                    .memoryPools(List.of())
                    .garbageCollectors(List.of())
                    .topProcesses(List.of())
                    .topThreads(List.of())
                    .build();
        }
        return HostResourceSnapshotRes.builder()
                .hostname(dto.getHostname())
                .osName(dto.getOsName())
                .osVersion(dto.getOsVersion())
                .architecture(dto.getArchitecture())
                .cpuName(dto.getCpuName())
                .cpuPhysicalCount(dto.getCpuPhysicalCount())
                .cpuLogicalCount(dto.getCpuLogicalCount())
                .cpuUsagePercent(dto.getCpuUsagePercent())
                .load1(dto.getLoad1())
                .load5(dto.getLoad5())
                .load15(dto.getLoad15())
                .memoryTotalBytes(dto.getMemoryTotalBytes())
                .memoryUsedBytes(dto.getMemoryUsedBytes())
                .memoryAvailableBytes(dto.getMemoryAvailableBytes())
                .memoryUsagePercent(dto.getMemoryUsagePercent())
                .swapTotalBytes(dto.getSwapTotalBytes())
                .swapUsedBytes(dto.getSwapUsedBytes())
                .disks(dto.getDisks() == null ? List.of() : dto.getDisks().stream().map(MonitorWebAssembler::toRes).toList())
                .diskTotalBytes(dto.getDiskTotalBytes())
                .diskUsedBytes(dto.getDiskUsedBytes())
                .diskUsagePercent(dto.getDiskUsagePercent())
                .networks(dto.getNetworks() == null ? List.of() : dto.getNetworks().stream().map(MonitorWebAssembler::toRes).toList())
                .networkRecvBytesPerSec(dto.getNetworkRecvBytesPerSec())
                .networkSentBytesPerSec(dto.getNetworkSentBytesPerSec())
                .jvmPid(dto.getJvmPid())
                .javaVersion(dto.getJavaVersion())
                .jvmUptimeMs(dto.getJvmUptimeMs())
                .jvmHeapUsedBytes(dto.getJvmHeapUsedBytes())
                .jvmHeapMaxBytes(dto.getJvmHeapMaxBytes())
                .jvmNonHeapUsedBytes(dto.getJvmNonHeapUsedBytes())
                .jvmNonHeapMaxBytes(dto.getJvmNonHeapMaxBytes())
                .jvmDirectUsedBytes(dto.getJvmDirectUsedBytes())
                .jvmDirectMaxBytes(dto.getJvmDirectMaxBytes())
                .jvmThreadCount(dto.getJvmThreadCount())
                .jvmDaemonThreadCount(dto.getJvmDaemonThreadCount())
                .jvmLoadedClassCount(dto.getJvmLoadedClassCount())
                .memoryPools(dto.getMemoryPools() == null ? List.of() : dto.getMemoryPools().stream().map(MonitorWebAssembler::toRes).toList())
                .garbageCollectors(dto.getGarbageCollectors() == null ? List.of() : dto.getGarbageCollectors().stream().map(MonitorWebAssembler::toRes).toList())
                .topProcesses(dto.getTopProcesses() == null ? List.of() : dto.getTopProcesses().stream().map(MonitorWebAssembler::toRes).toList())
                .topThreads(dto.getTopThreads() == null ? List.of() : dto.getTopThreads().stream().map(MonitorWebAssembler::toRes).toList())
                .notice(dto.getNotice())
                .sampledAt(dto.getSampledAt())
                .build();
    }

    public static List<ResourceMetricPointRes> toResourceHistoryRes(List<ResourceMetricPointDTO> points) {
        return points == null ? List.of() : points.stream().map(MonitorWebAssembler::toRes).toList();
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

    private static HostDiskRes toRes(HostDiskDTO dto) {
        return HostDiskRes.builder()
                .name(dto.getName())
                .mount(dto.getMount())
                .type(dto.getType())
                .totalBytes(dto.getTotalBytes())
                .usedBytes(dto.getUsedBytes())
                .usableBytes(dto.getUsableBytes())
                .usagePercent(dto.getUsagePercent())
                .build();
    }

    private static HostNetworkRes toRes(HostNetworkDTO dto) {
        return HostNetworkRes.builder()
                .name(dto.getName())
                .displayName(dto.getDisplayName())
                .ipv4(dto.getIpv4())
                .mac(dto.getMac())
                .recvBytes(dto.getRecvBytes())
                .sentBytes(dto.getSentBytes())
                .recvBytesPerSec(dto.getRecvBytesPerSec())
                .sentBytesPerSec(dto.getSentBytesPerSec())
                .build();
    }

    private static JvmMemoryPoolRes toRes(JvmMemoryPoolDTO dto) {
        return JvmMemoryPoolRes.builder()
                .name(dto.getName())
                .type(dto.getType())
                .usedBytes(dto.getUsedBytes())
                .committedBytes(dto.getCommittedBytes())
                .maxBytes(dto.getMaxBytes())
                .usagePercent(dto.getUsagePercent())
                .build();
    }

    private static JvmGcRes toRes(JvmGcDTO dto) {
        return JvmGcRes.builder()
                .name(dto.getName())
                .collectionCount(dto.getCollectionCount())
                .collectionTimeMs(dto.getCollectionTimeMs())
                .build();
    }

    private static HostProcessRes toRes(HostProcessDTO dto) {
        return HostProcessRes.builder()
                .pid(dto.getPid())
                .name(dto.getName())
                .user(dto.getUser())
                .rssBytes(dto.getRssBytes())
                .virtualBytes(dto.getVirtualBytes())
                .cpuPercent(dto.getCpuPercent())
                .currentJvm(dto.isCurrentJvm())
                .commandLine(dto.getCommandLine())
                .build();
    }

    private static HostThreadHotspotRes toRes(HostThreadHotspotDTO dto) {
        return HostThreadHotspotRes.builder()
                .threadId(dto.getThreadId())
                .name(dto.getName())
                .state(dto.getState())
                .cpuTimeMs(dto.getCpuTimeMs())
                .userTimeMs(dto.getUserTimeMs())
                .daemon(dto.isDaemon())
                .stackTop(dto.getStackTop() == null ? List.of() : dto.getStackTop())
                .build();
    }

    private static ResourceMetricPointRes toRes(ResourceMetricPointDTO dto) {
        return ResourceMetricPointRes.builder()
                .sampledAt(dto.getSampledAt())
                .cpuUsagePercent(dto.getCpuUsagePercent())
                .memoryUsedBytes(dto.getMemoryUsedBytes())
                .memoryTotalBytes(dto.getMemoryTotalBytes())
                .memoryUsagePercent(dto.getMemoryUsagePercent())
                .swapUsedBytes(dto.getSwapUsedBytes())
                .swapTotalBytes(dto.getSwapTotalBytes())
                .diskUsedBytes(dto.getDiskUsedBytes())
                .diskTotalBytes(dto.getDiskTotalBytes())
                .diskUsagePercent(dto.getDiskUsagePercent())
                .networkRecvBytesPerSec(dto.getNetworkRecvBytesPerSec())
                .networkSentBytesPerSec(dto.getNetworkSentBytesPerSec())
                .jvmHeapUsedBytes(dto.getJvmHeapUsedBytes())
                .jvmHeapMaxBytes(dto.getJvmHeapMaxBytes())
                .jvmNonHeapUsedBytes(dto.getJvmNonHeapUsedBytes())
                .build();
    }
}
