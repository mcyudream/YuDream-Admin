package online.yudream.base.application.platform.wiki.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import online.yudream.base.application.platform.capability.service.CapabilityAppService;
import online.yudream.base.application.platform.wiki.assembler.WikiKnowledgeAssembler;
import online.yudream.base.application.platform.wiki.dto.WikiSourceDTO;
import online.yudream.base.application.system.file.dto.FileContentDTO;
import online.yudream.base.application.system.file.service.FileAppService;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.capability.aggregate.CapabilityModule;
import online.yudream.base.domain.platform.capability.repo.CapabilityModuleRepo;
import online.yudream.base.domain.platform.wiki.aggregate.WikiSource;
import online.yudream.base.domain.platform.wiki.aggregate.WikiSpace;
import online.yudream.base.domain.platform.wiki.enumerate.WikiCaptionStatus;
import online.yudream.base.domain.platform.wiki.enumerate.WikiSourceFormat;
import online.yudream.base.domain.platform.wiki.repo.WikiSourceRepo;
import online.yudream.base.domain.platform.wiki.repo.WikiSpaceRepo;
import online.yudream.base.domain.platform.wiki.service.WikiRemoteImageFetcher;
import online.yudream.base.domain.platform.wiki.service.WikiVisionCaptionGateway;
import online.yudream.base.domain.platform.wiki.service.WikiWebPageFetcher;
import online.yudream.base.domain.platform.wiki.valobj.WikiRemoteImage;
import online.yudream.base.domain.platform.wiki.valobj.WikiSourceImage;
import online.yudream.base.domain.platform.wiki.valobj.WikiWebPage;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 原始资料来源管理：上传、URL/批量导入、删除级联、重新生成图片 caption。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WikiSourceAppService {

    /** Markdown 图片引用：![alt](url "可选标题")；支持单/双引号标题与站内文件地址（含 dev /proxy 前缀） */
    private static final Pattern MARKDOWN_IMAGE = Pattern.compile(
            "!\\[[^\\]]*]\\(\\s*<?(https?://[^\\s)>]+?|/(?:proxy/)?api/files/(\\d+)/content)>?(\\s+(?:\"[^\"]*\"|'[^']*'))?\\s*\\)");
    private static final int MAX_MARKDOWN_IMAGES = 10;

    private final CapabilityAppService capabilities;
    private final CapabilityModuleRepo capabilityModuleRepo;
    private final WikiSpaceRepo spaceRepo;
    private final WikiSourceRepo sourceRepo;
    private final FileAppService fileAppService;
    private final WikiSourceExtractionService extractionService;
    private final WikiWebPageFetcher webPageFetcher;
    private final WikiRemoteImageFetcher remoteImageFetcher;
    private final WikiVisionCaptionGateway visionCaptionGateway;
    private final WikiIngestAppService ingestAppService;

    @Transactional
    public WikiSourceDTO importFile(Long spaceId, String folderPath, InputStream inputStream, String fileName,
                                    String contentType, long size) {
        enabled();
        WikiSpace space = space(spaceId);
        if (inputStream == null || size <= 0) {
            throw new BizException("上传文件不能为空");
        }
        try {
            byte[] bytes = inputStream.readAllBytes();
            String hash = sha256(bytes);
            if (sourceRepo.findByContentHash(spaceId, hash).isPresent()) {
                throw new BizException("资料已存在");
            }
            Long fileObjectId = fileAppService.upload(new java.io.ByteArrayInputStream(bytes), fileName, contentType,
                    size, "wiki-source", null, false).getId();
            WikiSource source = WikiSource.file(spaceId, folderPath, fileName, fileName, contentType, fileObjectId, hash);
            extractionService.extract(source, space, aiConfig());
            WikiSource saved = sourceRepo.save(source);
            ingestAppService.enqueueIngest(spaceId, saved.getId());
            return WikiKnowledgeAssembler.source(saved);
        }
        catch (BizException exception) {
            throw exception;
        }
        catch (Exception exception) {
            throw new BizException("资料导入失败：" + readableMessage(exception));
        }
    }

    @Transactional
    public List<WikiSourceDTO> importUrls(Long spaceId, String folderPath, List<String> urls) {
        enabled();
        space(spaceId);
        if (urls == null || urls.isEmpty()) {
            return List.of();
        }
        List<WikiSourceDTO> imported = new ArrayList<>();
        for (String url : urls) {
            try {
                imported.add(importUrl(spaceId, folderPath, null, url));
            }
            catch (Exception exception) {
                log.warn("URL 导入失败 {}：{}", url, exception.getMessage());
            }
        }
        return imported;
    }

    @Transactional
    public WikiSourceDTO importUrl(Long spaceId, String folderPath, String title, String url) {
        enabled();
        space(spaceId);
        WikiWebPage page = webPageFetcher.fetch(url);
        WikiSource source = WikiSource.url(spaceId, folderPath,
                StringUtils.hasText(title) ? title : page.title(), url, "text/html");
        source.setFormat(WikiSourceFormat.WEB);
        source.setContentHash(sha256(url));
        source.markExtracted(page.content(), List.of());
        WikiSource saved = sourceRepo.save(source);
        ingestAppService.enqueueIngest(spaceId, saved.getId());
        return WikiKnowledgeAssembler.source(saved);
    }

    @Transactional(readOnly = true)
    public List<WikiSourceDTO> list(Long spaceId) {
        enabled();
        return sourceRepo.findBySpaceId(spaceId).stream().map(WikiKnowledgeAssembler::source).toList();
    }

    @Transactional
    public WikiSourceDTO createText(Long spaceId, String folderPath, String title, String content) {
        enabled();
        WikiSpace space = space(spaceId);
        if (!StringUtils.hasText(content)) {
            throw new BizException("资料内容不能为空");
        }
        // 在线 Markdown 编辑的资料同样摄取其中引用的远程图片：下载入库、可生成 caption、重写为站内地址
        MarkdownImageIngest ingest = ingestMarkdownImages(space, content);
        WikiSource source = WikiSource.text(spaceId, folderPath, title, ingest.markdown(), sha256(ingest.markdown()));
        source.markExtracted(ingest.markdown(), ingest.images());
        WikiSource saved = sourceRepo.save(source);
        ingestAppService.enqueueIngest(spaceId, saved.getId());
        return WikiKnowledgeAssembler.source(saved);
    }

    @Transactional
    public WikiSourceDTO updateText(Long sourceId, String title, String content) {
        enabled();
        WikiSource source = sourceRepo.findById(sourceId).orElseThrow(() -> new BizException("资料不存在"));
        if (!StringUtils.hasText(content)) {
            throw new BizException("资料内容不能为空");
        }
        MarkdownImageIngest ingest = ingestMarkdownImages(space(source.getSpaceId()), content);
        source.updateText(title, ingest.markdown(), sha256(ingest.markdown()));
        source.markExtracted(ingest.markdown(), ingest.images());
        WikiSource saved = sourceRepo.save(source);
        ingestAppService.enqueueIngest(saved.getSpaceId(), saved.getId());
        return WikiKnowledgeAssembler.source(saved);
    }

    @Transactional
    public void delete(Long sourceId) {
        enabled();
        WikiSource source = sourceRepo.findById(sourceId).orElseThrow(() -> new BizException("资料不存在"));
        ingestAppService.enqueueCleanup(source.getSpaceId(), sourceId, source.getTitle(), source.displayPath());
        if (source.getFileObjectId() != null) {
            try {
                fileAppService.delete(source.getFileObjectId());
            }
            catch (Exception ignored) {
                // 文件对象删除失败不影响资料来源删除
            }
        }
        sourceRepo.deleteById(sourceId);
    }

    @Transactional
    public WikiSourceDTO captionImages(Long sourceId) {
        enabled();
        WikiSource source = sourceRepo.findById(sourceId).orElseThrow(() -> new BizException("资料不存在"));
        WikiSpace space = space(source.getSpaceId());
        if (!StringUtils.hasText(space.getVisionProviderCode()) || !StringUtils.hasText(space.getVisionModelCode())) {
            throw new BizException("该知识库未配置视觉模型");
        }
        List<WikiSourceImage> images = new ArrayList<>();
        for (WikiSourceImage image : source.getImages()) {
            if (image.captionStatus() == WikiCaptionStatus.CAPTIONED) {
                images.add(image);
                continue;
            }
            images.add(caption(space, image));
        }
        source.setImages(images);
        return WikiKnowledgeAssembler.source(sourceRepo.save(source));
    }

    private WikiSourceImage caption(WikiSpace space, WikiSourceImage image) {
        try {
            FileContentDTO content = fileAppService.content(image.fileObjectId());
            byte[] bytes;
            try (InputStream inputStream = content.getInputStream()) {
                bytes = inputStream.readAllBytes();
            }
            String dataUrl = "data:" + image.contentType() + ";base64," + Base64.getEncoder().encodeToString(bytes);
            String caption = visionCaptionGateway.caption(space.getVisionProviderCode(), space.getVisionModelCode(),
                    aiConfig(), dataUrl);
            return new WikiSourceImage(image.fileObjectId(), image.pageNumber(), image.sequence(), caption,
                    WikiCaptionStatus.CAPTIONED, space.getVisionProviderCode(), space.getVisionModelCode(),
                    image.width(), image.height(), image.contentType());
        }
        catch (Exception exception) {
            log.warn("图片 caption 失败：{}", exception.getMessage());
            return new WikiSourceImage(image.fileObjectId(), image.pageNumber(), image.sequence(), image.caption(),
                    WikiCaptionStatus.FAILED, image.captionProviderCode(), image.captionModelCode(),
                    image.width(), image.height(), image.contentType());
        }
    }

    /**
     * 摄取在线 Markdown 中引用的远程图片：下载入库（图片重写为站内地址），并按知识库视觉配置生成 caption。
     */
    private MarkdownImageIngest ingestMarkdownImages(WikiSpace space, String markdown) {
        if (!StringUtils.hasText(markdown)) {
            return new MarkdownImageIngest(markdown, List.of());
        }
        Matcher matcher = MARKDOWN_IMAGE.matcher(markdown);
        Map<String, String> replacements = new LinkedHashMap<>();
        Set<Long> seenInternalIds = new LinkedHashSet<>();
        List<WikiSourceImage> images = new ArrayList<>();
        boolean captionEnabled = StringUtils.hasText(space.getVisionProviderCode())
                && StringUtils.hasText(space.getVisionModelCode());
        Map<String, String> aiConfig = captionEnabled ? aiConfig() : Map.of();
        while (matcher.find()) {
            if (images.size() >= MAX_MARKDOWN_IMAGES) {
                break;
            }
            String imageUrl = matcher.group(1);
            String internalId = matcher.group(2);
            if (internalId != null) {
                // 站内文件引用：无需下载，登记为资料图片并规范化地址（去掉 dev 代理前缀）
                Long fileObjectId = Long.valueOf(internalId);
                if (seenInternalIds.add(fileObjectId)) {
                    images.add(captionStored(fileObjectId, images.size(), space, aiConfig, captionEnabled));
                }
                if (imageUrl.startsWith("/proxy/") && !replacements.containsKey(imageUrl)) {
                    replacements.put(imageUrl, imageUrl.substring("/proxy".length()));
                }
                continue;
            }
            if (replacements.containsKey(imageUrl)) {
                continue;
            }
            try {
                WikiRemoteImage remote = remoteImageFetcher.fetch(imageUrl);
                Long fileObjectId = fileAppService.upload(new java.io.ByteArrayInputStream(remote.content()),
                        remote.fileName(), remote.contentType(), remote.content().length, "wiki-image", null, true).getId();
                replacements.put(imageUrl, "/api/files/" + fileObjectId + "/content");
                images.add(captionDownloaded(fileObjectId, remote, images.size(), space, aiConfig, captionEnabled));
            }
            catch (Exception exception) {
                log.warn("Markdown 图片摄取失败 {}：{}", imageUrl, exception.getMessage());
            }
        }
        String rewritten = markdown;
        for (Map.Entry<String, String> entry : replacements.entrySet()) {
            rewritten = rewritten.replace(entry.getKey(), entry.getValue());
        }
        return new MarkdownImageIngest(rewritten, images);
    }

    /** 站内已存储图片：登记为资料图片，配置了视觉模型时读取字节生成 caption。 */
    private WikiSourceImage captionStored(Long fileObjectId, int sequence, WikiSpace space,
                                          Map<String, String> aiConfig, boolean captionEnabled) {
        try {
            FileContentDTO content = fileAppService.content(fileObjectId);
            String contentType = StringUtils.hasText(content.getContentType()) ? content.getContentType() : "image/png";
            if (!captionEnabled) {
                closeQuietly(content);
                return new WikiSourceImage(fileObjectId, 0, sequence, null, WikiCaptionStatus.SKIPPED,
                        space.getVisionProviderCode(), space.getVisionModelCode(), 0, 0, contentType);
            }
            byte[] bytes;
            try (InputStream inputStream = content.getInputStream()) {
                bytes = inputStream.readAllBytes();
            }
            String dataUrl = "data:" + contentType + ";base64," + Base64.getEncoder().encodeToString(bytes);
            String caption = visionCaptionGateway.caption(space.getVisionProviderCode(), space.getVisionModelCode(),
                    aiConfig, dataUrl);
            return new WikiSourceImage(fileObjectId, 0, sequence, caption, WikiCaptionStatus.CAPTIONED,
                    space.getVisionProviderCode(), space.getVisionModelCode(), 0, 0, contentType);
        }
        catch (Exception exception) {
            log.warn("站内图片 caption 失败 {}：{}", fileObjectId, exception.getMessage());
            return new WikiSourceImage(fileObjectId, 0, sequence, null, WikiCaptionStatus.FAILED,
                    space.getVisionProviderCode(), space.getVisionModelCode(), 0, 0, "image/png");
        }
    }

    private void closeQuietly(FileContentDTO content) {
        try {
            if (content.getInputStream() != null) {
                content.getInputStream().close();
            }
        }
        catch (Exception ignored) {
            // 忽略关闭失败
        }
    }

    private WikiSourceImage captionDownloaded(Long fileObjectId, WikiRemoteImage remote, int sequence, WikiSpace space,
                                              Map<String, String> aiConfig, boolean captionEnabled) {
        if (!captionEnabled) {
            return new WikiSourceImage(fileObjectId, 0, sequence, null, WikiCaptionStatus.SKIPPED,
                    space.getVisionProviderCode(), space.getVisionModelCode(), 0, 0, remote.contentType());
        }
        try {
            String dataUrl = "data:" + remote.contentType() + ";base64," + Base64.getEncoder().encodeToString(remote.content());
            String caption = visionCaptionGateway.caption(space.getVisionProviderCode(), space.getVisionModelCode(),
                    aiConfig, dataUrl);
            return new WikiSourceImage(fileObjectId, 0, sequence, caption, WikiCaptionStatus.CAPTIONED,
                    space.getVisionProviderCode(), space.getVisionModelCode(), 0, 0, remote.contentType());
        }
        catch (Exception exception) {
            log.warn("图片 caption 失败：{}", exception.getMessage());
            return new WikiSourceImage(fileObjectId, 0, sequence, null, WikiCaptionStatus.FAILED,
                    space.getVisionProviderCode(), space.getVisionModelCode(), 0, 0, remote.contentType());
        }
    }

    private record MarkdownImageIngest(String markdown, List<WikiSourceImage> images) {
    }

    private Map<String, String> aiConfig() {
        return capabilityModuleRepo.findByCode("ai")
                .filter(CapabilityModule::enabled)
                .map(CapabilityModule::getConfig)
                .orElseThrow(() -> new BizException("AI 能力未启用"));
    }

    private WikiSpace space(Long spaceId) {
        return spaceRepo.findById(spaceId).orElseThrow(() -> new BizException("知识库不存在"));
    }

    private String sha256(byte[] bytes) {
        try {
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        }
        catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private String sha256(String value) {
        return sha256((value == null ? "" : value).getBytes(StandardCharsets.UTF_8));
    }

    private String readableMessage(Exception exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank() ? exception.getClass().getSimpleName() : message;
    }

    private void enabled() {
        capabilities.ensureEnabled("wiki", "Wiki 知识库");
    }
}
