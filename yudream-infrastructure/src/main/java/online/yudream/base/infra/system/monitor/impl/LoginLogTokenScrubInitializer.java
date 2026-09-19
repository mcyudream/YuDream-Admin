package online.yudream.base.infra.system.monitor.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

/**
 * 历史登录日志脱敏：登录审计曾明文落库会话令牌，启动时统一清除存量 token 字段，
 * 新记录已不再写入令牌。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class LoginLogTokenScrubInitializer implements ApplicationRunner {

    private final MongoTemplate mongoTemplate;

    @Override
    public void run(ApplicationArguments args) {
        try {
            Query query = Query.query(Criteria.where("token").exists(true));
            Update update = new Update().unset("token");
            var result = mongoTemplate.updateMulti(query, update, "sysLoginLog");
            if (result.getModifiedCount() > 0) {
                log.info("已清除登录日志中的历史明文令牌 {} 条", result.getModifiedCount());
            }
        } catch (Exception e) {
            log.warn("清理登录日志历史令牌失败：{}", e.getMessage());
        }
    }
}
