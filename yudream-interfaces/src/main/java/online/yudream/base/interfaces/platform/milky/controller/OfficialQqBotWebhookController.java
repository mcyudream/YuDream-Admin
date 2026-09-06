package online.yudream.base.interfaces.platform.milky.controller;

import lombok.RequiredArgsConstructor;
import online.yudream.base.application.platform.milky.service.OfficialQqBotWebhookAppService;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/public/qqbot")
@RequiredArgsConstructor
public class OfficialQqBotWebhookController {
    private final OfficialQqBotWebhookAppService appService;

    @PostMapping("/{connectionId}/webhook")
    public Object webhook(@PathVariable Long connectionId,
                          @RequestBody String body,
                          @RequestHeader(value = "X-Signature-Ed25519", required = false) String signature,
                          @RequestHeader(value = "X-Signature-Timestamp", required = false) String timestamp) {
        return appService.handle(connectionId, body, signature, timestamp);
    }
}
