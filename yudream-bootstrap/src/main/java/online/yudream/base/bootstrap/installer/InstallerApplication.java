package online.yudream.base.bootstrap.installer;

import org.springframework.boot.Banner;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;

import java.util.List;

/**
 * 安装器切片入口：仅装配安装向导相关的接口/应用/基础设施包，
 * 排除 MongoDB/Redis/邮件自动配置，供「无数据库」状态下完成安装向导。
 * 引导配置写盘后由安装服务触发延迟退出，容器重启策略拉起后走正常启动。
 */
@SpringBootApplication(scanBasePackages = {
        "online.yudream.base.interfaces.installer",
        "online.yudream.base.application.installer",
        "online.yudream.base.infra.installer",
        "online.yudream.base.bootstrap.installer"
})
public class InstallerApplication {

    /** 安装器模式必须排除的自动配置（避免在无中间件状态下初始化客户端）。 */
    public static final String AUTOCONFIGURE_EXCLUDES = String.join(",", List.of(
            "org.springframework.boot.autoconfigure.mongo.MongoAutoConfiguration",
            "org.springframework.boot.autoconfigure.data.mongo.MongoDataAutoConfiguration",
            "org.springframework.boot.autoconfigure.data.mongo.MongoRepositoriesAutoConfiguration",
            "org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration",
            "org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration",
            "org.springframework.boot.autoconfigure.mail.MailSenderAutoConfiguration",
            // SaTokenDaoForRedisTemplate 依赖 Redis 自动配置提供的 StringRedisTemplate，需一并排除
            "cn.dev33.satoken.dao.SaTokenDaoForRedisTemplate"
    ));

    public static SpringApplicationBuilder builder() {
        return new SpringApplicationBuilder(InstallerApplication.class)
                .bannerMode(Banner.Mode.OFF)
                .properties("spring.autoconfigure.exclude=" + AUTOCONFIGURE_EXCLUDES);
    }

    public static void run(String[] args) {
        builder().run(args);
    }
}
