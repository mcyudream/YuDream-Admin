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
import online.yudream.base.domain.platform.wiki.aggregate.WikiNode;
import online.yudream.base.domain.platform.wiki.aggregate.WikiSource;
import online.yudream.base.domain.platform.wiki.aggregate.WikiSpace;
import online.yudream.base.domain.platform.wiki.enumerate.WikiCaptionStatus;
import online.yudream.base.domain.platform.wiki.enumerate.WikiSourceFormat;
import online.yudream.base.domain.platform.wiki.repo.WikiNodeRepo;
import online.yudream.base.domain.platform.wiki.repo.WikiPageVersionRepo;
import online.yudream.base.domain.platform.wiki.repo.WikiSourceRepo;
import online.yudream.base.domain.platform.wiki.repo.WikiSpaceRepo;
import online.yudream.base.domain.platform.wiki.service.WikiRemoteImageFetcher;
import online.yudream.base.domain.platform.wiki.service.WikiWebPageFetcher;
import online.yudream.base.domain.platform.wiki.valobj.WikiRemoteImage;
import online.yudream.base.domain.platform.wiki.valobj.WikiSourceImage;
import online.yudream.base.domain.platform.wiki.valobj.WikiWebPage;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.ObjectProvider;
import online.yudream.base.application.platform.ai.service.AiRunAppService;
import online.yudream.base.application.platform.ai.dto.AiRunDTO;
import online.yudream.base.domain.platform.ai.service.AiUsageExecutionScope;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
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
    /** 站内图片地址：用于资料删除时判断图片是否仍被页面/版本引用。 */
    private static final Pattern FILE_CONTENT_URL = Pattern.compile("/api/files/(\\d+)/content");
    private static final int MAX_MARKDOWN_IMAGES = 10;

    private final CapabilityAppService capabilities;
    private final CapabilityModuleRepo capabilityModuleRepo;
    private final WikiSpaceRepo spaceRepo;
    private final WikiSourceRepo sourceRepo;
    private final WikiNodeRepo nodeRepo;
    private final WikiPageVersionRepo versionRepo;
    private final FileAppService fileAppService;
    private final WikiWebPageFetcher webPageFetcher;
    private final WikiRemoteImageFetcher remoteImageFetcher;
    private final WikiImageCaptionService imageCaptions;
    private final ObjectProvider<AiRunAppService> runs;
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
    public WikiSourceDTO createText(Long spaceId, String folderPath, String title, String content, Long callerId) {
        enabled();
        WikiSpace space = space(spaceId);
        if (!StringUtils.hasText(content)) {
            throw new BizException("资料内容不能为空");
        }
        // 在线 Markdown 编辑的资料同样摄取其中引用的远程图片：下载入库、可生成 caption、重写为站内地址
        MarkdownImageIngest ingest = ingestMarkdownImages(space, content, callerId);
        WikiSource source = WikiSource.text(spaceId, folderPath, title, ingest.markdown(), sha256(ingest.markdown()));
        source.markExtracted(ingest.markdown(), ingest.images());
        WikiSource saved = sourceRepo.save(source);
        ingestAppService.enqueueIngest(spaceId, saved.getId());
        return WikiKnowledgeAssembler.source(saved);
    }

    @Transactional
    public WikiSourceDTO updateText(Long sourceId, String title, String content, Long callerId) {
        enabled();
        WikiSource source = sourceRepo.findById(sourceId).orElseThrow(() -> new BizException("资料不存在"));
        if (!StringUtils.hasText(content)) {
            throw new BizException("资料内容不能为空");
        }
        MarkdownImageIngest ingest = ingestMarkdownImages(space(source.getSpaceId()), content, callerId);
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
        releaseImages(source);
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

    /**
     * 图片资产级联/保留策略：删除不再被本空间任何页面草稿、发布版本或其他资料引用的抽取图片，
     * 避免留下可访问的 orphan URL；仍被引用的图片保留，保证公开页面与历史版本不出现死链。
     */
    private void releaseImages(WikiSource source) {
        List<Long> candidates = (source.getImages() == null ? List.<WikiSourceImage>of() : source.getImages()).stream()
                .map(WikiSourceImage::fileObjectId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
        if (candidates.isEmpty()) {
            return;
        }
        java.util.Set<Long> referenced = referencedImageIds(source, candidates);
        for (Long fileObjectId : candidates) {
            if (referenced.contains(fileObjectId)) {
                continue;
            }
            try {
                fileAppService.delete(fileObjectId);
            }
            catch (Exception exception) {
                log.warn("资料图片清理失败，fileObjectId={}：{}", fileObjectId, exception.getMessage());
            }
        }
    }

    private java.util.Set<Long> referencedImageIds(WikiSource source, List<Long> candidates) {
        java.util.Set<Long> referenced = new java.util.LinkedHashSet<>();
        for (WikiNode node : nodeRepo.findBySpaceId(source.getSpaceId())) {
            collectImageRefs(node.getMarkdownDraft(), candidates, referenced);
            if (node.getPublishedVersionId() != null) {
                versionRepo.findById(node.getPublishedVersionId())
                        .ifPresent(version -> collectImageRefs(version.getMarkdown(), candidates, referenced));
            }
        }
        for (WikiSource other : sourceRepo.findBySpaceId(source.getSpaceId())) {
            if (java.util.Objects.equals(other.getId(), source.getId())) {
                continue;
            }
            collectImageRefs(other.getExtractedText(), candidates, referenced);
            if (other.getImages() != null) {
                other.getImages().forEach(image -> {
                    if (image.fileObjectId() != null && candidates.contains(image.fileObjectId())) {
                        referenced.add(image.fileObjectId());
                    }
                });
            }
        }
        return referenced;
    }

    private void collectImageRefs(String markdown, List<Long> candidates, java.util.Set<Long> referenced) {
        if (!StringUtils.hasText(markdown)) {
            return;
        }
        Matcher matcher = FILE_CONTENT_URL.matcher(markdown);
        while (matcher.find()) {
            try {
                Long fileObjectId = Long.valueOf(matcher.group(1));
                if (candidates.contains(fileObjectId)) {
                    referenced.add(fileObjectId);
                }
            }
            catch (NumberFormatException ignored) {
                // 非数字文件 ID 不参与匹配
            }
        }
    }

    @Transactional
    public WikiSourceDTO captionImages(Long sourceId, Long callerId) {
        enabled();
        WikiSource source = sourceRepo.findById(sourceId).orElseThrow(() -> new BizException("资料不存在"));
        WikiSpace space = space(source.getSpaceId());
        if (!StringUtils.hasText(space.getVisionProviderCode()) || !StringUtils.hasText(space.getVisionModelCode())) {
            throw new BizException("该知识库未配置视觉模型");
        }
        int captioned = imageCaptions.caption(source, space, callerId, aiConfig());
        WikiSource saved = sourceRepo.save(source);
        if (captioned > 0) {
            // caption 属于生成上下文与原文档页的输入：新增描述后必须重新摄入，否则不会进入页面
            saved.markIngestPending();
            saved = sourceRepo.save(saved);
            ingestAppService.enqueueIngest(saved.getSpaceId(), saved.getId());
        }
        return WikiKnowledgeAssembler.source(saved);
    }

    public AiRunDTO startCaptionImages(Long sourceId, Long callerId) {
        enabled();
        var runner = runs.getIfAvailable();
        if (runner == null) throw new BizException("项目未开放 AI 能力");
        sourceRepo.findById(sourceId).orElseThrow(() -> new BizException("资料不存在"));
        return runner.start(callerId, "wiki-caption", "wiki-caption:" + sourceId, emitter -> {
            emitter.input("重新生成资料图片描述");
            try (AiUsageExecutionScope ignored = AiUsageExecutionScope.open(callerId)) {
                return captionImages(sourceId, callerId);
            }
        }, null);
    }

    /**
     * 摄取在线 Markdown 中引用的远程图片：下载入库（图片重写为站内地址），并按知识库视觉配置生成 caption。
     */
    private MarkdownImageIngest ingestMarkdownImages(WikiSpace space, String markdown, Long callerId) {
        if (!StringUtils.hasText(markdown)) {
            return new MarkdownImageIngest(markdown, List.of());
        }
        Matcher matcher = MARKDOWN_IMAGE.matcher(markdown);
        Map<String, String> replacements = new LinkedHashMap<>();
        Set<Long> seenInternalIds = new LinkedHashSet<>();
        List<WikiSourceImage> images = new ArrayList<>();
        boolean captionEnabled = StringUtils.hasText(space.getVisionProviderCode())
                && StringUtils.hasText(space.getVisionModelCode());
        Map<String, String> aiConfig = Map.of();
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
                    WikiSourceImage image = captionStored(fileObjectId, images.size(), space, aiConfig, captionEnabled, callerId);
                    if (image != null) {
                        images.add(image);
                    }
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
                // 在线 Markdown 资料的图片资产也始终私有；公开阅读仅能经已发布页面图片代理获取。
                Long fileObjectId = fileAppService.upload(new java.io.ByteArrayInputStream(remote.content()),
                        remote.fileName(), remote.contentType(), remote.content().length, "wiki-image", callerId, false).getId();
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

    /** 校验站内文件访问权并登记图片，视觉描述留给持久摄入运行。无权限的站内 ID 直接跳过，避免枚举私有文件。 */
    private WikiSourceImage captionStored(Long fileObjectId, int sequence, WikiSpace space,
                                          Map<String, String> aiConfig, boolean captionEnabled, Long callerId) {
        try {
            // 仅允许公开文件或调用方本人文件进入视觉模型，防止站内 id 枚举读取私有文件。
            FileContentDTO content = fileAppService.content(fileObjectId, callerId, false);
            String contentType = StringUtils.hasText(content.getContentType()) ? content.getContentType() : "image/png";
            if (!captionEnabled) {
                closeQuietly(content);
                return new WikiSourceImage(fileObjectId, 0, sequence, null, WikiCaptionStatus.SKIPPED,
                        space.getVisionProviderCode(), space.getVisionModelCode(), 0, 0, contentType);
            }
            closeQuietly(content);
            return new WikiSourceImage(fileObjectId, 0, sequence, null, WikiCaptionStatus.PENDING,
                    space.getVisionProviderCode(), space.getVisionModelCode(), 0, 0, contentType);
        }
        catch (Exception exception) {
            log.warn("站内图片无权访问，已跳过登记 {}：{}", fileObjectId, exception.getMessage());
            return null;
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
        return new WikiSourceImage(fileObjectId, 0, sequence, null, WikiCaptionStatus.PENDING,
                space.getVisionProviderCode(), space.getVisionModelCode(), 0, 0, remote.contentType());
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
