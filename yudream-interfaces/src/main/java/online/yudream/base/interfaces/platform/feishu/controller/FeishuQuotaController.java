package online.yudream.base.interfaces.platform.feishu.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import online.yudream.base.application.platform.feishu.service.FeishuQuotaAppService;
import online.yudream.base.domain.system.security.anno.PermissionRegister;
import online.yudream.base.interfaces.common.Result;
import online.yudream.base.interfaces.platform.feishu.assembler.FeishuWebAssembler;
import online.yudream.base.interfaces.platform.feishu.request.FeishuQuotaConfigRequest;
import online.yudream.base.interfaces.platform.feishu.res.FeishuQuotaConfigRes;
import online.yudream.base.interfaces.platform.feishu.res.FeishuQuotaRes;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 飞书调用额度接口：当前用户额度查询 + 管理端上限配置（每用户分钟/日额度，非角色档位）。
 */
@RestController
@RequestMapping("/api/platform/feishu/quota")
@ConditionalOnProperty(prefix = "yudream.platform.capabilities.feishu", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class FeishuQuotaController {

    private final FeishuQuotaAppService feishuQuotaAppService;

    @GetMapping("/me")
    @PermissionRegister(code = "platform:feishu:view", name = "查看飞书调用额度", module = "平台能力", desc = "查看当前用户飞书云文档分钟/日调用额度")
    public Result<FeishuQuotaRes> me() {
        return Result.ok(FeishuWebAssembler.toRes(feishuQuotaAppService.me()));
    }

    @GetMapping("/config")
    @PermissionRegister(code = "platform:feishu:view", name = "查看飞书额度配置", module = "平台能力", desc = "查看飞书调用额度上限配置")
    public Result<FeishuQuotaConfigRes> config() {
        return Result.ok(FeishuWebAssembler.toRes(feishuQuotaAppService.config()));
    }

    @PutMapping("/config")
    @PermissionRegister(code = "platform:feishu:config", name = "配置飞书调用额度", module = "平台能力", desc = "调整每用户飞书调用分钟/日上限，留空保持当前值")
    public Result<FeishuQuotaConfigRes> updateConfig(@Valid @RequestBody FeishuQuotaConfigRequest request) {
        return Result.ok(FeishuWebAssembler.toRes(feishuQuotaAppService.updateConfig(
                FeishuWebAssembler.quotaConfigCmd(request))));
    }
}
