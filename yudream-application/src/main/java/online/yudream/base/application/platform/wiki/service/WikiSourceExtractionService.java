package online.yudream.base.application.platform.wiki.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import online.yudream.base.application.system.file.dto.FileContentDTO;
import online.yudream.base.application.system.file.service.FileAppService;
import online.yudream.base.domain.platform.document.service.DocumentTextExtractor;
import online.yudream.base.domain.platform.document.valobj.DocumentSource;
import online.yudream.base.domain.platform.wiki.aggregate.WikiSource;
import online.yudream.base.domain.platform.wiki.aggregate.WikiSpace;
import online.yudream.base.domain.platform.wiki.enumerate.WikiCaptionStatus;
import online.yudream.base.domain.platform.wiki.enumerate.WikiSourceKind;
import online.yudream.base.domain.platform.wiki.enumerate.WikiSourceFormat;
import online.yudream.base.domain.platform.wiki.service.WikiDocumentImageExtractor;
import online.yudream.base.domain.platform.wiki.service.WikiVisionCaptionGateway;
import online.yudream.base.domain.platform.wiki.valobj.WikiExtractedImage;
import online.yudream.base.domain.platform.wiki.valobj.WikiSourceImage;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.nio.charset.StandardCharsets;

/**
 * 原始资料抽取编排：文本抽取 + PDF 抽图 + 视觉 caption。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WikiSourceExtractionService {

    private static final int MAX_IMAGES_PER_SOURCE = 30;

    private final FileAppService fileAppService;
    private final DocumentTextExtractor documentTextExtractor;
    private final WikiDocumentImageExtractor imageExtractor;
    private final WikiVisionCaptionGateway visionCaptionGateway;

    public void extract(WikiSource source, WikiSpace space, Map<String, String> aiConfig) {
        if (source.getKind() != WikiSourceKind.FILE) {
            source.skipExtraction("仅文件资料需要抽取");
            return;
        }
        try {
            byte[] bytes = readFile(source.getFileObjectId());
            if (source.getFormat() == WikiSourceFormat.MARKDOWN || source.getFormat() == WikiSourceFormat.TEXT) {
                source.markExtracted(new String(bytes, StandardCharsets.UTF_8), List.of());
                return;
            }
            DocumentSource document = DocumentSource.base64(
                    Base64.getEncoder().encodeToString(bytes), source.getMimeType(), source.getFileName());
            String text;
            try {
                text = documentTextExtractor.extract(document);
            }
            catch (Exception exception) {
                source.failExtraction(exception.getMessage());
                return;
            }
            List<WikiExtractedImage> extracted;
            try {
                extracted = imageExtractor.extractImages(document);
            }
            catch (Exception exception) {
                log.warn("资料来源 {} 抽图失败：{}", source.getTitle(), exception.getMessage());
                extracted = List.of();
            }
            source.markExtracted(text, buildImages(source, space, aiConfig, extracted));
        }
        catch (Exception exception) {
            source.failExtraction(exception.getMessage());
        }
    }

    private List<WikiSourceImage> buildImages(WikiSource source, WikiSpace space, Map<String, String> aiConfig,
                                              List<WikiExtractedImage> extracted) {
        if (extracted == null || extracted.isEmpty()) {
            return List.of();
        }
        boolean captionEnabled = StringUtils.hasText(space.getVisionProviderCode())
                && StringUtils.hasText(space.getVisionModelCode());
        List<WikiSourceImage> images = new ArrayList<>();
        int sequence = 0;
        for (WikiExtractedImage image : extracted) {
            if (sequence >= MAX_IMAGES_PER_SOURCE) {
                break;
            }
            try {
                Long fileObjectId = fileAppService.upload(new ByteArrayInputStream(image.content()), image.fileName(),
                        image.contentType(), image.content().length, "wiki-image", null, true).getId();
                images.add(caption(fileObjectId, image, sequence++, space, aiConfig, captionEnabled));
            }
            catch (Exception exception) {
                log.warn("资料来源 {} 第 {} 张图片处理失败：{}", source.getTitle(), image.pageNumber(), exception.getMessage());
            }
        }
        return images;
    }

    private WikiSourceImage caption(Long fileObjectId, WikiExtractedImage image, int sequence, WikiSpace space,
                                    Map<String, String> aiConfig, boolean captionEnabled) {
        if (!captionEnabled) {
            return new WikiSourceImage(fileObjectId, image.pageNumber(), sequence, null, WikiCaptionStatus.SKIPPED,
                    space.getVisionProviderCode(), space.getVisionModelCode(), image.width(), image.height(), image.contentType());
        }
        try {
            String dataUrl = "data:" + image.contentType() + ";base64,"
                    + Base64.getEncoder().encodeToString(image.content());
            String caption = visionCaptionGateway.caption(space.getVisionProviderCode(), space.getVisionModelCode(),
                    aiConfig, dataUrl);
            return new WikiSourceImage(fileObjectId, image.pageNumber(), sequence, caption, WikiCaptionStatus.CAPTIONED,
                    space.getVisionProviderCode(), space.getVisionModelCode(), image.width(), image.height(), image.contentType());
        }
        catch (Exception exception) {
            log.warn("图片 caption 失败：{}", exception.getMessage());
            return new WikiSourceImage(fileObjectId, image.pageNumber(), sequence, null, WikiCaptionStatus.FAILED,
                    space.getVisionProviderCode(), space.getVisionModelCode(), image.width(), image.height(), image.contentType());
        }
    }

    private byte[] readFile(Long fileObjectId) throws IOException {
        if (fileObjectId == null) {
            throw new IllegalArgumentException("资料文件未上传");
        }
        FileContentDTO content = fileAppService.content(fileObjectId);
        try (InputStream inputStream = content.getInputStream()) {
            return inputStream.readAllBytes();
        }
    }
}
