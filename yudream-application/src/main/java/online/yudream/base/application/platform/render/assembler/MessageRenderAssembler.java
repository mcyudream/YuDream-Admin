package online.yudream.base.application.platform.render.assembler;

import online.yudream.base.application.platform.render.cmd.MessageRenderCmd;
import online.yudream.base.application.platform.render.dto.RenderedImageDTO;
import online.yudream.base.domain.platform.render.model.RenderModels.RenderRequest;
import online.yudream.base.domain.platform.render.model.RenderModels.RenderedImage;

public final class MessageRenderAssembler {
    private MessageRenderAssembler() {
    }

    public static RenderRequest toRequest(MessageRenderCmd cmd) {
        return new RenderRequest(cmd.getSourceType(), cmd.getContent(), cmd.getWidth(), cmd.getMaxHeight(),
                cmd.getTransparent(), cmd.getFormat(), cmd.getOptions());
    }

    public static RenderedImageDTO toDTO(RenderedImage image) {
        return new RenderedImageDTO(image.contentType(), image.content(), image.width(), image.height());
    }
}
