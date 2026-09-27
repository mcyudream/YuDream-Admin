package online.yudream.base.interfaces.platform.mobile.controller;

import cn.dev33.satoken.stp.StpUtil;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import online.yudream.base.application.platform.mobile.dto.MobileDeviceDTO;
import online.yudream.base.application.platform.mobile.dto.MobileManifestDTO;
import online.yudream.base.application.platform.mobile.query.MobileManifestQuery;
import online.yudream.base.application.platform.mobile.service.MobileAppService;
import online.yudream.base.domain.system.security.anno.PermissionRegister;
import online.yudream.base.interfaces.common.Result;
import online.yudream.base.interfaces.platform.mobile.assembler.MobileWebAssembler;
import online.yudream.base.interfaces.platform.mobile.request.MobileDeviceRegisterRequest;
import online.yudream.base.interfaces.platform.mobile.request.MobileDeviceUnregisterRequest;
import online.yudream.base.interfaces.platform.mobile.res.MobileDeviceRes;
import online.yudream.base.interfaces.platform.mobile.res.MobileManifestRes;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 移动 App 端点：manifest 聚合与设备注册，双 token 鉴权沿用既有 sa-token 体系。
 * <p>
 * 项目闸门关闭（yudream.platform.capabilities.mobile-app.enabled!=true）时本控制器
 * 不注册，不暴露任何移动端点；能力应用闸门由应用层 ensureEnabled 把守。
 */
@RestController
@RequestMapping("/api/mobile")
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "yudream.platform.capabilities.mobile-app", name = "enabled", havingValue = "true")
public class MobileController {

    private final MobileAppService mobileAppService;

    @GetMapping("/manifest")
    public Result<MobileManifestRes> manifest(@RequestParam String platform,
                                              @RequestParam(required = false) String hostVersion,
                                              @RequestParam(required = false) String capabilitySet) {
        StpUtil.checkLogin();
        MobileManifestQuery query = MobileWebAssembler.toManifestQuery(platform, hostVersion, capabilitySet);
        MobileManifestDTO manifest = mobileAppService.manifest(query);
        return Result.ok(MobileWebAssembler.toRes(manifest));
    }

    @PostMapping("/devices/register")
    @PermissionRegister(code = "mobile:device:create", name = "注册移动设备", module = "移动应用",
            desc = "注册当前用户的移动设备并绑定推送通道，同一设备 token 幂等更新")
    public Result<MobileDeviceRes> register(@Valid @RequestBody MobileDeviceRegisterRequest request) {
        MobileDeviceDTO device = mobileAppService.register(StpUtil.getLoginIdAsLong(),
                MobileWebAssembler.toRegisterCmd(request));
        return Result.ok(MobileWebAssembler.toRes(device));
    }

    @PostMapping("/devices/unregister")
    @PermissionRegister(code = "mobile:device:delete", name = "注销移动设备", module = "移动应用",
            desc = "注销当前用户的移动设备注册记录")
    public Result<Void> unregister(@Valid @RequestBody MobileDeviceUnregisterRequest request) {
        mobileAppService.unregister(StpUtil.getLoginIdAsLong(), MobileWebAssembler.toUnregisterCmd(request));
        return Result.ok();
    }

    @GetMapping("/devices")
    @PermissionRegister(code = "mobile:device:view", name = "查看移动设备", module = "移动应用",
            desc = "查看当前用户已注册的移动设备列表")
    public Result<List<MobileDeviceRes>> devices() {
        return Result.ok(MobileWebAssembler.toDeviceResList(mobileAppService.listByUser(StpUtil.getLoginIdAsLong())));
    }
}
