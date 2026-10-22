package online.yudream.base.infra.platform.workspace.impl;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import online.yudream.base.domain.platform.workspace.aggregate.UserWorkspace;
import online.yudream.base.domain.platform.workspace.repo.UserWorkspaceRepo;
import online.yudream.base.domain.shared.IdGenerator;
import online.yudream.base.infra.platform.workspace.dataobj.UserWorkspaceDO;
import online.yudream.base.infra.platform.workspace.mapper.WorkspaceInfraMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "yudream.platform.capabilities.ai", name = "enabled", havingValue = "true")
public class UserWorkspaceRepoImpl implements UserWorkspaceRepo {

    private final MongoTemplate mongo;
    private final IdGenerator ids;

    @PostConstruct
    void ensureIndexes() {
        try {
            mongo.indexOps(UserWorkspaceDO.class).ensureIndex(new Index().on("ownerUserId", Sort.Direction.ASC).unique());
        } catch (RuntimeException e) {
            log.warn("用户工作区唯一索引创建失败：{}", e.getMessage());
        }
    }

    @Override
    public Optional<UserWorkspace> findByOwner(Long ownerUserId) {
        return Optional.ofNullable(WorkspaceInfraMapper.workspace(
                mongo.findOne(Query.query(Criteria.where("ownerUserId").is(ownerUserId)), UserWorkspaceDO.class)));
    }

    @Override
    public UserWorkspace getOrCreate(Long ownerUserId) {
        Optional<UserWorkspace> existing = findByOwner(ownerUserId);
        if (existing.isPresent()) {
            return existing.get();
        }
        UserWorkspace workspace = UserWorkspace.create(ownerUserId);
        UserWorkspaceDO dataObj = new UserWorkspaceDO();
        dataObj.setId(ids.nextId());
        dataObj.setOwnerUserId(workspace.getOwnerUserId());
        dataObj.setCreateTime(LocalDateTime.now());
        dataObj.setUpdateTime(LocalDateTime.now());
        try {
            return WorkspaceInfraMapper.workspace(mongo.insert(dataObj));
        } catch (DuplicateKeyException race) {
            return findByOwner(ownerUserId).orElseThrow(() -> race);
        }
    }
}
