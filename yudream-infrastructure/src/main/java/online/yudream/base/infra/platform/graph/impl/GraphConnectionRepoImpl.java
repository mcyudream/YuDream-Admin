package online.yudream.base.infra.platform.graph.impl;

import lombok.RequiredArgsConstructor;
import online.yudream.base.domain.common.PageResult;
import online.yudream.base.domain.platform.graph.aggregate.GraphConnection;
import online.yudream.base.domain.platform.graph.enumerate.GraphConnectionStatus;
import online.yudream.base.domain.platform.graph.repo.GraphConnectionRepo;
import online.yudream.base.domain.shared.IdGenerator;
import online.yudream.base.infra.platform.graph.dataobj.GraphConnectionDO;
import online.yudream.base.infra.platform.graph.mapper.GraphInfraMapper;
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
public class GraphConnectionRepoImpl implements GraphConnectionRepo {

    private final MongoTemplate mongoTemplate;
    private final IdGenerator idGenerator;

    @Override
    public GraphConnection save(GraphConnection connection) {
        GraphConnectionDO dataObj = GraphInfraMapper.toDataObj(connection);
        if (dataObj.getId() == null) {
            dataObj.setId(idGenerator.nextId());
            dataObj.setCreateTime(LocalDateTime.now());
        }
        dataObj.setUpdateTime(LocalDateTime.now());
        return GraphInfraMapper.toDomain(mongoTemplate.save(dataObj));
    }

    @Override
    public Optional<GraphConnection> findById(Long id) {
        return Optional.ofNullable(GraphInfraMapper.toDomain(mongoTemplate.findById(id, GraphConnectionDO.class)));
    }

    @Override
    public Optional<GraphConnection> findByCode(String code) {
        Query query = Query.query(Criteria.where("code").is(code));
        return Optional.ofNullable(GraphInfraMapper.toDomain(mongoTemplate.findOne(query, GraphConnectionDO.class)));
    }

    @Override
    public Optional<GraphConnection> findActiveAuthorizedByCode(String code, String pluginCode) {
        if (!StringUtils.hasText(code) || !StringUtils.hasText(pluginCode)) {
            return Optional.empty();
        }
        Query query = Query.query(Criteria.where("code").is(code.trim())
                .and("status").is(GraphConnectionStatus.ACTIVE)
                .and("authorizedPluginCodes").is(pluginCode.trim()));
        return Optional.ofNullable(GraphInfraMapper.toDomain(mongoTemplate.findOne(query, GraphConnectionDO.class)));
    }

    @Override
    public List<GraphConnection> findActiveAuthorizedByPluginCode(String pluginCode, int limit) {
        if (!StringUtils.hasText(pluginCode)) {
            return List.of();
        }
        Query query = Query.query(Criteria.where("status").is(GraphConnectionStatus.ACTIVE)
                        .and("authorizedPluginCodes").is(pluginCode.trim()))
                .with(Sort.by(Sort.Direction.DESC, "createTime"))
                .limit(Math.clamp(limit, 1, 100));
        return mongoTemplate.find(query, GraphConnectionDO.class).stream().map(GraphInfraMapper::toDomain).toList();
    }

    @Override
    public PageResult<GraphConnection> page(String keyword, GraphConnectionStatus status, int page, int size) {
        Query query = query(keyword, status).with(Sort.by(Sort.Direction.DESC, "createTime"));
        long total = mongoTemplate.count(query, GraphConnectionDO.class);
        int currentPage = Math.max(page, 1);
        int pageSize = Math.max(size, 1);
        query.skip((long) (currentPage - 1) * pageSize).limit(pageSize);
        return new PageResult<>(
                mongoTemplate.find(query, GraphConnectionDO.class).stream().map(GraphInfraMapper::toDomain).toList(),
                total,
                currentPage,
                pageSize
        );
    }

    private Query query(String keyword, GraphConnectionStatus status) {
        Query query = new Query();
        if (status != null) {
            query.addCriteria(Criteria.where("status").is(status));
        }
        if (StringUtils.hasText(keyword)) {
            String pattern = ".*" + Pattern.quote(keyword.trim()) + ".*";
            query.addCriteria(new Criteria().orOperator(
                    Criteria.where("name").regex(pattern, "i"),
                    Criteria.where("code").regex(pattern, "i"),
                    Criteria.where("description").regex(pattern, "i")
            ));
        }
        return query;
    }
}
