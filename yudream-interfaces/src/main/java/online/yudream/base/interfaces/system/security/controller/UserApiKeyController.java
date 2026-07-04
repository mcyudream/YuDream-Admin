package online.yudream.base.interfaces.system.security.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import online.yudream.base.application.system.security.query.ApiKeyPageQuery;
import online.yudream.base.application.system.security.service.ApiSecurityAppService;
import online.yudream.base.domain.common.PageResult;
import online.yudream.base.interfaces.common.Result;
import online.yudream.base.interfaces.system.security.assembler.ApiSecurityWebAssembler;
import online.yudream.base.interfaces.system.security.request.ApiKeyCreateRequest;
import online.yudream.base.interfaces.system.security.res.ApiKeyCreateResultRes;
import online.yudream.base.interfaces.system.security.res.ApiKeyCredentialRes;
import online.yudream.base.interfaces.system.security.support.SecurityPrincipalSupport;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/user/me/api-keys")
@RequiredArgsConstructor
public class UserApiKeyController {

    private final ApiSecurityAppService apiSecurityAppService;

    @GetMapping
    public Result<PageResult<ApiKeyCredentialRes>> page(ApiKeyPageQuery query) {
        return Result.ok(ApiSecurityWebAssembler.toPage(apiSecurityAppService.pageApiKeys(
                ApiSecurityWebAssembler.toSelfQuery(query, SecurityPrincipalSupport.current()))));
    }

    @PostMapping
    public Result<ApiKeyCreateResultRes> create(@Valid @RequestBody ApiKeyCreateRequest request) {
        return Result.ok(ApiSecurityWebAssembler.toRes(apiSecurityAppService.createApiKey(
                ApiSecurityWebAssembler.toCmd(request, SecurityPrincipalSupport.current()))));
    }

    @PostMapping("/{id}/revoke")
    public Result<ApiKeyCredentialRes> revoke(@PathVariable Long id) {
        return Result.ok(ApiSecurityWebAssembler.toRes(apiSecurityAppService.revokeApiKey(
                ApiSecurityWebAssembler.toSelfRevokeCmd(id, SecurityPrincipalSupport.current()))));
    }
}
