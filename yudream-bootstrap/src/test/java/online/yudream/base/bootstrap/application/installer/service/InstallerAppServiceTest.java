package online.yudream.base.bootstrap.application.installer.service;

import online.yudream.base.application.installer.assembler.InstallerAssembler;
import online.yudream.base.application.installer.cmd.InstallerApplyCmd;
import online.yudream.base.application.installer.dto.InstallerApplyResultDTO;
import online.yudream.base.application.installer.service.InstallerAppService;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.installer.repo.BootstrapConfigStore;
import online.yudream.base.domain.installer.service.MongoProbe;
import online.yudream.base.domain.installer.service.RedisProbe;
import online.yudream.base.domain.installer.valobj.BootstrapConfig;
import online.yudream.base.domain.installer.valobj.MongoProbeResult;
import online.yudream.base.domain.installer.valobj.MongoProbeSpec;
import online.yudream.base.domain.installer.valobj.RedisProbeResult;
import online.yudream.base.domain.installer.valobj.RedisProbeSpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InstallerAppServiceTest {

    private StubMongoProbe mongoProbe;
    private StubRedisProbe redisProbe;
    private StubStore store;
    private List<Object> events;
    private MockEnvironment environment;
    private InstallerAppService service;

    @BeforeEach
    void setUp() {
        mongoProbe = new StubMongoProbe();
        redisProbe = new StubRedisProbe();
        store = new StubStore();
        events = new ArrayList<>();
        environment = new MockEnvironment();
        service = new InstallerAppService(mongoProbe, redisProbe, store,
                new InstallerAssembler(), events::add, environment, "");
    }

    private InstallerApplyCmd validCmd() {
        InstallerApplyCmd cmd = new InstallerApplyCmd();
        cmd.setMongoUri("mongodb://mongo:27017/yudream");
        cmd.setRedisHost("redis");
        return cmd;
    }

    @Test
    void applySavesConfigGeneratesKeyAndPublishesEvent() {
        InstallerApplyResultDTO result = service.apply(validCmd());

        assertTrue(result.isRestarting());
        assertNotNull(result.getBootstrapFileLocation());
        assertNotNull(store.saved);
        assertEquals(32, Base64.getDecoder().decode(store.saved.credentialKey()).length);
        assertEquals("redis", store.saved.redisHost());
        assertEquals(1, events.size());
    }

    @Test
    void applyKeepsProvidedCredentialKeyWhenValid() {
        InstallerApplyCmd cmd = validCmd();
        cmd.setCredentialKey("67M7G+5hekFUH3BRfkrLv0JTBVaspj8gQh16z3uyVeI=");
        service.apply(cmd);
        assertEquals("67M7G+5hekFUH3BRfkrLv0JTBVaspj8gQh16z3uyVeI=", store.saved.credentialKey());
    }

    @Test
    void applyRejectsSecondInstall() {
        service.apply(validCmd());
        assertThrows(BizException.class, () -> service.apply(validCmd()));
    }

    @Test
    void applyRejectsUnreachableMongo() {
        mongoProbe.result = MongoProbeResult.unreachable("连接超时，无法访问目标 MongoDB");
        BizException e = assertThrows(BizException.class, () -> service.apply(validCmd()));
        assertTrue(e.getMessage().contains("MongoDB 连接失败"));
    }

    @Test
    void applyRejectsUnreachableRedis() {
        redisProbe.result = RedisProbeResult.unreachable("无法连接目标 Redis");
        BizException e = assertThrows(BizException.class, () -> service.apply(validCmd()));
        assertTrue(e.getMessage().contains("Redis 连接失败"));
    }

    @Test
    void applyRejectsBlankRedisHost() {
        InstallerApplyCmd cmd = validCmd();
        cmd.setRedisHost(" ");
        BizException e = assertThrows(BizException.class, () -> service.apply(cmd));
        assertTrue(e.getMessage().contains("Redis 地址不能为空"));
    }

    @Test
    void applyRejectsInvalidCredentialKey() {
        InstallerApplyCmd cmd = validCmd();
        cmd.setCredentialKey("not-base64!!");
        assertThrows(BizException.class, () -> service.apply(cmd));
    }

    @Test
    void applyRejectsWrongLengthCredentialKey() {
        InstallerApplyCmd cmd = validCmd();
        cmd.setCredentialKey(Base64.getEncoder().encodeToString(new byte[16]));
        assertThrows(BizException.class, () -> service.apply(cmd));
    }

    @Test
    void applyRejectsMissingSetupToken() {
        InstallerAppService tokenService = new InstallerAppService(mongoProbe, redisProbe, store,
                new InstallerAssembler(), events::add, environment, "secret-token");
        InstallerApplyCmd cmd = validCmd();
        BizException e = assertThrows(BizException.class, () -> tokenService.apply(cmd));
        assertTrue(e.getMessage().contains("安装令牌"));
        cmd.setSetupToken("secret-token");
        tokenService.apply(cmd);
    }

    @Test
    void discoverRanksReachableCandidatesFirst() {
        mongoProbe.result = MongoProbeResult.ok(12, "7.0.0");
        environment.setProperty("YUDREAM_DISCOVERY_MONGO_HOST", "mongo");
        environment.setProperty("YUDREAM_DISCOVERY_REDIS_HOST", "redis");
        redisProbe.result = RedisProbeResult.authRequired(5, "服务器已启用认证，请填写 Redis 密码");

        var discovery = service.discover();

        assertFalse(discovery.getMongo().isEmpty());
        assertEquals("mongodb://mongo:27017", discovery.getMongo().get(0).getTarget());
        assertTrue(discovery.getMongo().get(0).isReachable());
        assertEquals("redis:6379", discovery.getRedis().get(0).getTarget());
        assertTrue(discovery.getRedis().get(0).isAuthRequired());
    }

    @Test
    void probeRedisReportsAuthRequired() {
        redisProbe.result = RedisProbeResult.authRequired(4, "服务器已启用认证，请填写 Redis 密码");
        var dto = service.probeRedis(newRedisCmd("redis", null));
        assertTrue(dto.isReachable());
        assertTrue(dto.isAuthRequired());
    }

    private online.yudream.base.application.installer.cmd.RedisProbeCmd newRedisCmd(String host, String password) {
        online.yudream.base.application.installer.cmd.RedisProbeCmd cmd =
                new online.yudream.base.application.installer.cmd.RedisProbeCmd();
        cmd.setHost(host);
        cmd.setPassword(password);
        return cmd;
    }

    private static class StubMongoProbe implements MongoProbe {
        MongoProbeResult result = MongoProbeResult.ok(3, "7.0.0");

        @Override
        public MongoProbeResult probe(MongoProbeSpec spec) {
            return result;
        }
    }

    private static class StubRedisProbe implements RedisProbe {
        RedisProbeResult result = RedisProbeResult.ok(2, "7.2.4");

        @Override
        public RedisProbeResult probe(RedisProbeSpec spec) {
            return result;
        }
    }

    private static class StubStore implements BootstrapConfigStore {
        BootstrapConfig saved;
        private final AtomicReference<Boolean> existsFlag = new AtomicReference<>(false);

        @Override
        public boolean exists() {
            return Boolean.TRUE.equals(existsFlag.get());
        }

        @Override
        public Path location() {
            return Path.of("/tmp/yudream-bootstrap.properties");
        }

        @Override
        public void save(BootstrapConfig config) {
            this.saved = config;
            this.existsFlag.set(true);
        }
    }
}
