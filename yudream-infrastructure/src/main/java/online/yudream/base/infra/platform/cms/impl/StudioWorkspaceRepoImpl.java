package online.yudream.base.infra.platform.cms.impl;

import lombok.RequiredArgsConstructor;
import online.yudream.base.domain.platform.cms.aggregate.StudioWorkspace;
import online.yudream.base.domain.platform.cms.repo.StudioWorkspaceRepo;
import online.yudream.base.infra.platform.cms.dataobj.StudioWorkspaceDO;
import online.yudream.base.infra.platform.cms.mapper.StudioInfraMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "yudream.platform.capabilities.cms", name = "enabled", havingValue = "true")
public class StudioWorkspaceRepoImpl implements StudioWorkspaceRepo {
    private final MongoTemplate mongoTemplate;
    @Override
    public Optional<StudioWorkspace> findByThemeCode(String themeCode) {
        return Optional.ofNullable(StudioInfraMapper.toDomain(mongoTemplate.findById(themeCode, StudioWorkspaceDO.class)));
    }
    @Override
    public boolean compareAndSet(long expectedRevision, StudioWorkspace workspace) {
        StudioWorkspaceDO data = StudioInfraMapper.toDataObj(workspace);
        if (expectedRevision == 0) {
            try { mongoTemplate.insert(data); return true; }
            catch (DuplicateKeyException ex) { return false; }
        }
        Query query = Query.query(Criteria.where("_id").is(workspace.themeCode()).and("revision").is(expectedRevision));
        Update update = new Update().set("revision", data.getRevision()).set("documentJson", data.getDocumentJson())
                .set("publishedReleaseId", data.getPublishedReleaseId()).set("updatedAt", data.getUpdatedAt()).set("releases", data.getReleases());
        return mongoTemplate.updateFirst(query, update, StudioWorkspaceDO.class).getMatchedCount() == 1;
    }
}
