package online.yudream.base.infra.platform.wiki.impl;

import lombok.RequiredArgsConstructor;
import online.yudream.base.domain.common.PageResult;
import online.yudream.base.domain.platform.wiki.aggregate.WikiSpaceMember;
import online.yudream.base.domain.platform.wiki.enumerate.WikiSpaceMemberSubjectType;
import online.yudream.base.domain.platform.wiki.repo.WikiSpaceMemberRepo;
import online.yudream.base.domain.shared.IdGenerator;
import online.yudream.base.infra.platform.wiki.dataobj.WikiSpaceMemberDO;
import online.yudream.base.infra.platform.wiki.mapper.WikiInfraMapper;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class WikiSpaceMemberRepoImpl implements WikiSpaceMemberRepo {

    private static final int MAX_PAGE_SIZE = 200;

    private final MongoTemplate mongo;
    private final IdGenerator ids;

    @Override
    public WikiSpaceMember save(WikiSpaceMember member) {
        WikiSpaceMemberDO dataObj = WikiInfraMapper.member(member);
        if (dataObj.getId() == null) {
            dataObj.setId(ids.nextId());
            dataObj.setCreateTime(LocalDateTime.now());
        }
        dataObj.setUpdateTime(LocalDateTime.now());
        WikiSpaceMemberDO saved = mongo.save(dataObj);
        member.setVersion(saved.getVersion());
        return WikiInfraMapper.member(saved);
    }

    @Override
    public Optional<WikiSpaceMember> findById(Long id) {
        return Optional.ofNullable(WikiInfraMapper.member(mongo.findById(id, WikiSpaceMemberDO.class)));
    }

    @Override
    public Optional<WikiSpaceMember> findBySubject(Long spaceId, WikiSpaceMemberSubjectType subjectType, Long subjectId) {
        Query query = Query.query(Criteria.where("spaceId").is(spaceId).and("subjectType").is(subjectType)
                .and("subjectId").is(subjectId));
        return Optional.ofNullable(WikiInfraMapper.member(mongo.findOne(query, WikiSpaceMemberDO.class)));
    }

    @Override
    public List<WikiSpaceMember> findBySpaceId(Long spaceId) {
        return mongo.find(Query.query(Criteria.where("spaceId").is(spaceId))
                        .with(Sort.by(Sort.Direction.ASC, "createTime")),
                WikiSpaceMemberDO.class).stream().map(WikiInfraMapper::member).toList();
    }

    @Override
    public PageResult<WikiSpaceMember> page(Long spaceId, int page, int size) {
        int currentPage = Math.max(page, 1);
        int pageSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        Query query = Query.query(Criteria.where("spaceId").is(spaceId))
                .with(Sort.by(Sort.Direction.ASC, "createTime"));
        long total = mongo.count(query, WikiSpaceMemberDO.class);
        query.skip((long) (currentPage - 1) * pageSize).limit(pageSize);
        return new PageResult<>(mongo.find(query, WikiSpaceMemberDO.class).stream().map(WikiInfraMapper::member).toList(),
                total, currentPage, pageSize);
    }

    @Override
    public long countBySpaceId(Long spaceId) {
        return mongo.count(Query.query(Criteria.where("spaceId").is(spaceId)), WikiSpaceMemberDO.class);
    }

    @Override
    public void deleteById(Long id) {
        mongo.remove(Query.query(Criteria.where("id").is(id)), WikiSpaceMemberDO.class);
    }

    @Override
    public void deleteBySpaceId(Long spaceId) {
        mongo.remove(Query.query(Criteria.where("spaceId").is(spaceId)), WikiSpaceMemberDO.class);
    }
}
