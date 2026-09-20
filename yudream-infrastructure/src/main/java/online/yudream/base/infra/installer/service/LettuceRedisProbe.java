package online.yudream.base.infra.installer.service;

import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisCommandExecutionException;
import io.lettuce.core.RedisConnectionException;
import io.lettuce.core.RedisURI;
import io.lettuce.core.SocketOptions;
import io.lettuce.core.api.StatefulRedisConnection;
import online.yudream.base.domain.installer.service.RedisProbe;
import online.yudream.base.domain.installer.valobj.RedisProbeResult;
import online.yudream.base.domain.installer.valobj.RedisProbeSpec;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.Locale;

/**
 * Redis 探针实现：基于 Lettuce 按需建立短连接，探测完即关闭，不产生长驻资源。
 */
@Component
public class LettuceRedisProbe implements RedisProbe {

    private static final Duration TIMEOUT = Duration.ofSeconds(3);

    @Override
    public RedisProbeResult probe(RedisProbeSpec spec) {
        if (spec == null || !StringUtils.hasText(spec.host())) {
            return RedisProbeResult.unreachable("Redis 地址不能为空");
        }
        RedisURI.Builder uriBuilder = RedisURI.builder()
                .withHost(spec.host())
                .withPort(spec.port())
                .withDatabase(spec.database())
                .withTimeout(TIMEOUT);
        if (spec.ssl()) {
            uriBuilder.withSsl(true);
        }
        if (spec.password() != null) {
            uriBuilder.withPassword(spec.password().toCharArray());
        }
        RedisClient client = RedisClient.create(uriBuilder.build());
        client.setOptions(io.lettuce.core.ClientOptions.builder()
                .socketOptions(SocketOptions.builder().connectTimeout(TIMEOUT).build())
                .build());
        long start = System.currentTimeMillis();
        try (StatefulRedisConnection<String, String> connection = client.connect()) {
            // INFO 命令输出按行读取，避免部分托管 Redis 禁用 INFO 时误判
            String pong = connection.sync().ping();
            if (pong == null || !"PONG".equalsIgnoreCase(pong.trim())) {
                return RedisProbeResult.unreachable("PING 响应异常：" + pong);
            }
            long latency = System.currentTimeMillis() - start;
            return RedisProbeResult.ok(latency, readVersion(connection.sync()));
        } catch (RedisCommandExecutionException e) {
            long latency = System.currentTimeMillis() - start;
            String message = e.getMessage() == null ? "" : e.getMessage().toUpperCase(Locale.ROOT);
            if (message.contains("WRONGPASS") || message.contains("ERR invalid password")) {
                return RedisProbeResult.authFailed(latency, "认证失败：用户名或密码不正确");
            }
            if (message.contains("NOAUTH") || message.contains("unauthenticated") || message.contains("AUTH")) {
                return RedisProbeResult.authRequired(latency, "服务器已启用认证，请填写 Redis 密码");
            }
            return RedisProbeResult.unreachable("服务器返回错误：" + e.getMessage());
        } catch (RedisConnectionException e) {
            return RedisProbeResult.unreachable("无法连接目标 Redis");
        } catch (Exception e) {
            return RedisProbeResult.unreachable("连接失败：" + e.getMessage());
        } finally {
            client.shutdown();
        }
    }

    private String readVersion(io.lettuce.core.api.sync.RedisCommands<String, String> commands) {
        try {
            String info = commands.info("server");
            if (info == null) {
                return null;
            }
            for (String line : info.split("\n")) {
                String trimmed = line.trim();
                if (trimmed.startsWith("redis_version:")) {
                    return trimmed.substring("redis_version:".length()).trim();
                }
            }
        } catch (Exception ignored) {
            // 版本信息获取失败不影响连通性结论
        }
        return null;
    }
}
