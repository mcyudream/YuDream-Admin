package online.yudream.base.domain.system.monitor.service;

import online.yudream.base.domain.system.monitor.dto.RedisMonitorDTO;

public interface RedisMonitorGateway {

    RedisMonitorDTO overview(String pattern, int limit);
}
