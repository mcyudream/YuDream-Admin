package online.yudream.base.domain.system.monitor.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RedisMonitorDTO {

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

    private List<RedisKeySampleDTO> keys;

    private String message;
}
