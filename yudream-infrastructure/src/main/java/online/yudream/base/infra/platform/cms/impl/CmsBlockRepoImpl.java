package online.yudream.base.infra.platform.cms.impl;

import lombok.RequiredArgsConstructor;
import online.yudream.base.domain.common.PageResult;
import online.yudream.base.domain.platform.cms.aggregate.CmsBlock;
import online.yudream.base.domain.platform.cms.enumerate.CmsBlockKind;
import online.yudream.base.domain.platform.cms.repo.CmsBlockRepo;
import online.yudream.base.domain.shared.IdGenerator;
import online.yudream.base.infra.platform.cms.dataobj.CmsBlockDO;
import online.yudream.base.infra.platform.cms.mapper.CmsInfraMapper;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class CmsBlockRepoImpl implements CmsBlockRepo {

    private final MongoTemplate mongoTemplate;
    private final IdGenerator idGenerator;

    @Override
    public CmsBlock save(CmsBlock block) {
        CmsBlockDO dataObj = CmsInfraMapper.toDataObj(block);
        if (dataObj.getId() == null) {
            dataObj.setId(idGenerator.nextId());
            dataObj.setCreateTime(LocalDateTime.now());
        }
        dataObj.setUpdateTime(LocalDateTime.now());
        return CmsInfraMapper.toDomain(mongoTemplate.save(dataObj));
    }

    @Override
    public Optional<CmsBlock> findById(Long id) {
        return Optional.ofNullable(CmsInfraMapper.toDomain(mongoTemplate.findById(id, CmsBlockDO.class)));
    }

    @Override
    public Optional<CmsBlock> findByCode(String code) {
        Query query = Query.query(Criteria.where("code").is(code));
        return Optional.ofNullable(CmsInfraMapper.toDomain(mongoTemplate.findOne(query, CmsBlockDO.class)));
    }

    @Override
    public void deleteById(Long id) {
        Query query = Query.query(Criteria.where("id").is(id));
        mongoTemplate.remove(query, CmsBlockDO.class);
    }

    @Override
    public PageResult<CmsBlock> page(String keyword, String category, CmsBlockKind kind, int page, int size) {
        Query query = query(keyword, category, kind).with(Sort.by(Sort.Direction.DESC, "createTime"));
        return page(query, page, size);
    }

    @Override
    public List<CmsBlock> findEnabledByKind(CmsBlockKind kind) {
        Query query = Query.query(Criteria.where("enabled").is(true));
        if (kind != null) {
            query.addCriteria(Criteria.where("kind").is(kind));
        }
        query.with(Sort.by(Sort.Direction.ASC, "sort").and(Sort.by(Sort.Direction.DESC, "createTime")));
        return mongoTemplate.find(query, CmsBlockDO.class).stream().map(CmsInfraMapper::toDomain).toList();
    }

    @Override
    public List<CmsBlock> findAllEnabled() {
        Query query = Query.query(Criteria.where("enabled").is(true))
                .with(Sort.by(Sort.Direction.ASC, "sort").and(Sort.by(Sort.Direction.DESC, "createTime")));
        return mongoTemplate.find(query, CmsBlockDO.class).stream().map(CmsInfraMapper::toDomain).toList();
    }

    private PageResult<CmsBlock> page(Query query, int page, int size) {
        long total = mongoTemplate.count(query, CmsBlockDO.class);
        int currentPage = Math.max(page, 1);
        int pageSize = Math.max(size, 1);
        query.skip((long) (currentPage - 1) * pageSize).limit(pageSize);
        return new PageResult<>(
                mongoTemplate.find(query, CmsBlockDO.class).stream().map(CmsInfraMapper::toDomain).toList(),
                total,
                currentPage,
                pageSize
        );
    }

    private Query query(String keyword, String category, CmsBlockKind kind) {
        Query query = new Query();
        if (StringUtils.hasText(keyword)) {
            String pattern = ".*" + Pattern.quote(keyword.trim()) + ".*";
            query.addCriteria(new Criteria().orOperator(
                    Criteria.where("code").regex(pattern, "i"),
                    Criteria.where("name").regex(pattern, "i"),
                    Criteria.where("description").regex(pattern, "i")
            ));
        }
        if (StringUtils.hasText(category)) {
            query.addCriteria(Criteria.where("category").is(category.trim()));
        }
        if (kind != null) {
            query.addCriteria(Criteria.where("kind").is(kind));
        }
        return query;
    }
}
