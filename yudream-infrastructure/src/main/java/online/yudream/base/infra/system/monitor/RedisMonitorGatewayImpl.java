package online.yudream.base.infra.system.monitor;

import lombok.RequiredArgsConstructor;
import online.yudream.base.domain.system.monitor.dto.RedisKeySampleDTO;
import online.yudream.base.domain.system.monitor.dto.RedisMonitorDTO;
import online.yudream.base.domain.system.monitor.service.RedisMonitorGateway;
import org.springframework.data.redis.connection.DataType;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

@Service
@RequiredArgsConstructor
public class RedisMonitorGatewayImpl implements RedisMonitorGateway {

    private final StringRedisTemplate stringRedisTemplate;

    @Override
    public RedisMonitorDTO overview(String pattern, int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 200));
        String match = StringUtils.hasText(pattern) ? pattern : "*";
        try {
            Properties info = stringRedisTemplate.execute((RedisCallback<Properties>) connection -> connection.serverCommands().info());
            Long dbSize = stringRedisTemplate.execute((RedisCallback<Long>) connection -> connection.serverCommands().dbSize());
            List<RedisKeySampleDTO> keys = scanKeys(match, safeLimit);
            long hits = longValue(info, "keyspace_hits");
            long misses = longValue(info, "keyspace_misses");
            double hitRate = hits + misses == 0 ? 0 : Math.round((hits * 10000.0 / (hits + misses))) / 100.0;
            return RedisMonitorDTO.builder()
                    .connected(true)
                    .version(value(info, "redis_version"))
                    .dbSize(dbSize == null ? 0 : dbSize)
                    .uptime(value(info, "uptime_in_days") + " days")
                    .usedMemory(value(info, "used_memory_human"))
                    .maxMemory(value(info, "maxmemory_human"))
                    .connectedClients(longValue(info, "connected_clients"))
                    .totalCommands(longValue(info, "total_commands_processed"))
                    .opsPerSecond(longValue(info, "instantaneous_ops_per_sec"))
                    .keyspaceHits(hits)
                    .keyspaceMisses(misses)
                    .hitRate(hitRate)
                    .keyspace(keyspace(info))
                    .keys(keys)
                    .build();
        }
        catch (Exception e) {
            return RedisMonitorDTO.builder()
                    .connected(false)
                    .message(e.getMessage())
                    .keys(List.of())
                    .keyspace(Map.of())
                    .build();
        }
    }

    private List<RedisKeySampleDTO> scanKeys(String pattern, int limit) {
        List<RedisKeySampleDTO> keys = new ArrayList<>();
        ScanOptions options = ScanOptions.scanOptions().match(pattern).count(Math.max(20, limit)).build();
        try (Cursor<String> cursor = stringRedisTemplate.scan(options)) {
            while (cursor.hasNext() && keys.size() < limit) {
                String key = cursor.next();
                DataType type = stringRedisTemplate.type(key);
                Long ttl = stringRedisTemplate.getExpire(key);
                keys.add(RedisKeySampleDTO.builder()
                        .key(key)
                        .type(type == null ? "NONE" : type.code())
                        .ttl(ttl)
                        .build());
            }
        }
        return keys;
    }

    private Map<String, String> keyspace(Properties info) {
        Map<String, String> map = new LinkedHashMap<>();
        for (String name : info.stringPropertyNames()) {
            if (name.startsWith("db")) {
                map.put(name, info.getProperty(name));
            }
        }
        return map;
    }

    private String value(Properties info, String key) {
        return info == null ? "" : info.getProperty(key, "");
    }

    private long longValue(Properties info, String key) {
        String value = value(info, key);
        if (!StringUtils.hasText(value)) {
            return 0L;
        }
        try {
            return Long.parseLong(value);
        }
        catch (NumberFormatException e) {
            return 0L;
        }
    }
}
