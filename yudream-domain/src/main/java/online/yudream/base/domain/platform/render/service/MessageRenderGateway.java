package online.yudream.base.domain.platform.render.service;

import online.yudream.base.domain.platform.render.model.RenderModels.RenderRequest;
import online.yudream.base.domain.platform.render.model.RenderModels.RenderedImage;

public interface MessageRenderGateway {
    RenderedImage render(RenderRequest request);
    boolean healthy();
}
