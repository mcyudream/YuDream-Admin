package online.yudream.base.interfaces.platform.render.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import online.yudream.base.application.platform.render.dto.RenderedImageDTO;
import online.yudream.base.application.platform.render.service.MessageRenderAppService;
import online.yudream.base.domain.system.security.anno.PermissionRegister;
import online.yudream.base.interfaces.platform.render.assembler.MessageRenderWebAssembler;
import online.yudream.base.interfaces.platform.render.request.MessageRenderRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/platform/render")
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "yudream.platform.capabilities.message-render", name = "enabled", havingValue = "true")
public class MessageRenderController {
    private final MessageRenderAppService renderAppService;

    @PostMapping
    @PermissionRegister(code = "platform:render:use", name = "渲染消息图片", module = "消息渲染", desc = "将 HTML、Markdown 或 URL 渲染为图片")
    public ResponseEntity<byte[]> render(@Valid @RequestBody MessageRenderRequest request) {
        RenderedImageDTO image = renderAppService.render(MessageRenderWebAssembler.toCmd(request));
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=message-render.png")
                .contentType(MediaType.parseMediaType(image.contentType()))
                .contentLength(image.content().length)
                .body(image.content());
    }
}
