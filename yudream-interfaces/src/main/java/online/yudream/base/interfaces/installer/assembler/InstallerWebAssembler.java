package online.yudream.base.interfaces.installer.assembler;

import online.yudream.base.application.installer.cmd.InstallerApplyCmd;
import online.yudream.base.application.installer.cmd.MongoProbeCmd;
import online.yudream.base.application.installer.cmd.RedisProbeCmd;
import online.yudream.base.application.installer.dto.InstallerApplyResultDTO;
import online.yudream.base.application.installer.dto.InstallerStatusDTO;
import online.yudream.base.application.installer.dto.MiddlewareDiscoveryDTO;
import online.yudream.base.application.installer.dto.MiddlewareProbeDTO;
import online.yudream.base.interfaces.installer.request.InstallerApplyRequest;
import online.yudream.base.interfaces.installer.request.MongoProbeRequest;
import online.yudream.base.interfaces.installer.request.RedisProbeRequest;
import online.yudream.base.interfaces.installer.res.InstallerApplyResultRes;
import online.yudream.base.interfaces.installer.res.InstallerStatusRes;
import online.yudream.base.interfaces.installer.res.MiddlewareDiscoveryRes;
import online.yudream.base.interfaces.installer.res.MiddlewareProbeRes;

import java.util.List;

/**
 * 安装器接口装配：request -> cmd、DTO -> res。
 */
public final class InstallerWebAssembler {

    private InstallerWebAssembler() {
    }

    public static MongoProbeCmd toCmd(MongoProbeRequest request) {
        MongoProbeCmd cmd = new MongoProbeCmd();
        cmd.setUri(request.getUri());
        return cmd;
    }

    public static RedisProbeCmd toCmd(RedisProbeRequest request) {
        RedisProbeCmd cmd = new RedisProbeCmd();
        cmd.setHost(request.getHost());
        cmd.setPort(request.getPort());
        cmd.setPassword(request.getPassword());
        cmd.setDatabase(request.getDatabase());
        cmd.setSsl(request.getSsl());
        return cmd;
    }

    public static InstallerApplyCmd toCmd(InstallerApplyRequest request) {
        InstallerApplyCmd cmd = new InstallerApplyCmd();
        cmd.setMongoUri(request.getMongoUri());
        cmd.setRedisHost(request.getRedisHost());
        cmd.setRedisPort(request.getRedisPort());
        cmd.setRedisPassword(request.getRedisPassword());
        cmd.setRedisDatabase(request.getRedisDatabase());
        cmd.setRedisSsl(request.getRedisSsl());
        cmd.setCredentialKey(request.getCredentialKey());
        cmd.setSnowflakeDataCenterId(request.getSnowflakeDataCenterId());
        cmd.setSnowflakeMachineId(request.getSnowflakeMachineId());
        cmd.setSetupToken(request.getSetupToken());
        return cmd;
    }

    public static InstallerStatusRes toRes(InstallerStatusDTO dto) {
        return InstallerStatusRes.builder()
                .installerMode(dto.isInstallerMode())
                .bootstrapFileLocation(dto.getBootstrapFileLocation())
                .dockerDeployment(dto.isDockerDeployment())
                .javaVersion(dto.getJavaVersion())
                .setupTokenRequired(dto.isSetupTokenRequired())
                .build();
    }

    public static MiddlewareProbeRes toRes(MiddlewareProbeDTO dto) {
        return MiddlewareProbeRes.builder()
                .target(dto.getTarget())
                .kind(dto.getKind())
                .reachable(dto.isReachable())
                .authRequired(dto.isAuthRequired())
                .authFailed(dto.isAuthFailed())
                .version(dto.getVersion())
                .latencyMs(dto.getLatencyMs())
                .message(dto.getMessage())
                .build();
    }

    public static MiddlewareDiscoveryRes toRes(MiddlewareDiscoveryDTO dto) {
        List<MiddlewareProbeRes> mongo = dto.getMongo() == null
                ? List.of() : dto.getMongo().stream().map(InstallerWebAssembler::toRes).toList();
        List<MiddlewareProbeRes> redis = dto.getRedis() == null
                ? List.of() : dto.getRedis().stream().map(InstallerWebAssembler::toRes).toList();
        return MiddlewareDiscoveryRes.builder().mongo(mongo).redis(redis).build();
    }

    public static InstallerApplyResultRes toRes(InstallerApplyResultDTO dto) {
        return InstallerApplyResultRes.builder()
                .restarting(dto.isRestarting())
                .bootstrapFileLocation(dto.getBootstrapFileLocation())
                .restartDelayMs(dto.getRestartDelayMs())
                .build();
    }
}
