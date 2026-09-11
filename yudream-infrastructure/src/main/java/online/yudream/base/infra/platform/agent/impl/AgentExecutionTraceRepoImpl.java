package online.yudream.base.infra.platform.agent.impl;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import online.yudream.base.application.platform.agent.service.AgentTraceProperties;
import online.yudream.base.domain.platform.agent.aggregate.AgentExecutionTrace;
import online.yudream.base.domain.platform.agent.enumerate.AgentTraceSource;
import online.yudream.base.domain.platform.agent.enumerate.AgentTraceStatus;
import online.yudream.base.domain.platform.agent.repo.AgentExecutionTraceRepo;
import online.yudream.base.domain.platform.agent.valobj.AgentTraceBucket;
import online.yudream.base.domain.platform.agent.valobj.AgentTraceQuery;
import online.yudream.base.domain.platform.agent.valobj.AgentTraceStats;
import online.yudream.base.domain.shared.IdGenerator;
import online.yudream.base.infra.platform.agent.dataobj.AgentExecutionTraceDO;
import online.yudream.base.infra.platform.agent.mapper.AgentTraceInfraMapper;
import org.bson.Document;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.aggregation.ConditionalOperators;
import org.springframework.data.mongodb.core.aggregation.GroupOperation;
import org.springframework.data.mongodb.core.aggregation.MatchOperation;
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
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class AgentExecutionTraceRepoImpl implements AgentExecutionTraceRepo {

    private static final int EXPORT_MAX = 10_000;
    private static final int STATS_BUCKET_LIMIT = 8;

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
    public List<AgentExecutionTrace> listForExport(AgentTraceQuery query, int limit) {
        int size = limit <= 0 ? 1 : Math.min(limit, EXPORT_MAX);
        Query mongoQuery = Query.query(criteria(query))
                .with(Sort.by(Sort.Direction.DESC, "createTime"))
                .limit(size);
        return mongo.find(mongoQuery, AgentExecutionTraceDO.class).stream()
                .map(AgentTraceInfraMapper::toDomain)
                .toList();
    }

    @Override
    public long count(AgentTraceQuery query) {
        return mongo.count(Query.query(criteria(query)), AgentExecutionTraceDO.class);
    }

    @Override
    public AgentTraceStats stats(AgentTraceQuery query) {
        Criteria match = criteria(query);
        MatchOperation matchOp = Aggregation.match(match);
        GroupOperation totals = Aggregation.group()
                .count().as("total")
                .sum(ConditionalOperators.when(Criteria.where("status").is(AgentTraceStatus.SUCCEEDED)).then(1).otherwise(0)).as("succeeded")
                .sum(ConditionalOperators.when(Criteria.where("status").is(AgentTraceStatus.FAILED)).then(1).otherwise(0)).as("failed")
                .sum(ConditionalOperators.when(Criteria.where("status").is(AgentTraceStatus.RUNNING)).then(1).otherwise(0)).as("running")
                .avg("durationMs").as("avgDurationMs")
                .max("durationMs").as("maxDurationMs")
                .sum("usage.promptTokens").as("promptTokens")
                .sum("usage.completionTokens").as("completionTokens")
                .sum("usage.totalTokens").as("totalTokens");
        AggregationResults<Document> totalsResult = mongo.aggregate(
                Aggregation.newAggregation(matchOp, totals),
                AgentExecutionTraceDO.class,
                Document.class
        );
        Document totalsDoc = totalsResult.getUniqueMappedResult();
        if (totalsDoc == null) {
            return AgentTraceStats.empty();
        }
        return new AgentTraceStats(
                longValue(totalsDoc, "total"),
                longValue(totalsDoc, "succeeded"),
                longValue(totalsDoc, "failed"),
                longValue(totalsDoc, "running"),
                Math.round(doubleValue(totalsDoc, "avgDurationMs")),
                longValue(totalsDoc, "maxDurationMs"),
                longValue(totalsDoc, "promptTokens"),
                longValue(totalsDoc, "completionTokens"),
                longValue(totalsDoc, "totalTokens"),
                bucketBy(match, "source", true),
                bucketBy(match, "agentCode", false)
        );
    }

    @Override
    public long deleteByTraceId(String traceId) {
        if (!StringUtils.hasText(traceId)) {
            return 0;
        }
        return mongo.remove(Query.query(Criteria.where("traceId").is(traceId)), AgentExecutionTraceDO.class).getDeletedCount();
    }

    @Override
    public long delete(AgentTraceQuery query) {
        return mongo.remove(Query.query(criteria(query)), AgentExecutionTraceDO.class).getDeletedCount();
    }

    private List<AgentTraceBucket> bucketBy(Criteria match, String field, boolean enumKey) {
        AggregationResults<Document> result = mongo.aggregate(
                Aggregation.newAggregation(
                        Aggregation.match(match),
                        Aggregation.group(field).count().as("count"),
                        Aggregation.sort(Sort.Direction.DESC, "count"),
                        Aggregation.limit(STATS_BUCKET_LIMIT)
                ),
                AgentExecutionTraceDO.class,
                Document.class
        );
        List<AgentTraceBucket> buckets = new ArrayList<>();
        for (Document document : result.getMappedResults()) {
            Object raw = document.get("_id");
            String key = raw == null ? "" : String.valueOf(raw);
            long count = longValue(document, "count");
            if (!StringUtils.hasText(key)) {
                buckets.add(new AgentTraceBucket("", "未标注", count));
                continue;
            }
            String label = enumKey ? sourceLabel(key) : key;
            buckets.add(new AgentTraceBucket(key, label, count));
        }
        return buckets;
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
        if (StringUtils.hasText(query.agentCode())) {
            parts.add(Criteria.where("agentCode").is(query.agentCode()));
        }
        if (query.startTime() != null) {
            parts.add(Criteria.where("startTime").gte(query.startTime()));
        }
        if (query.endTime() != null) {
            parts.add(Criteria.where("startTime").lte(query.endTime()));
        }
        if (StringUtils.hasText(query.keyword())) {
            Pattern pattern = Pattern.compile(Pattern.quote(query.keyword()), Pattern.CASE_INSENSITIVE);
            parts.add(new Criteria().orOperator(
                    Criteria.where("agentName").regex(pattern),
                    Criteria.where("agentCode").regex(pattern),
                    Criteria.where("ownerPluginCode").regex(pattern),
                    Criteria.where("traceId").regex(pattern),
                    Criteria.where("input").regex(pattern),
                    Criteria.where("error").regex(pattern)
            ));
        }
        return parts.isEmpty() ? new Criteria() : new Criteria().andOperator(parts);
    }

    private static String sourceLabel(String key) {
        return switch (key) {
            case "CHAT" -> "聊天";
            case "WIKI" -> "Wiki";
            case "CMS" -> "CMS";
            case "DEBUG" -> "调试";
            case "PLUGIN" -> "插件";
            case "SYSTEM" -> "系统";
            default -> key;
        };
    }

    private static long longValue(Document document, String field) {
        Number number = document.get(field, Number.class);
        return number == null ? 0L : number.longValue();
    }

    private static double doubleValue(Document document, String field) {
        Number number = document.get(field, Number.class);
        return number == null ? 0D : number.doubleValue();
    }
}
