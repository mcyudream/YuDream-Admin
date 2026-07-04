package online.yudream.base.interfaces.system.security.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import online.yudream.base.application.system.security.service.OAuthPasskeyAppService;
import online.yudream.base.domain.system.security.anno.PermissionRegister;
import online.yudream.base.interfaces.common.Result;
import online.yudream.base.interfaces.system.security.assembler.ApiSecurityWebAssembler;
import online.yudream.base.interfaces.system.security.request.OAuthClientSaveRequest;
import online.yudream.base.interfaces.system.security.request.OAuthProviderSaveRequest;
import online.yudream.base.interfaces.system.security.res.OAuthClientCreateResultRes;
import online.yudream.base.interfaces.system.security.res.OAuthClientRes;
import online.yudream.base.interfaces.system.security.res.OAuthProviderRes;
import online.yudream.base.interfaces.system.security.res.PasskeyCredentialRes;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/system/security")
@RequiredArgsConstructor
public class OAuthPasskeyController {

    private final OAuthPasskeyAppService oauthPasskeyAppService;

    @GetMapping("/oauth/clients")
    @PermissionRegister(code = "system:security:oauth:view", name = "查看OAuth客户端", module = "系统管理", desc = "查看 OAuth 服务端客户端")
    public Result<List<OAuthClientRes>> clients() {
        return Result.ok(oauthPasskeyAppService.listClients().stream().map(ApiSecurityWebAssembler::toRes).toList());
    }

    @PostMapping("/oauth/clients")
    @PermissionRegister(code = "system:security:oauth:edit", name = "新增OAuth客户端", module = "系统管理", desc = "新增 OAuth 服务端客户端")
    public Result<OAuthClientCreateResultRes> createClient(@Valid @RequestBody OAuthClientSaveRequest request) {
        return Result.ok(ApiSecurityWebAssembler.toRes(oauthPasskeyAppService.createClient(ApiSecurityWebAssembler.toCmd(request))));
    }

    @PutMapping("/oauth/clients/{id}")
    @PermissionRegister(code = "system:security:oauth:edit", name = "编辑OAuth客户端", module = "系统管理", desc = "编辑 OAuth 服务端客户端")
    public Result<OAuthClientRes> updateClient(@PathVariable Long id, @Valid @RequestBody OAuthClientSaveRequest request) {
        return Result.ok(ApiSecurityWebAssembler.toRes(oauthPasskeyAppService.updateClient(ApiSecurityWebAssembler.toCmd(id, request))));
    }

    @DeleteMapping("/oauth/clients/{id}")
    @PermissionRegister(code = "system:security:oauth:edit", name = "禁用OAuth客户端", module = "系统管理", desc = "禁用 OAuth 服务端客户端")
    public Result<Void> disableClient(@PathVariable Long id) {
        oauthPasskeyAppService.disableClient(id);
        return Result.ok();
    }

    @GetMapping("/oauth/providers")
    @PermissionRegister(code = "system:security:oauth:view", name = "查看OAuth提供商", module = "系统管理", desc = "查看 OAuth 外部登录提供商")
    public Result<List<OAuthProviderRes>> providers() {
        return Result.ok(oauthPasskeyAppService.listProviders().stream().map(ApiSecurityWebAssembler::toRes).toList());
    }

    @PostMapping("/oauth/providers")
    @PermissionRegister(code = "system:security:oauth:edit", name = "保存OAuth提供商", module = "系统管理", desc = "新增 OAuth 外部登录提供商")
    public Result<OAuthProviderRes> createProvider(@Valid @RequestBody OAuthProviderSaveRequest request) {
        return Result.ok(ApiSecurityWebAssembler.toRes(oauthPasskeyAppService.saveProvider(ApiSecurityWebAssembler.toCmd(request))));
    }

    @PutMapping("/oauth/providers/{id}")
    @PermissionRegister(code = "system:security:oauth:edit", name = "编辑OAuth提供商", module = "系统管理", desc = "编辑 OAuth 外部登录提供商")
    public Result<OAuthProviderRes> updateProvider(@PathVariable Long id, @Valid @RequestBody OAuthProviderSaveRequest request) {
        return Result.ok(ApiSecurityWebAssembler.toRes(oauthPasskeyAppService.saveProvider(ApiSecurityWebAssembler.toCmd(id, request))));
    }

    @DeleteMapping("/oauth/providers/{id}")
    @PermissionRegister(code = "system:security:oauth:edit", name = "禁用OAuth提供商", module = "系统管理", desc = "禁用 OAuth 外部登录提供商")
    public Result<Void> disableProvider(@PathVariable Long id) {
        oauthPasskeyAppService.disableProvider(id);
        return Result.ok();
    }

    @GetMapping("/passkeys")
    @PermissionRegister(code = "system:security:passkey:view", name = "查看Passkey", module = "系统管理", desc = "查看用户 Passkey 凭据")
    public Result<List<PasskeyCredentialRes>> passkeys(@RequestParam(required = false) Long userId) {
        return Result.ok(ApiSecurityWebAssembler.toPasskeyResList(
                oauthPasskeyAppService.listPasskeys(ApiSecurityWebAssembler.toPasskeyCredentialQuery(userId))
        ));
    }

    @PostMapping("/passkeys/{id}/revoke")
    @PermissionRegister(code = "system:security:passkey:revoke", name = "吊销Passkey", module = "系统管理", desc = "吊销用户 Passkey 凭据")
    public Result<PasskeyCredentialRes> revokePasskey(@PathVariable Long id) {
        return Result.ok(ApiSecurityWebAssembler.toRes(oauthPasskeyAppService.revokePasskey(ApiSecurityWebAssembler.toPasskeyRevokeCmd(id))));
    }
}
