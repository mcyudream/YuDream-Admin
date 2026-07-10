package online.yudream.base.infra.platform.plugin.service;

import lombok.RequiredArgsConstructor;
import online.yudream.base.application.platform.render.cmd.MessageRenderCmd;
import online.yudream.base.application.platform.render.dto.RenderedImageDTO;
import online.yudream.base.application.platform.render.service.MessageRenderAppService;
import online.yudream.base.domain.platform.render.model.RenderModels.SourceType;
import online.yudream.base.plugin.spi.system.render.PluginRenderService;
import online.yudream.base.plugin.spi.system.render.PluginRenderedImage;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

@Service
@RequiredArgsConstructor
public class PluginRenderFrameworkService implements PluginRenderService {
    private final MessageRenderAppService messageRenderAppService;

    @Override
    public CompletionStage<PluginRenderedImage> html(String html) { return render(SourceType.HTML, html); }

    @Override
    public CompletionStage<PluginRenderedImage> markdown(String markdown) { return render(SourceType.MARKDOWN, markdown); }

    @Override
    public CompletionStage<PluginRenderedImage> url(String url) { return render(SourceType.URL, url); }

    private CompletionStage<PluginRenderedImage> render(SourceType sourceType, String content) {
        return CompletableFuture.supplyAsync(() -> {
            MessageRenderCmd cmd = new MessageRenderCmd();
            cmd.setSourceType(sourceType);
            cmd.setContent(content);
            RenderedImageDTO rendered = messageRenderAppService.render(cmd);
            return new PluginRenderedImage(rendered.contentType(), rendered.content(), rendered.width(), rendered.height());
        });
    }
}
