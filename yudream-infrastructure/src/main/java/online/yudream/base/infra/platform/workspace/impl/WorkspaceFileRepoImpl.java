package online.yudream.base.infra.platform.workspace.impl;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.workspace.aggregate.WorkspaceFile;
import online.yudream.base.domain.platform.workspace.enumerate.WorkspaceEntryKind;
import online.yudream.base.domain.platform.workspace.repo.WorkspaceFileRepo;
import online.yudream.base.domain.shared.IdGenerator;
import online.yudream.base.infra.platform.workspace.dataobj.WorkspaceFileDO;
import online.yudream.base.infra.platform.workspace.mapper.WorkspaceInfraMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "yudream.platform.capabilities.ai", name = "enabled", havingValue = "true")
public class WorkspaceFileRepoImpl implements WorkspaceFileRepo {

    private final MongoTemplate mongo;
    private final IdGenerator ids;

    @PostConstruct
    void ensureIndexes() {
        try {
            var index = mongo.indexOps(WorkspaceFileDO.class);
            index.ensureIndex(new Index().on("workspaceId", Sort.Direction.ASC).on("path", Sort.Direction.ASC));
            index.ensureIndex(new Index().on("deletedAt", Sort.Direction.ASC).sparse());
        } catch (RuntimeException e) {
            log.warn("工作区文件索引创建失败：{}", e.getMessage());
        }
    }

    @Override
    public List<WorkspaceFile> listActive(Long workspaceId) {
        Query query = Query.query(Criteria.where("workspaceId").is(workspaceId).and("deletedAt").is(null))
                .with(Sort.by(Sort.Direction.ASC, "path"));
        return mongo.find(query, WorkspaceFileDO.class).stream().map(WorkspaceInfraMapper::file).toList();
    }

    @Override
    public List<WorkspaceFile> listTrash(Long workspaceId) {
        Query query = Query.query(Criteria.where("workspaceId").is(workspaceId).and("deletedAt").ne(null))
                .with(Sort.by(Sort.Direction.DESC, "deletedAt"));
        return mongo.find(query, WorkspaceFileDO.class).stream().map(WorkspaceInfraMapper::file).toList();
    }

    @Override
    public Optional<WorkspaceFile> findActive(Long workspaceId, String path) {
        Query query = Query.query(Criteria.where("workspaceId").is(workspaceId).and("path").is(path).and("deletedAt").is(null));
        return Optional.ofNullable(WorkspaceInfraMapper.file(mongo.findOne(query, WorkspaceFileDO.class)));
    }

    @Override
    public Optional<WorkspaceFile> findById(Long id) {
        return Optional.ofNullable(WorkspaceInfraMapper.file(mongo.findById(id, WorkspaceFileDO.class)));
    }

    @Override
    public WorkspaceFile save(WorkspaceFile file) {
        WorkspaceFileDO dataObj = WorkspaceInfraMapper.file(file);
        if (dataObj.getId() == null) {
            dataObj.setId(ids.nextId());
            dataObj.setCreateTime(LocalDateTime.now());
        }
        dataObj.setUpdateTime(LocalDateTime.now());
        try {
            return WorkspaceInfraMapper.file(mongo.save(dataObj));
        } catch (OptimisticLockingFailureException conflict) {
            throw new BizException("文件已被其他操作修改，请刷新后重试：" + file.getPath());
        }
    }

    @Override
    public void delete(Long id) {
        mongo.remove(Query.query(Criteria.where("id").is(id)), WorkspaceFileDO.class);
    }

    @Override
    public List<WorkspaceFile> findTrashedBefore(LocalDateTime cutoff, int limit) {
        Query query = Query.query(Criteria.where("deletedAt").lt(cutoff)).limit(Math.max(1, limit));
        return mongo.find(query, WorkspaceFileDO.class).stream().map(WorkspaceInfraMapper::file).toList();
    }

    @Override
    public List<WorkspaceFile> findStaleFilesUnder(String directory, LocalDateTime cutoff, int limit) {
        Query query = Query.query(Criteria.where("path").regex("^" + Pattern.quote(directory + "/"))
                        .and("kind").is(WorkspaceEntryKind.FILE)
                        .and("deletedAt").is(null)
                        .and("contentUpdatedAt").lt(cutoff))
                .limit(Math.max(1, limit));
        return mongo.find(query, WorkspaceFileDO.class).stream().map(WorkspaceInfraMapper::file).toList();
    }
}
