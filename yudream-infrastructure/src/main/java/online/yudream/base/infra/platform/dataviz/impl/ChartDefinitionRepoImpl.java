package online.yudream.base.infra.platform.dataviz.impl;

import lombok.RequiredArgsConstructor;
import online.yudream.base.domain.common.PageResult;
import online.yudream.base.domain.platform.dataviz.aggregate.ChartDefinition;
import online.yudream.base.domain.platform.dataviz.repo.ChartDefinitionRepo;
import online.yudream.base.domain.shared.IdGenerator;
import online.yudream.base.infra.platform.dataviz.dataobj.ChartDefinitionDO;
import online.yudream.base.infra.platform.dataviz.mapper.ChartInfraMapper;
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
public class ChartDefinitionRepoImpl implements ChartDefinitionRepo {

    private final MongoTemplate mongoTemplate;
    private final IdGenerator idGenerator;

    @Override
    public ChartDefinition save(ChartDefinition chartDefinition) {
        ChartDefinitionDO dataObj = ChartInfraMapper.toDataObj(chartDefinition);
        if (dataObj.getId() == null) {
            dataObj.setId(idGenerator.nextId());
            dataObj.setCreateTime(LocalDateTime.now());
        }
        dataObj.setUpdateTime(LocalDateTime.now());
        return ChartInfraMapper.toDomain(mongoTemplate.save(dataObj));
    }

    @Override
    public Optional<ChartDefinition> findById(Long id) {
        return Optional.ofNullable(ChartInfraMapper.toDomain(mongoTemplate.findById(id, ChartDefinitionDO.class)));
    }

    @Override
    public Optional<ChartDefinition> findByCode(String code) {
        Query query = Query.query(Criteria.where("code").is(code));
        return Optional.ofNullable(ChartInfraMapper.toDomain(mongoTemplate.findOne(query, ChartDefinitionDO.class)));
    }

    @Override
    public PageResult<ChartDefinition> page(String keyword, int page, int size) {
        int currentPage = Math.max(page, 1);
        int pageSize = Math.max(size, 1);
        Query query = query(keyword).with(Sort.by(Sort.Direction.DESC, "createTime"));
        long total = mongoTemplate.count(query, ChartDefinitionDO.class);
        query.skip((long) (currentPage - 1) * pageSize).limit(pageSize);
        return new PageResult<>(
                mongoTemplate.find(query, ChartDefinitionDO.class).stream().map(ChartInfraMapper::toDomain).toList(),
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
                    Criteria.where("code").regex(pattern, "i"),
                    Criteria.where("name").regex(pattern, "i")
            ));
        }
        return query;
    }
}
