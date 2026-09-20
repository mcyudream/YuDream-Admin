package online.yudream.base.interfaces.installer.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import online.yudream.base.application.installer.dto.InstallerApplyResultDTO;
import online.yudream.base.application.installer.dto.InstallerStatusDTO;
import online.yudream.base.application.installer.dto.MiddlewareDiscoveryDTO;
import online.yudream.base.application.installer.dto.MiddlewareProbeDTO;
import online.yudream.base.application.installer.service.InstallerAppService;
import online.yudream.base.interfaces.common.Result;
import online.yudream.base.interfaces.installer.assembler.InstallerWebAssembler;
import online.yudream.base.interfaces.installer.request.InstallerApplyRequest;
import online.yudream.base.interfaces.installer.request.MongoProbeRequest;
import online.yudream.base.interfaces.installer.request.RedisProbeRequest;
import online.yudream.base.interfaces.installer.res.InstallerApplyResultRes;
import online.yudream.base.interfaces.installer.res.InstallerStatusRes;
import online.yudream.base.interfaces.installer.res.MiddlewareDiscoveryRes;
import online.yudream.base.interfaces.installer.res.MiddlewareProbeRes;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 安装向导接口：仅存在于安装器模式（无引导配置且未预置数据库配置），
 * 正常启动时该控制器不会注册，无需鉴权注解。
 */
@RestController
@RequestMapping("/api/installer")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "yudream.bootstrap.installer", havingValue = "true")
public class InstallerController {

    private final InstallerAppService installerAppService;

    @GetMapping("/status")
    public Result<InstallerStatusRes> status() {
        return Result.ok(InstallerWebAssembler.toRes(installerAppService.status()));
    }

    /**
     * 中间件自动发现：探测 compose 惯用服务名与部署提示地址，供向导预填表单。
     */
    @PostMapping("/discover")
    public Result<MiddlewareDiscoveryRes> discover() {
        return Result.ok(InstallerWebAssembler.toRes(installerAppService.discover()));
    }

    @PostMapping("/probe/mongo")
    public Result<MiddlewareProbeRes> probeMongo(@Valid @RequestBody MongoProbeRequest request) {
        return Result.ok(InstallerWebAssembler.toRes(installerAppService.probeMongo(
                InstallerWebAssembler.toCmd(request))));
    }

    @PostMapping("/probe/redis")
    public Result<MiddlewareProbeRes> probeRedis(@Valid @RequestBody RedisProbeRequest request) {
        return Result.ok(InstallerWebAssembler.toRes(installerAppService.probeRedis(
                InstallerWebAssembler.toCmd(request))));
    }

    /**
     * 写入引导配置并触发自动重启；容器场景下由重启策略拉起进入正常模式。
     */
    @PostMapping("/apply")
    public Result<InstallerApplyResultRes> apply(@Valid @RequestBody InstallerApplyRequest request) {
        InstallerApplyResultDTO result = installerAppService.apply(
                InstallerWebAssembler.toCmd(request));
        return Result.ok(InstallerWebAssembler.toRes(result));
    }
}
