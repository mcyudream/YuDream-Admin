package online.yudream.base.infra.platform.cms.impl;

import lombok.RequiredArgsConstructor;
import online.yudream.base.domain.common.PageResult;
import online.yudream.base.domain.platform.cms.aggregate.CmsPage;
import online.yudream.base.domain.platform.cms.enumerate.PageStatus;
import online.yudream.base.domain.platform.cms.repo.CmsPageRepo;
import online.yudream.base.domain.shared.IdGenerator;
import online.yudream.base.infra.platform.cms.dataobj.CmsPageDO;
import online.yudream.base.infra.platform.cms.mapper.CmsInfraMapper;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class CmsPageRepoImpl implements CmsPageRepo {

    private final MongoTemplate mongoTemplate;
    private final IdGenerator idGenerator;

    @Override
    public CmsPage save(CmsPage page) {
        CmsPageDO dataObj = CmsInfraMapper.toDataObj(page);
        if (dataObj.getId() == null) {
            dataObj.setId(idGenerator.nextId());
            dataObj.setCreateTime(LocalDateTime.now());
        }
        dataObj.setUpdateTime(LocalDateTime.now());
        return CmsInfraMapper.toDomain(mongoTemplate.save(dataObj));
    }

    @Override
    public Optional<CmsPage> findById(Long id) {
        return Optional.ofNullable(CmsInfraMapper.toDomain(mongoTemplate.findById(id, CmsPageDO.class)));
    }

    @Override
    public Optional<CmsPage> findBySlug(String slug) {
        Query query = Query.query(Criteria.where("slug").is(slug));
        return Optional.ofNullable(CmsInfraMapper.toDomain(mongoTemplate.findOne(query, CmsPageDO.class)));
    }

    @Override
    public PageResult<CmsPage> page(String keyword, int page, int size) {
        Query query = query(keyword).with(Sort.by(Sort.Direction.DESC, "createTime"));
        return page(query, page, size);
    }

    @Override
    public PageResult<CmsPage> publishedPage(String keyword, String category, String tag, int page, int size) {
        Query query = query(keyword)
                .addCriteria(Criteria.where("status").is(PageStatus.PUBLISHED))
                .with(Sort.by(Sort.Direction.DESC, "publishedAt", "createTime"));
        if (StringUtils.hasText(category)) {
            query.addCriteria(Criteria.where("categories").is(category.trim()));
        }
        if (StringUtils.hasText(tag)) {
            query.addCriteria(Criteria.where("tags").is(tag.trim()));
        }
        return page(query, page, size);
    }

    private PageResult<CmsPage> page(Query query, int page, int size) {
        long total = mongoTemplate.count(query, CmsPageDO.class);
        int currentPage = Math.max(page, 1);
        int pageSize = Math.max(size, 1);
        query.skip((long) (currentPage - 1) * pageSize).limit(pageSize);
        return new PageResult<>(
                mongoTemplate.find(query, CmsPageDO.class).stream().map(CmsInfraMapper::toDomain).toList(),
                total,
                currentPage,
                pageSize
        );
    }

    private Query query(String keyword) {
        Query query = new Query();
        if (StringUtils.hasText(keyword)) {
            String pattern = ".*" + Pattern.quote(keyword.trim()) + ".*";
            query.addCriteria(new Criteria().orOperator(
                    Criteria.where("title").regex(pattern, "i"),
                    Criteria.where("slug").regex(pattern, "i"),
                    Criteria.where("summary").regex(pattern, "i")
            ));
        }
        return query;
    }
}
