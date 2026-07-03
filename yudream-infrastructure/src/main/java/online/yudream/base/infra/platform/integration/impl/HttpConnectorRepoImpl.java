package online.yudream.base.infra.platform.integration.impl;

import lombok.RequiredArgsConstructor;
import online.yudream.base.domain.common.PageResult;
import online.yudream.base.domain.platform.integration.aggregate.HttpConnector;
import online.yudream.base.domain.platform.integration.repo.HttpConnectorRepo;
import online.yudream.base.domain.shared.IdGenerator;
import online.yudream.base.infra.platform.integration.dataobj.HttpConnectorDO;
import online.yudream.base.infra.platform.integration.mapper.IntegrationInfraMapper;
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
public class HttpConnectorRepoImpl implements HttpConnectorRepo {

    private final MongoTemplate mongoTemplate;
    private final IdGenerator idGenerator;

    @Override
    public HttpConnector save(HttpConnector connector) {
        HttpConnectorDO dataObj = IntegrationInfraMapper.toDataObj(connector);
        if (dataObj.getId() == null) {
            dataObj.setId(idGenerator.nextId());
            dataObj.setCreateTime(LocalDateTime.now());
        }
        dataObj.setUpdateTime(LocalDateTime.now());
        return IntegrationInfraMapper.toDomain(mongoTemplate.save(dataObj));
    }

    @Override
    public Optional<HttpConnector> findById(Long id) {
        return Optional.ofNullable(IntegrationInfraMapper.toDomain(mongoTemplate.findById(id, HttpConnectorDO.class)));
    }

    @Override
    public Optional<HttpConnector> findByCode(String code) {
        Query query = Query.query(Criteria.where("code").is(code));
        return Optional.ofNullable(IntegrationInfraMapper.toDomain(mongoTemplate.findOne(query, HttpConnectorDO.class)));
    }

    @Override
    public PageResult<HttpConnector> page(String keyword, int page, int size) {
        Query query = query(keyword).with(Sort.by(Sort.Direction.DESC, "createTime"));
        long total = mongoTemplate.count(query, HttpConnectorDO.class);
        int currentPage = Math.max(page, 1);
        int pageSize = Math.max(size, 1);
        query.skip((long) (currentPage - 1) * pageSize).limit(pageSize);
        return new PageResult<>(
                mongoTemplate.find(query, HttpConnectorDO.class).stream().map(IntegrationInfraMapper::toDomain).toList(),
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
                    Criteria.where("name").regex(pattern, "i"),
                    Criteria.where("code").regex(pattern, "i"),
                    Criteria.where("url").regex(pattern, "i")
            ));
        }
        return query;
    }
}
