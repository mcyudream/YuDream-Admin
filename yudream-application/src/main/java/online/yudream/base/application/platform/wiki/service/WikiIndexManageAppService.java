package online.yudream.base.application.platform.wiki.service;

import lombok.RequiredArgsConstructor;
import online.yudream.base.application.platform.capability.service.CapabilityAppService;
import online.yudream.base.application.platform.wiki.dto.WikiIndexSnapshotDTO;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.wiki.aggregate.WikiNode;
import online.yudream.base.domain.platform.wiki.repo.WikiNodeRepo;
import online.yudream.base.domain.platform.wiki.repo.WikiSpaceRepo;
import online.yudream.base.domain.platform.wiki.service.WikiIndexGateway;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class WikiIndexManageAppService {
    private final CapabilityAppService capabilities;
    private final WikiSpaceRepo spaces;
    private final WikiNodeRepo nodes;
    private final WikiIndexGateway indexes;

    @Transactional(readOnly = true)
    public WikiIndexSnapshotDTO inspect(Long nodeId) {
        capabilities.ensureEnabled("wiki", "Wiki 知识库");
        WikiNode node = nodes.findById(nodeId).orElseThrow(() -> new BizException("节点不存在"));
        if (node.getPublishedVersionId() == null) throw new BizException("页面尚未发布");
        var snapshot = indexes.inspect(spaces.findById(node.getSpaceId()).orElseThrow(() -> new BizException("知识库不存在")), nodeId, node.getPublishedVersionId());
        return WikiIndexSnapshotDTO.builder().chunks(snapshot.chunks().stream().map(item -> WikiIndexSnapshotDTO.Chunk.builder().sequence(item.sequence()).title(item.title()).path(item.path()).content(item.content()).build()).toList()).relations(snapshot.relations().stream().map(item -> WikiIndexSnapshotDTO.Relation.builder().source(item.source()).sourceType(item.sourceType()).relation(item.relation()).target(item.target()).targetType(item.targetType()).confidence(item.confidence()).build()).toList()).build();
    }
}
