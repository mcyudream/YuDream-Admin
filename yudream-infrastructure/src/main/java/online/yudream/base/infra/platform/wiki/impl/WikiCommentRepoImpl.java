package online.yudream.base.infra.platform.wiki.impl;

import lombok.RequiredArgsConstructor;
import online.yudream.base.domain.common.PageResult;
import online.yudream.base.domain.platform.wiki.aggregate.WikiComment;
import online.yudream.base.domain.platform.wiki.enumerate.WikiCommentStatus;
import online.yudream.base.domain.platform.wiki.repo.WikiCommentRepo;
import online.yudream.base.domain.shared.IdGenerator;
import online.yudream.base.infra.platform.wiki.dataobj.WikiCommentDO;
import online.yudream.base.infra.platform.wiki.mapper.WikiInfraMapper;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class WikiCommentRepoImpl implements WikiCommentRepo {

    private static final int MAX_PAGE_SIZE = 200;

    private final MongoTemplate mongo;
    private final IdGenerator ids;

    @Override
    public WikiComment save(WikiComment comment) {
        WikiCommentDO dataObj = WikiInfraMapper.comment(comment);
        if (dataObj.getId() == null) {
            dataObj.setId(ids.nextId());
            dataObj.setCreateTime(LocalDateTime.now());
        }
        if (dataObj.getUpdateTime() == null) {
            dataObj.setUpdateTime(LocalDateTime.now());
        }
        WikiCommentDO saved = mongo.save(dataObj);
        comment.setVersion(saved.getVersion());
        return WikiInfraMapper.comment(saved);
    }

    @Override
    public Optional<WikiComment> findById(Long id) {
        return Optional.ofNullable(WikiInfraMapper.comment(mongo.findById(id, WikiCommentDO.class)));
    }

    @Override
    public PageResult<WikiComment> pageBySpace(Long spaceId, WikiCommentStatus status, int page, int size) {
        Query query = new Query().addCriteria(Criteria.where("spaceId").is(spaceId));
        if (status != null) {
            query.addCriteria(Criteria.where("status").is(status));
        }
        query.with(Sort.by(Sort.Direction.DESC, "createTime"));
        return paginate(query, page, size);
    }

    @Override
    public PageResult<WikiComment> pagePublishedRoots(Long spaceId, Long nodeId, int page, int size) {
        Query query = new Query().addCriteria(Criteria.where("spaceId").is(spaceId)
                        .and("nodeId").is(nodeId)
                        .and("status").is(WikiCommentStatus.PUBLISHED)
                        .and("parentId").is(null))
                .with(Sort.by(Sort.Order.desc("pinned"), Sort.Order.desc("createTime")));
        return paginate(query, page, size);
    }

    @Override
    public List<WikiComment> findPublishedReplies(Long spaceId, Long nodeId, Collection<Long> parentIds) {
        if (parentIds == null || parentIds.isEmpty()) {
            return List.of();
        }
        Query query = Query.query(Criteria.where("spaceId").is(spaceId)
                        .and("nodeId").is(nodeId)
                        .and("status").is(WikiCommentStatus.PUBLISHED)
                        .and("parentId").in(parentIds))
                .with(Sort.by(Sort.Direction.ASC, "createTime"));
        return mongo.find(query, WikiCommentDO.class).stream().map(WikiInfraMapper::comment).toList();
    }

    @Override
    public long countBySpaceId(Long spaceId) {
        return mongo.count(Query.query(Criteria.where("spaceId").is(spaceId)), WikiCommentDO.class);
    }

    @Override
    public void deleteBySpaceId(Long spaceId) {
        mongo.remove(Query.query(Criteria.where("spaceId").is(spaceId)), WikiCommentDO.class);
    }

    private PageResult<WikiComment> paginate(Query query, int page, int size) {
        int currentPage = Math.max(page, 1);
        int pageSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        long total = mongo.count(query, WikiCommentDO.class);
        query.skip((long) (currentPage - 1) * pageSize).limit(pageSize);
        return new PageResult<>(mongo.find(query, WikiCommentDO.class).stream().map(WikiInfraMapper::comment).toList(),
                total, currentPage, pageSize);
    }
}
