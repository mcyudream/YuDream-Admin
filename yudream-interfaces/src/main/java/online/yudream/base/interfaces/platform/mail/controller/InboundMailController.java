package online.yudream.base.interfaces.platform.mail.controller;

import lombok.RequiredArgsConstructor;
import online.yudream.base.application.platform.mail.query.InboundMailPageQuery;
import online.yudream.base.application.platform.mail.service.InboundMailAppService;
import online.yudream.base.domain.common.PageResult;
import online.yudream.base.domain.system.security.anno.PermissionRegister;
import online.yudream.base.interfaces.common.Result;
import online.yudream.base.interfaces.platform.mail.assembler.InboundMailWebAssembler;
import online.yudream.base.interfaces.platform.mail.res.InboundMailDetailRes;
import online.yudream.base.interfaces.platform.mail.res.InboundMailSummaryRes;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/platform/inbound-mail/messages")
@ConditionalOnProperty(prefix = "yudream.platform.capabilities.inbound-mail", name = "enabled", havingValue = "true")
public class InboundMailController {

    private final InboundMailAppService inboundMailAppService;

    @GetMapping
    @PermissionRegister(code = "platform:inbound-mail:view", name = "查看入站邮件", module = "入站邮箱", desc = "查看入站邮箱邮件列表")
    public Result<PageResult<InboundMailSummaryRes>> page(InboundMailPageQuery query) {
        return Result.ok(InboundMailWebAssembler.toPage(inboundMailAppService.page(query)));
    }

    @GetMapping("/{uid}")
    @PermissionRegister(code = "platform:inbound-mail:view", name = "查看入站邮件详情", module = "入站邮箱", desc = "查看入站邮箱邮件详情")
    public Result<InboundMailDetailRes> detail(@PathVariable long uid) {
        return Result.ok(InboundMailWebAssembler.toRes(inboundMailAppService.detail(uid)));
    }
}
