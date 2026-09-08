package online.yudream.base.domain.platform.render.service;

import online.yudream.base.domain.platform.render.model.RenderModels.RenderRequest;
import online.yudream.base.domain.platform.render.model.RenderModels.RenderedImage;
import online.yudream.base.domain.platform.render.model.RenderModels.RenderedPage;

public interface MessageRenderGateway {
    RenderedImage render(RenderRequest request);

    default RenderedPage fetchHtml(String url) {
        throw new UnsupportedOperationException("htmlFromUrl is unavailable");
    }

    boolean healthy();
}
