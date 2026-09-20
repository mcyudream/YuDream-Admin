package online.yudream.base.application.installer.assembler;

import online.yudream.base.application.installer.cmd.InstallerApplyCmd;
import online.yudream.base.application.installer.cmd.MongoProbeCmd;
import online.yudream.base.application.installer.cmd.RedisProbeCmd;
import online.yudream.base.application.installer.dto.InstallerApplyResultDTO;
import online.yudream.base.application.installer.dto.InstallerStatusDTO;
import online.yudream.base.application.installer.dto.MiddlewareDiscoveryDTO;
import online.yudream.base.application.installer.dto.MiddlewareProbeDTO;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.installer.valobj.BootstrapConfig;
import online.yudream.base.domain.installer.valobj.MongoProbeResult;
import online.yudream.base.domain.installer.valobj.MongoProbeSpec;
import online.yudream.base.domain.installer.valobj.RedisProbeResult;
import online.yudream.base.domain.installer.valobj.RedisProbeSpec;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Base64;
import java.util.List;

/**
 * 安装器应用装配：cmd 与探测值对象、值对象与 DTO 之间的转换。
 */
@Component
public class InstallerAssembler {

    public MongoProbeSpec toSpec(MongoProbeCmd cmd) {
        return new MongoProbeSpec(cmd == null ? null : cmd.getUri());
    }

    public RedisProbeSpec toSpec(RedisProbeCmd cmd) {
        if (cmd == null) {
            return new RedisProbeSpec(null, null, null, null, null);
        }
        return new RedisProbeSpec(cmd.getHost(), cmd.getPort(), cmd.getPassword(), cmd.getDatabase(), cmd.getSsl());
    }

    public MiddlewareProbeDTO toDTO(String target, MongoProbeResult result) {
        return MiddlewareProbeDTO.builder()
                .kind("MONGO")
                .target(target)
                .reachable(result.reachable())
                .authRequired(result.authRequired())
                .authFailed(result.authFailed())
                .version(result.version())
                .latencyMs(result.latencyMs())
                .message(result.message())
                .build();
    }

    public MiddlewareProbeDTO toDTO(String target, RedisProbeResult result) {
        return MiddlewareProbeDTO.builder()
                .kind("REDIS")
                .target(target)
                .reachable(result.reachable())
                .authRequired(result.authRequired())
                .authFailed(result.authFailed())
                .version(result.version())
                .latencyMs(result.latencyMs())
                .message(result.message())
                .build();
    }

    public MiddlewareDiscoveryDTO toDiscoveryDTO(List<MiddlewareProbeDTO> mongo, List<MiddlewareProbeDTO> redis) {
        return MiddlewareDiscoveryDTO.builder().mongo(mongo).redis(redis).build();
    }

    public InstallerStatusDTO toStatusDTO(boolean installerMode, String bootstrapFileLocation, boolean dockerDeployment,
                                          String javaVersion, boolean setupTokenRequired) {
        return InstallerStatusDTO.builder()
                .installerMode(installerMode)
                .bootstrapFileLocation(bootstrapFileLocation)
                .dockerDeployment(dockerDeployment)
                .javaVersion(javaVersion)
                .setupTokenRequired(setupTokenRequired)
                .build();
    }

    public InstallerApplyResultDTO toApplyResultDTO(String bootstrapFileLocation, long restartDelayMs) {
        return InstallerApplyResultDTO.builder()
                .restarting(true)
                .bootstrapFileLocation(bootstrapFileLocation)
                .restartDelayMs(restartDelayMs)
                .build();
    }

    public BootstrapConfig toConfig(InstallerApplyCmd cmd, String credentialKey) {
        return new BootstrapConfig(
                cmd.getMongoUri(),
                cmd.getRedisHost(),
                cmd.getRedisPort(),
                cmd.getRedisPassword(),
                cmd.getRedisDatabase(),
                cmd.getRedisSsl(),
                credentialKey,
                cmd.getSnowflakeDataCenterId(),
                cmd.getSnowflakeMachineId()
        );
    }

    /**
     * 校验并归一化主密钥：留空时生成 Base64 32 字节随机密钥。
     */
    public String normalizeCredentialKey(String credentialKey) {
        if (!StringUtils.hasText(credentialKey)) {
            byte[] key = new byte[32];
            new java.security.SecureRandom().nextBytes(key);
            return Base64.getEncoder().encodeToString(key);
        }
        try {
            byte[] decoded = Base64.getDecoder().decode(credentialKey.trim());
            if (decoded.length != 32) {
                throw new BizException("主密钥必须是 Base64 编码且解码后恰为 32 字节的 AES-256 密钥");
            }
            return credentialKey.trim();
        } catch (IllegalArgumentException e) {
            throw new BizException("主密钥必须是合法的 Base64 编码");
        }
    }
}
