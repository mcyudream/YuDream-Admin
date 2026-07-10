package online.yudream.base.interfaces.platform.satori.controller;

import lombok.RequiredArgsConstructor;
import online.yudream.base.application.platform.satori.service.SatoriEventAppService;
import online.yudream.base.interfaces.common.Result;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Passive Satori endpoint. Authentication is reverse-checked against this connection's token. */
@RestController
@RequestMapping("/api/platform/satori/webhooks")
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "yudream.platform.capabilities.satori", name = "enabled", havingValue = "true")
public class SatoriWebhookController {
    private final SatoriEventAppService eventAppService;

    @PostMapping("/{connectionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Result<Void> receive(@PathVariable Long connectionId,
                                @RequestHeader(value = "Authorization", required = false) String authorization,
                                @RequestHeader(value = "Satori-Opcode", required = false) String opcode,
                                @RequestBody String body) {
        eventAppService.acceptWebhook(connectionId, authorization, opcode, body);
        return Result.ok();
    }
}
