package online.yudream.base.infra.platform.agent.impl;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import online.yudream.base.application.platform.agent.service.AgentTraceProperties;
import online.yudream.base.domain.platform.agent.aggregate.AgentExecutionTrace;
import online.yudream.base.domain.platform.agent.enumerate.AgentTraceSource;
import online.yudream.base.domain.platform.agent.repo.AgentExecutionTraceRepo;
import online.yudream.base.domain.platform.agent.valobj.AgentTraceQuery;
import online.yudream.base.domain.shared.IdGenerator;
import online.yudream.base.infra.platform.agent.dataobj.AgentExecutionTraceDO;
import online.yudream.base.infra.platform.agent.mapper.AgentTraceInfraMapper;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AgentExecutionTraceRepoImpl implements AgentExecutionTraceRepo {

    private final MongoTemplate mongo;
    private final IdGenerator ids;
    private final AgentTraceProperties properties;

    /**
     * 工程未开启 Mongo 自动建索引，TTL 需在此显式声明；失败仅降级为不自动过期，不影响启动。
     */
    @PostConstruct
    void ensureIndexes() {
        try {
            mongo.indexOps(AgentExecutionTraceDO.class).ensureIndex(
                    new Index().on("createTime", Sort.Direction.ASC)
                            .expire(Duration.ofDays(Math.max(1, properties.getRetentionDays())))
            );
        } catch (RuntimeException e) {
            log.warn("Agent 执行追踪 TTL 索引创建失败：{}", e.getMessage());
        }
    }

    @Override
    public AgentExecutionTrace save(AgentExecutionTrace trace) {
        AgentExecutionTraceDO dataObj = AgentTraceInfraMapper.toDO(trace);
        if (dataObj.getId() == null) {
            dataObj.setId(ids.nextId());
            dataObj.setCreateTime(LocalDateTime.now());
        }
        dataObj.setUpdateTime(LocalDateTime.now());
        AgentExecutionTrace saved = AgentTraceInfraMapper.toDomain(mongo.save(dataObj));
        evictOverflow(trace.getSource());
        return saved;
    }

    private void evictOverflow(AgentTraceSource source) {
        int limit = properties.getMaxPerSource();
        if (source == null || limit <= 0) {
            return;
        }
        try {
            Query overflow = Query.query(Criteria.where("source").is(source))
                    .with(Sort.by(Sort.Direction.DESC, "createTime"))
                    .skip(limit)
                    .limit(1000);
            overflow.fields().include("id");
            List<Long> staleIds = mongo.find(overflow, AgentExecutionTraceDO.class).stream()
                    .map(AgentExecutionTraceDO::getId)
                    .toList();
            if (!staleIds.isEmpty()) {
                mongo.remove(Query.query(Criteria.where("id").in(staleIds)), AgentExecutionTraceDO.class);
            }
        } catch (RuntimeException e) {
            log.warn("Agent 执行追踪限量清理失败：{}", e.getMessage());
        }
    }

    @Override
    public Optional<AgentExecutionTrace> findByTraceId(String traceId) {
        if (!StringUtils.hasText(traceId)) {
            return Optional.empty();
        }
        return Optional.ofNullable(AgentTraceInfraMapper.toDomain(
                mongo.findOne(Query.query(Criteria.where("traceId").is(traceId)), AgentExecutionTraceDO.class)
        ));
    }

    @Override
    public List<AgentExecutionTrace> query(AgentTraceQuery query) {
        Query mongoQuery = Query.query(criteria(query))
                .with(Sort.by(Sort.Direction.DESC, "createTime"))
                .skip(query.skip())
                .limit(query.size());
        return mongo.find(mongoQuery, AgentExecutionTraceDO.class).stream()
                .map(AgentTraceInfraMapper::toDomain)
                .toList();
    }

    @Override
    public long count(AgentTraceQuery query) {
        return mongo.count(Query.query(criteria(query)), AgentExecutionTraceDO.class);
    }

    private Criteria criteria(AgentTraceQuery query) {
        List<Criteria> parts = new ArrayList<>();
        if (query.source() != null) {
            parts.add(Criteria.where("source").is(query.source()));
        }
        if (StringUtils.hasText(query.pluginCode())) {
            parts.add(Criteria.where("ownerPluginCode").is(query.pluginCode()));
        }
        if (query.status() != null) {
            parts.add(Criteria.where("status").is(query.status()));
        }
        return parts.isEmpty() ? new Criteria() : new Criteria().andOperator(parts);
    }
}
