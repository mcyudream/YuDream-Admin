package online.yudream.base.interfaces.system.monitor.res;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
@Builder
public class RedisMonitorRes {
    private boolean connected;
    private String version;
    private Long dbSize;
    private String uptime;
    private String usedMemory;
    private String maxMemory;
    private Long connectedClients;
    private Long totalCommands;
    private Long opsPerSecond;
    private Long keyspaceHits;
    private Long keyspaceMisses;
    private Double hitRate;
    private Map<String, String> keyspace;
    private List<RedisKeySampleRes> keys;
    private String message;
}
