package online.yudream.base.interfaces.platform.docs.controller;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import online.yudream.base.application.platform.docs.service.ApiDocAppService;
import online.yudream.base.domain.system.security.anno.PermissionRegister;
import online.yudream.base.interfaces.common.Result;
import online.yudream.base.interfaces.platform.docs.assembler.ApiDocWebAssembler;
import online.yudream.base.interfaces.platform.docs.request.ApiDocSettingsUpdateRequest;
import online.yudream.base.interfaces.platform.docs.res.ApiDocAccessTicketRes;
import online.yudream.base.interfaces.platform.docs.res.ApiDocSettingsRes;
import online.yudream.base.interfaces.platform.docs.service.ApiDocAccessTicketService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/platform/docs")
@RequiredArgsConstructor
public class ApiDocController {

    private final ApiDocAppService apiDocAppService;
    private final ApiDocAccessTicketService apiDocAccessTicketService;

    @GetMapping("/settings")
    @PermissionRegister(code = "platform:docs:view", name = "查看API文档", module = "平台能力", desc = "查看 API 文档配置")
    public Result<ApiDocSettingsRes> settings() {
        return Result.ok(ApiDocWebAssembler.toRes(apiDocAppService.settings()));
    }

    @GetMapping("/access-ticket")
    @PermissionRegister(code = "platform:docs:view", name = "查看API文档访问票据", module = "平台能力", desc = "生成 Swagger UI 和 OpenAPI 访问票据")
    public Result<ApiDocAccessTicketRes> accessTicket(HttpServletResponse response) {
        ApiDocAccessTicketService.Ticket ticket = apiDocAccessTicketService.issue();
        ResponseCookie cookie = ResponseCookie.from(ApiDocAccessTicketService.COOKIE_NAME, ticket.token())
                .httpOnly(true)
                .sameSite("Lax")
                .path("/")
                .maxAge(ticket.expiresIn())
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
        return Result.ok(ApiDocAccessTicketRes.builder()
                .ticket(ticket.token())
                .expiresIn(ticket.expiresIn())
                .build());
    }

    @PutMapping("/settings")
    @PermissionRegister(code = "platform:docs:config", name = "配置API文档", module = "平台能力", desc = "配置 API 文档开关与入口")
    public Result<ApiDocSettingsRes> update(@Valid @RequestBody ApiDocSettingsUpdateRequest request) {
        return Result.ok(ApiDocWebAssembler.toRes(apiDocAppService.update(ApiDocWebAssembler.toCmd(request))));
    }
}
