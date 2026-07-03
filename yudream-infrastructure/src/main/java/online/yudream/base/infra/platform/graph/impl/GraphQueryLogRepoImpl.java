package online.yudream.base.infra.platform.graph.impl;

import lombok.RequiredArgsConstructor;
import online.yudream.base.domain.common.PageResult;
import online.yudream.base.domain.platform.graph.aggregate.GraphQueryLog;
import online.yudream.base.domain.platform.graph.repo.GraphQueryLogRepo;
import online.yudream.base.domain.shared.IdGenerator;
import online.yudream.base.infra.platform.graph.dataobj.GraphQueryLogDO;
import online.yudream.base.infra.platform.graph.mapper.GraphInfraMapper;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class GraphQueryLogRepoImpl implements GraphQueryLogRepo {

    private final MongoTemplate mongoTemplate;
    private final IdGenerator idGenerator;

    @Override
    public GraphQueryLog save(GraphQueryLog log) {
        GraphQueryLogDO dataObj = GraphInfraMapper.toDataObj(log);
        if (dataObj.getId() == null) {
            dataObj.setId(idGenerator.nextId());
            dataObj.setCreateTime(LocalDateTime.now());
        }
        dataObj.setUpdateTime(LocalDateTime.now());
        return GraphInfraMapper.toDomain(mongoTemplate.save(dataObj));
    }

    @Override
    public PageResult<GraphQueryLog> page(String keyword, int page, int size) {
        Query query = query(keyword).with(Sort.by(Sort.Direction.DESC, "executedAt"));
        long total = mongoTemplate.count(query, GraphQueryLogDO.class);
        int currentPage = Math.max(page, 1);
        int pageSize = Math.max(size, 1);
        query.skip((long) (currentPage - 1) * pageSize).limit(pageSize);
        return new PageResult<>(
                mongoTemplate.find(query, GraphQueryLogDO.class).stream().map(GraphInfraMapper::toDomain).toList(),
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
                    Criteria.where("connectionCode").regex(pattern, "i"),
                    Criteria.where("cypher").regex(pattern, "i"),
                    Criteria.where("summary").regex(pattern, "i")
            ));
        }
        return query;
    }
}
