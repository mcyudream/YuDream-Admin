package online.yudream.base.application.platform.wiki.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import online.yudream.base.application.system.file.service.FileAppService;
import online.yudream.base.domain.platform.wiki.aggregate.WikiSource;
import online.yudream.base.domain.platform.wiki.aggregate.WikiSpace;
import online.yudream.base.domain.platform.wiki.enumerate.WikiCaptionStatus;
import online.yudream.base.domain.platform.wiki.service.WikiVisionCaptionGateway;
import online.yudream.base.domain.platform.wiki.valobj.WikiSourceImage;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Map;

/** 图片描述统一在后台 AI 运行内执行；文件授权仍取原发起用户，禁止以站内 ID 枚举私有文件。 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WikiImageCaptionService {
    private final FileAppService files;
    private final WikiVisionCaptionGateway vision;

    /**
     * 为资料内尚未描述成功的图片生成 caption。
     *
     * @return 本次新增 caption 的图片数量（已描述/失败/跳过的图片不计入）
     */
    public int caption(WikiSource source, WikiSpace space, Long callerId, Map<String, String> config) {
        if (!StringUtils.hasText(space.getVisionProviderCode()) || !StringUtils.hasText(space.getVisionModelCode())) return 0;
        var images = new ArrayList<WikiSourceImage>();
        int captioned = 0;
        for (var image : source.getImages()) {
            if (image.captionStatus() == WikiCaptionStatus.CAPTIONED) {
                images.add(image);
                continue;
            }
            try {
                var file = files.content(image.fileObjectId(), callerId, false);
                byte[] bytes;
                try (InputStream input = file.getInputStream()) { bytes = input.readAllBytes(); }
                String data = "data:" + image.contentType() + ";base64," + Base64.getEncoder().encodeToString(bytes);
                String caption = vision.caption(space.getVisionProviderCode(), space.getVisionModelCode(), config, data);
                images.add(withCaption(image, caption, WikiCaptionStatus.CAPTIONED, space));
                captioned++;
            } catch (RuntimeException | java.io.IOException error) {
                if (Thread.currentThread().isInterrupted()) throw new online.yudream.base.domain.common.exception.BizException("图片描述任务已取消");
                log.warn("资料图片描述失败，sourceId={} imageId={}", source.getId(), image.fileObjectId(), error);
                images.add(withCaption(image, image.caption(), WikiCaptionStatus.FAILED, space));
            }
        }
        source.setImages(images);
        return captioned;
    }

    private WikiSourceImage withCaption(WikiSourceImage image, String caption, WikiCaptionStatus status, WikiSpace space) {
        return new WikiSourceImage(image.fileObjectId(), image.pageNumber(), image.sequence(), caption, status,
                space.getVisionProviderCode(), space.getVisionModelCode(), image.width(), image.height(), image.contentType());
    }
}
