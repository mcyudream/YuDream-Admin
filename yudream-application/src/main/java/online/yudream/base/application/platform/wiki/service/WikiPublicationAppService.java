package online.yudream.base.application.platform.wiki.service;

import lombok.RequiredArgsConstructor;
import online.yudream.base.application.platform.capability.service.CapabilityAppService;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.wiki.aggregate.WikiNode;
import online.yudream.base.domain.platform.wiki.aggregate.WikiPageVersion;
import online.yudream.base.domain.platform.wiki.aggregate.WikiSpace;
import online.yudream.base.domain.platform.wiki.enumerate.WikiIndexStatus;
import online.yudream.base.domain.platform.wiki.enumerate.WikiNodeType;
import online.yudream.base.domain.platform.wiki.repo.WikiNodeRepo;
import online.yudream.base.domain.platform.wiki.repo.WikiPageVersionRepo;
import online.yudream.base.domain.platform.wiki.repo.WikiSpaceRepo;
import online.yudream.base.domain.platform.wiki.service.WikiIndexGateway;
import online.yudream.base.domain.platform.wiki.service.WikiPublicationProgressGateway;
import online.yudream.base.domain.platform.wiki.valobj.WikiPublicationProgress;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Service
@RequiredArgsConstructor
public class WikiPublicationAppService {
    private final CapabilityAppService capabilities;
    private final WikiSpaceRepo spaces;
    private final WikiNodeRepo nodes;
    private final WikiPageVersionRepo versions;
    private final WikiIndexGateway indexes;
    private final WikiPublicationProgressGateway progress;

    @Transactional
    public void publish(Long nodeId) {
        capabilities.ensureEnabled("wiki", "Wiki 知识库");
        WikiNode node = nodes.findById(nodeId).orElseThrow(() -> new BizException("节点不存在"));
        if (node.getNodeType() != WikiNodeType.PAGE) throw new BizException("仅页面可以发布");
        WikiSpace space = spaces.findById(node.getSpaceId()).orElseThrow(() -> new BizException("知识库不存在"));
        publish(nodeId, null, "version", "正在创建发布版本", 5, false);
        long revision = versions.findLatest(nodeId).map(version -> version.getRevision() + 1).orElse(1L);
        WikiPageVersion version = WikiPageVersion.builder().spaceId(space.getId()).nodeId(nodeId).revision(revision)
                .title(node.getTitle()).markdown(node.getMarkdownDraft()).contentHash(hash(node.getMarkdownDraft()))
                .indexStatus(WikiIndexStatus.INDEXING).build();
        version = versions.save(version);
        try {
            publish(nodeId, version.getId(), "indexing", "发布版本已创建，开始构建检索索引", 10, false);
            WikiPageVersion current = version;
            indexes.index(space, current, node.getAncestorPath() + node.getSlug(), event ->
                    publish(nodeId, current.getId(), event.phase(), event.message(), event.percent(), false));
            version.indexed();
            publish(nodeId, version.getId(), "complete", "页面已发布，向量索引和图谱结果已就绪", 100, true);
        } catch (Exception error) {
            version.failed(error.getMessage());
            publish(nodeId, version.getId(), "failed", "页面已发布，但索引失败：" + version.getIndexError(), 100, true);
        }
        node.setPublishedVersionId(version.getId());
        nodes.save(node);
        versions.save(version);
    }

    @Transactional
    public void unpublish(Long nodeId) {
        WikiNode node = nodes.findById(nodeId).orElseThrow(() -> new BizException("节点不存在"));
        if (node.getPublishedVersionId() != null) {
            indexes.remove(spaces.findById(node.getSpaceId()).orElseThrow(() -> new BizException("知识库不存在")), nodeId, node.getPublishedVersionId());
            node.setPublishedVersionId(null);
            nodes.save(node);
        }
    }

    private void publish(Long nodeId, Long versionId, String phase, String message, int percent, boolean completed) {
        progress.publish(new WikiPublicationProgress(nodeId, versionId, phase, message, percent, completed));
    }

    private static String hash(String value) {
        try {
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest((value == null ? "" : value).getBytes(StandardCharsets.UTF_8)));
        } catch (Exception error) {
            throw new IllegalStateException(error);
        }
    }
}
