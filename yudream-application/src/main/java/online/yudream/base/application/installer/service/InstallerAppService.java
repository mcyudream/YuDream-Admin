package online.yudream.base.application.installer.service;

import lombok.extern.slf4j.Slf4j;
import online.yudream.base.application.installer.assembler.InstallerAssembler;
import online.yudream.base.application.installer.cmd.InstallerApplyCmd;
import online.yudream.base.application.installer.cmd.MongoProbeCmd;
import online.yudream.base.application.installer.cmd.RedisProbeCmd;
import online.yudream.base.application.installer.dto.InstallerApplyResultDTO;
import online.yudream.base.application.installer.dto.InstallerStatusDTO;
import online.yudream.base.application.installer.dto.MiddlewareDiscoveryDTO;
import online.yudream.base.application.installer.dto.MiddlewareProbeDTO;
import online.yudream.base.application.installer.event.InstallerConfigAppliedEvent;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.installer.repo.BootstrapConfigStore;
import online.yudream.base.domain.installer.service.MongoProbe;
import online.yudream.base.domain.installer.service.RedisProbe;
import online.yudream.base.domain.installer.valobj.MongoProbeSpec;
import online.yudream.base.domain.installer.valobj.RedisProbeSpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * 安装器应用服务：状态自检、中间件连通探测与自动发现、引导配置落盘。
 * 仅在安装器模式（无引导配置且未预置数据库配置）下装配。
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "yudream.bootstrap.installer", havingValue = "true")
public class InstallerAppService {

    /** 落盘后延迟退出，给响应留出返回时间；容器由重启策略拉起进入正常模式。 */
    public static final long RESTART_DELAY_MS = 1500;

    private final MongoProbe mongoProbe;
    private final RedisProbe redisProbe;
    private final BootstrapConfigStore bootstrapConfigStore;
    private final InstallerAssembler assembler;
    private final ApplicationEventPublisher eventPublisher;
    private final Environment environment;

    /**
     * 部署级初始化令牌（可选）：设置 YUDREAM_SETUP_TOKEN 后，安装请求必须携带匹配值，
     * 防止初始化窗口期被匿名抢装。
     */
    private final String setupTokenExpected;

    public InstallerAppService(MongoProbe mongoProbe,
                               RedisProbe redisProbe,
                               BootstrapConfigStore bootstrapConfigStore,
                               InstallerAssembler assembler,
                               ApplicationEventPublisher eventPublisher,
                               Environment environment,
                               @Value("${YUDREAM_SETUP_TOKEN:}") String setupTokenExpected) {
        this.mongoProbe = mongoProbe;
        this.redisProbe = redisProbe;
        this.bootstrapConfigStore = bootstrapConfigStore;
        this.assembler = assembler;
        this.eventPublisher = eventPublisher;
        this.environment = environment;
        this.setupTokenExpected = setupTokenExpected;
    }

    public InstallerStatusDTO status() {
        boolean dockerDeployment = false;
        try {
            dockerDeployment = Files.exists(java.nio.file.Path.of("/.dockerenv"));
        } catch (Exception ignored) {
            // 非 Linux/容器环境视为非容器部署
        }
        return assembler.toStatusDTO(true,
                bootstrapConfigStore.location().toAbsolutePath().toString(),
                dockerDeployment,
                System.getProperty("java.version"),
                StringUtils.hasText(setupTokenExpected));
    }

    public MiddlewareProbeDTO probeMongo(MongoProbeCmd cmd) {
        MongoProbeSpec spec = assembler.toSpec(cmd);
        return assembler.toDTO(masked(spec.uri()), mongoProbe.probe(spec));
    }

    public MiddlewareProbeDTO probeRedis(RedisProbeCmd cmd) {
        RedisProbeSpec spec = assembler.toSpec(cmd);
        return assembler.toDTO(spec.target(), redisProbe.probe(spec));
    }

    /**
     * 中间件自动发现：按部署提示（YUDREAM_DISCOVERY_*）→ compose 惯用服务名 → 本机默认端口的
     * 顺序并行探测候选，可达项按延迟升序排列，供向导直接预填。
     */
    public MiddlewareDiscoveryDTO discover() {
        List<CompletableFuture<MiddlewareProbeDTO>> mongoFutures = new ArrayList<>();
        for (String candidate : mongoCandidates()) {
            mongoFutures.add(CompletableFuture.supplyAsync(() ->
                    assembler.toDTO(masked(candidate), mongoProbe.probe(new MongoProbeSpec(candidate)))));
        }
        List<CompletableFuture<MiddlewareProbeDTO>> redisFutures = new ArrayList<>();
        for (RedisProbeSpec candidate : redisCandidates()) {
            redisFutures.add(CompletableFuture.supplyAsync(() ->
                    assembler.toDTO(candidate.target(), redisProbe.probe(candidate))));
        }
        List<MiddlewareProbeDTO> mongo = new ArrayList<>();
        mongoFutures.forEach(future -> mongo.add(future.join()));
        List<MiddlewareProbeDTO> redis = new ArrayList<>();
        redisFutures.forEach(future -> redis.add(future.join()));
        mongo.sort(probeOrder());
        redis.sort(probeOrder());
        return assembler.toDiscoveryDTO(List.copyOf(mongo), List.copyOf(redis));
    }

    public InstallerApplyResultDTO apply(InstallerApplyCmd cmd) {
        if (cmd == null || !StringUtils.hasText(cmd.getMongoUri())) {
            throw new BizException("MongoDB 连接串不能为空");
        }
        if (!StringUtils.hasText(cmd.getRedisHost())) {
            throw new BizException("Redis 地址不能为空（登录会话依赖 Redis，暂不支持跳过）");
        }
        if (bootstrapConfigStore.exists()) {
            throw new BizException("系统已完成安装，引导配置已存在，请勿重复安装");
        }
        if (StringUtils.hasText(setupTokenExpected)
                && !setupTokenExpected.equals(cmd.getSetupToken())) {
            log.warn("安装请求令牌校验失败，已拒绝");
            throw new BizException("安装令牌缺失或不匹配");
        }
        MiddlewareProbeDTO mongo = assembler.toDTO(masked(cmd.getMongoUri().trim()),
                mongoProbe.probe(new MongoProbeSpec(cmd.getMongoUri())));
        if (!mongo.isReachable() || mongo.isAuthFailed()) {
            throw new BizException("MongoDB 连接失败：" + mongo.getMessage());
        }
        MiddlewareProbeDTO redis = assembler.toDTO(cmd.getRedisHost().trim() + ":" +
                        (cmd.getRedisPort() == null ? 6379 : cmd.getRedisPort()),
                redisProbe.probe(assembler.toSpec(toRedisCmd(cmd))));
        if (!redis.isReachable() || redis.isAuthFailed()) {
            throw new BizException("Redis 连接失败：" + redis.getMessage());
        }
        String credentialKey = assembler.normalizeCredentialKey(cmd.getCredentialKey());
        bootstrapConfigStore.save(assembler.toConfig(cmd, credentialKey));
        log.info("安装配置已写入引导文件，系统将在 {}ms 后重启以进入正常模式", RESTART_DELAY_MS);
        eventPublisher.publishEvent(new InstallerConfigAppliedEvent(
                bootstrapConfigStore.location().toAbsolutePath().toString()));
        return assembler.toApplyResultDTO(
                bootstrapConfigStore.location().toAbsolutePath().toString(), RESTART_DELAY_MS);
    }

    private List<String> mongoCandidates() {
        Set<String> candidates = new LinkedHashSet<>();
        String hintUri = environment.getProperty("YUDREAM_DISCOVERY_MONGO_URI");
        if (StringUtils.hasText(hintUri)) {
            candidates.add(hintUri.trim());
        }
        String hintHost = environment.getProperty("YUDREAM_DISCOVERY_MONGO_HOST");
        if (StringUtils.hasText(hintHost)) {
            String hintPort = environment.getProperty("YUDREAM_DISCOVERY_MONGO_PORT");
            candidates.add("mongodb://" + hintHost.trim() + ":" + defaultPort(hintPort, 27017));
        }
        candidates.add("mongodb://mongo:27017");
        candidates.add("mongodb://localhost:27017");
        return List.copyOf(candidates);
    }

    private List<RedisProbeSpec> redisCandidates() {
        List<RedisProbeSpec> candidates = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        String hintHost = environment.getProperty("YUDREAM_DISCOVERY_REDIS_HOST");
        String hintPort = environment.getProperty("YUDREAM_DISCOVERY_REDIS_PORT");
        if (StringUtils.hasText(hintHost)) {
            String host = hintHost.trim();
            int port = defaultPort(hintPort, 6379);
            seen.add(host + ":" + port);
            candidates.add(new RedisProbeSpec(host, port, null, 0, false));
        }
        for (String host : List.of("redis", "localhost")) {
            if (seen.add(host + ":6379")) {
                candidates.add(new RedisProbeSpec(host, 6379, null, 0, false));
            }
        }
        return candidates;
    }

    private RedisProbeCmd toRedisCmd(InstallerApplyCmd cmd) {
        RedisProbeCmd probeCmd = new RedisProbeCmd();
        probeCmd.setHost(cmd.getRedisHost());
        probeCmd.setPort(cmd.getRedisPort());
        probeCmd.setPassword(cmd.getRedisPassword());
        probeCmd.setDatabase(cmd.getRedisDatabase());
        probeCmd.setSsl(cmd.getRedisSsl());
        return probeCmd;
    }

    private int defaultPort(String port, int fallback) {
        try {
            return StringUtils.hasText(port) ? Integer.parseInt(port.trim()) : fallback;
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private String masked(String uri) {
        if (uri == null) {
            return null;
        }
        // 隐藏连接串中的密码段，避免探测目标回显凭据
        return uri.replaceAll("(://[^:/@]+:)[^@]+(@)", "$1****$2");
    }

    private Comparator<MiddlewareProbeDTO> probeOrder() {
        return Comparator.comparing(MiddlewareProbeDTO::isReachable).reversed()
                .thenComparingLong(MiddlewareProbeDTO::getLatencyMs);
    }
}
