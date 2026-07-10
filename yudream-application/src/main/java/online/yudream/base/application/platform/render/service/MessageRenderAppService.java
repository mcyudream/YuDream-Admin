package online.yudream.base.application.platform.render.service;

import lombok.RequiredArgsConstructor;
import online.yudream.base.application.platform.capability.service.CapabilityAppService;
import online.yudream.base.application.platform.render.assembler.MessageRenderAssembler;
import online.yudream.base.application.platform.render.cmd.MessageRenderCmd;
import online.yudream.base.application.platform.render.dto.RenderedImageDTO;
import online.yudream.base.domain.platform.render.service.MessageRenderGateway;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MessageRenderAppService {
    public static final String CAPABILITY_CODE = "message-render";
    private final CapabilityAppService capabilityAppService;
    private final MessageRenderGateway renderGateway;

    public RenderedImageDTO render(MessageRenderCmd cmd) {
        capabilityAppService.ensureEnabled(CAPABILITY_CODE, "消息渲染");
        return MessageRenderAssembler.toDTO(renderGateway.render(MessageRenderAssembler.toRequest(cmd)));
    }

    public boolean healthy() {
        capabilityAppService.ensureEnabled(CAPABILITY_CODE, "消息渲染");
        return renderGateway.healthy();
    }
}
