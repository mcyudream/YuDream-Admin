package online.yudream.base.application.platform.wiki.service;

import lombok.RequiredArgsConstructor;
import online.yudream.base.application.platform.capability.service.CapabilityAppService;
import online.yudream.base.application.system.file.dto.FileContentDTO;
import online.yudream.base.application.system.file.dto.FileObjectDTO;
import online.yudream.base.application.system.file.service.FileAppService;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.wiki.aggregate.WikiNode;
import online.yudream.base.domain.platform.wiki.aggregate.WikiPageVersion;
import online.yudream.base.domain.platform.wiki.aggregate.WikiSource;
import online.yudream.base.domain.platform.wiki.aggregate.WikiSpace;
import online.yudream.base.domain.platform.wiki.enumerate.WikiNodeType;
import online.yudream.base.domain.platform.wiki.repo.WikiNodeRepo;
import online.yudream.base.domain.platform.wiki.repo.WikiPageVersionRepo;
import online.yudream.base.domain.platform.wiki.repo.WikiSourceRepo;
import online.yudream.base.domain.platform.wiki.repo.WikiSpaceRepo;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.regex.Pattern;

/**
 * 公开 Wiki 图片代理：按“空间已公开 + 节点已发布 + 文件被该发布版本正文引用”放行图片流，
 * 使公开页面不再依赖文件级 {@code publicAccess} 标记（抽取/草稿资产可以保持非公开）。
 *
 * <p>该端点只输出 image/* 内容，且不提供任何列表/枚举能力：未发布的资料图片、草稿图片、
 * 未被任何已发布正文引用的文件在此一律拒绝。发布被撤回（节点取消发布/空间关闭公开阅读）后，
 * 同一路径立即拒绝，不依赖文件级标记变更。
 */
@Service
@RequiredArgsConstructor
public class WikiPublicImageAppService {

    /** 站内图片地址：兼容历史 dev 代理前缀；只用于判断发布正文是否引用该文件。 */
    private static final Pattern FILE_CONTENT_REF = Pattern.compile("/api/files/(\\d+)/content");
    private static final Pattern FILE_CONTENT_URL = Pattern.compile("/(?:proxy/)?api/files/(\\d+)/content");

    /** 判断 Markdown 是否引用指定站内文件（公开图片代理与公开渲染共用同一判定）。 */
    public static boolean referencesFile(String markdown, Long fileId) {
        if (markdown == null || markdown.isBlank() || fileId == null) {
            return false;
        }
        var matcher = FILE_CONTENT_REF.matcher(markdown);
        String expected = String.valueOf(fileId);
        while (matcher.find()) {
            if (expected.equals(matcher.group(1))) {
                return true;
            }
        }
        return false;
    }

    /** 从站内文件地址中提取文件 ID；非站内文件地址返回 null（远程图片不属于平台资产）。 */
    public static Long fileObjectId(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        var matcher = FILE_CONTENT_URL.matcher(url);
        if (!matcher.find()) {
            return null;
        }
        try {
            return Long.valueOf(matcher.group(1));
        }
        catch (NumberFormatException ignored) {
            return null;
        }
    }

    /** 发布感知的公开图片代理地址：与 {@link #content(String, Long, Long)} 的安全判定一一对应。 */
    public static String publicImageUrl(String spaceSlug, Long nodeId, Long fileObjectId) {
        return "/api/public/wiki/" + spaceSlug + "/nodes/" + nodeId + "/images/" + fileObjectId;
    }

    private final CapabilityAppService capabilities;
    private final WikiSpaceRepo spaces;
    private final WikiNodeRepo nodes;
    private final WikiPageVersionRepo versions;
    private final WikiSourceRepo sources;
    private final FileAppService files;

    /**
     * 读取发布页面引用的图片内容。
     *
     * @param spaceSlug 知识库 slug
     * @param nodeId    已发布页面节点 ID
     * @param fileId    站内文件 ID
     */
    @Transactional(readOnly = true)
    public FileContentDTO content(String spaceSlug, Long nodeId, Long fileId) {
        capabilities.ensureEnabled("wiki", "Wiki 知识库");
        WikiSpace space = spaces.findBySlug(spaceSlug).orElseThrow(() -> new BizException("知识库不存在"));
        if (!space.isPublicReadEnabled()) {
            throw new BizException("该知识库未开放公开阅读");
        }
        WikiNode node = nodes.findById(nodeId).orElseThrow(() -> new BizException("页面不存在"));
        if (!space.getId().equals(node.getSpaceId()) || node.getNodeType() != WikiNodeType.PAGE
                || node.getPublishedVersionId() == null) {
            throw new BizException("页面未发布");
        }
        WikiPageVersion version = versions.findById(node.getPublishedVersionId())
                .orElseThrow(() -> new BizException("页面未发布"));
        if (!node.getId().equals(version.getNodeId()) || !space.getId().equals(version.getSpaceId())) {
            throw new BizException("页面未发布");
        }
        // 统一拒绝口径，避免通过错误信息探测某个文件 ID 是否存在/属于哪个空间
        if (fileId == null || !referencesFile(version.getMarkdown(), fileId) || !isSpaceWikiImage(space.getId(), fileId)) {
            throw new BizException("图片不可公开访问");
        }
        FileContentDTO content = files.content(fileId);
        String contentType = content.getContentType() == null ? "" : content.getContentType().toLowerCase();
        if (!contentType.startsWith("image/")) {
            closeQuietly(content);
            throw new BizException("该文件不是图片");
        }
        return content;
    }

    /**
     * 资产归属校验：文件必须是本空间登记过的 Wiki 图片资产，且来自 wiki 图片上传通道。
     * <p>
     * 仅凭“发布正文引用了该文件 ID”不足以放行：任何能编辑/发布页面的人都可以把猜测到的私有文件 ID
     * 写进正文，借代理匿名读取跨空间或跨模块文件。当前文件模型没有空间字段，空间归属以
     * {@link WikiSource#getImages()} 登记表达；未登记的旧资产、其他空间的资料图片、非 wiki 模块文件
     * 一律拒绝（fail closed）。编辑器直传等未走资料登记的图片也会被拒绝，属已知限制。
     */
    private boolean isSpaceWikiImage(Long spaceId, Long fileId) {
        if (fileId == null || spaceId == null) {
            return false;
        }
        // 资料来源登记是当前模型下的空间归属证据；仓储按 spaceId + images.fileObjectId 过滤
        boolean registered = sources.findByImageFileObjectIds(spaceId, List.of(fileId)).stream()
                .anyMatch(source -> spaceId.equals(source.getSpaceId()) && source.getImages() != null
                        && source.getImages().stream().anyMatch(image -> fileId.equals(image.fileObjectId())));
        if (!registered) {
            return false;
        }
        try {
            FileObjectDTO file = files.get(fileId);
            String module = file.getModule() == null ? "" : file.getModule().trim();
            return module.startsWith("wiki-image");
        }
        catch (BizException exception) {
            // 文件不存在/已删除：按不可公开访问处理，不回显内部原因
            return false;
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
}
