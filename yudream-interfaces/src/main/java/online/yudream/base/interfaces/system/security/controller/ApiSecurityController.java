package online.yudream.base.interfaces.system.security.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import online.yudream.base.application.system.security.query.ApiKeyPageQuery;
import online.yudream.base.application.system.security.service.ApiEncryptionAppService;
import online.yudream.base.application.system.security.service.ApiSecurityAppService;
import online.yudream.base.domain.common.PageResult;
import online.yudream.base.domain.system.security.anno.PermissionRegister;
import online.yudream.base.interfaces.common.Result;
import online.yudream.base.interfaces.system.security.assembler.ApiSecurityWebAssembler;
import online.yudream.base.interfaces.system.security.request.ApiKeyCreateRequest;
import online.yudream.base.interfaces.system.security.request.ApiSecurityPolicyUpdateRequest;
import online.yudream.base.interfaces.system.security.res.ApiKeyCreateResultRes;
import online.yudream.base.interfaces.system.security.res.ApiKeyCredentialRes;
import online.yudream.base.interfaces.system.security.res.ApiEncryptionPublicKeyRes;
import online.yudream.base.interfaces.system.security.res.ApiEncryptionStatusRes;
import online.yudream.base.interfaces.system.security.res.ApiSecurityPolicyRes;
import online.yudream.base.interfaces.system.security.support.SecurityPrincipalSupport;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/system/security")
@RequiredArgsConstructor
public class ApiSecurityController {

    private final ApiSecurityAppService apiSecurityAppService;
    private final ApiEncryptionAppService apiEncryptionAppService;

    @GetMapping("/encryption/status")
    public Result<ApiEncryptionStatusRes> encryptionStatus() {
        return Result.ok(ApiSecurityWebAssembler.toRes(apiEncryptionAppService.status()));
    }

    @GetMapping("/encryption/public-key")
    public Result<ApiEncryptionPublicKeyRes> encryptionPublicKey() {
        return Result.ok(ApiSecurityWebAssembler.toRes(apiEncryptionAppService.publicKey()));
    }

    @GetMapping("/policy")
    @PermissionRegister(code = "system:security:view", name = "查看安全中心", module = "系统管理", desc = "查看系统安全策略")
    public Result<ApiSecurityPolicyRes> policy() {
        return Result.ok(ApiSecurityWebAssembler.toRes(apiSecurityAppService.policy()));
    }

    @PutMapping("/policy")
    @PermissionRegister(code = "system:security:edit", name = "编辑安全策略", module = "系统管理", desc = "编辑系统安全策略")
    public Result<ApiSecurityPolicyRes> updatePolicy(@Valid @RequestBody ApiSecurityPolicyUpdateRequest request) {
        return Result.ok(ApiSecurityWebAssembler.toRes(apiSecurityAppService.updatePolicy(ApiSecurityWebAssembler.toCmd(request))));
    }

    @GetMapping("/api-keys")
    @PermissionRegister(code = "system:security:view", name = "查看 API Key", module = "系统管理", desc = "查看系统 API Key")
    public Result<PageResult<ApiKeyCredentialRes>> pageApiKeys(ApiKeyPageQuery query) {
        return Result.ok(ApiSecurityWebAssembler.toPage(apiSecurityAppService.pageApiKeys(
                ApiSecurityWebAssembler.toQuery(query, SecurityPrincipalSupport.current()))));
    }

    @PostMapping("/api-keys")
    @PermissionRegister(code = "system:security:api-key:create", name = "创建 API Key", module = "系统管理", desc = "创建系统 API Key")
    public Result<ApiKeyCreateResultRes> createApiKey(@Valid @RequestBody ApiKeyCreateRequest request) {
        return Result.ok(ApiSecurityWebAssembler.toRes(apiSecurityAppService.createApiKey(
                ApiSecurityWebAssembler.toCmd(request, SecurityPrincipalSupport.current()))));
    }

    @PostMapping("/api-keys/{id}/revoke")
    @PermissionRegister(code = "system:security:api-key:revoke", name = "吊销 API Key", module = "系统管理", desc = "吊销系统 API Key")
    public Result<ApiKeyCredentialRes> revokeApiKey(@PathVariable Long id) {
        return Result.ok(ApiSecurityWebAssembler.toRes(apiSecurityAppService.revokeApiKey(
                ApiSecurityWebAssembler.toRevokeCmd(id, SecurityPrincipalSupport.current()))));
    }
}
