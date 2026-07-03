package online.yudream.base.application.system.monitor.service;

import lombok.RequiredArgsConstructor;
import online.yudream.base.domain.system.monitor.dto.RedisMonitorDTO;
import online.yudream.base.domain.system.monitor.service.RedisMonitorGateway;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RedisMonitorAppService {

    private final RedisMonitorGateway redisMonitorGateway;

    public RedisMonitorDTO overview(String pattern, int limit) {
        return redisMonitorGateway.overview(pattern, limit);
    }
}
