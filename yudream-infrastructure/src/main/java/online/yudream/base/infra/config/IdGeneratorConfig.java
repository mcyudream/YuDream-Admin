package online.yudream.base.infra.config;

import online.yudream.base.domain.shared.IdGenerator;
import online.yudream.base.domain.shared.SnowflakeIdGenerator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class IdGeneratorConfig {

    @Value("${snowflake.data-center-id:1}")
    private long dataCenterId;

    @Value("${snowflake.machine-id:1}")
    private long machineId;

    @Bean
    public IdGenerator idGenerator() {
        return new SnowflakeIdGenerator(dataCenterId, machineId);
    }
}