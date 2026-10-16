package online.yudream.base.infra.platform.feishu.impl;

import lombok.RequiredArgsConstructor;
import online.yudream.base.domain.platform.feishu.enumerate.FeishuQuotaScope;
import online.yudream.base.domain.platform.feishu.repo.FeishuCallQuotaRepo;
import online.yudream.base.domain.platform.feishu.valobj.FeishuQuotaPolicy;
import online.yudream.base.domain.platform.feishu.valobj.FeishuQuotaSnapshot;
import online.yudream.base.domain.platform.feishu.valobj.FeishuQuotaWindowKeys;
import online.yudream.base.domain.shared.IdGenerator;
import online.yudream.base.infra.platform.feishu.dataobj.FeishuCallQuotaDO;
import online.yudream.base.infra.platform.feishu.mapper.FeishuInfraMapper;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.MongoExpression;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * 飞书调用额度实现：计数桶持久化在 Mongo，条件自增由 findAndModify 完成，
 * 同一窗口的并发预占由存储层串行化，因此多实例部署不会超额。
 * <p>
 * 预占顺序：分钟窗口先占（突发限流最常见的拒绝路径），日窗口占用失败时把分钟计数退还，
 * 避免日额度拒绝污染分钟窗口。退还使用预占返回的窗口键，跨分钟/跨日边界不会错退到别的桶。
 */
@Service
@RequiredArgsConstructor
public class FeishuCallQuotaRepoImpl implements FeishuCallQuotaRepo {

    private static final String WINDOW_MINUTE = "MINUTE";
    private static final String WINDOW_DAY = "DAY";

    private final MongoTemplate mongo;
    private final IdGenerator ids;

    private volatile boolean indexesReady;

    private synchronized void ensureIndexes() {
        if (indexesReady) {
            return;
        }
        mongo.indexOps(FeishuCallQuotaDO.class).ensureIndex(new Index()
                .on("userId", Sort.Direction.ASC)
                .on("scope", Sort.Direction.ASC)
                .on("windowType", Sort.Direction.ASC)
                .on("windowKey", Sort.Direction.ASC)
                .unique()
                .named("feishuQuotaWindow"));
        indexesReady = true;
    }

    @Override
    public FeishuQuotaSnapshot snapshot(Long userId, FeishuQuotaScope scope, Instant now, FeishuQuotaPolicy policy) {
        ensureIndexes();
        FeishuQuotaWindowKeys keys = FeishuQuotaWindowKeys.of(now);
        FeishuCallQuotaDO minute = getOrCreate(userId, scope.code(), WINDOW_MINUTE, keys.minuteKey(), policy.perMinuteLimit());
        FeishuCallQuotaDO day = getOrCreate(userId, scope.code(), WINDOW_DAY, keys.dayKey(), policy.dailyLimit());
        return FeishuInfraMapper.quotaSnapshot(userId, scope, minute, day);
    }

    @Override
    public Optional<FeishuQuotaWindowKeys> reserve(Long userId, FeishuQuotaScope scope, Instant now, FeishuQuotaPolicy policy) {
        ensureIndexes();
        FeishuQuotaWindowKeys keys = FeishuQuotaWindowKeys.of(now);
        getOrCreate(userId, scope.code(), WINDOW_MINUTE, keys.minuteKey(), policy.perMinuteLimit());
        if (!increment(userId, scope.code(), WINDOW_MINUTE, keys.minuteKey(), policy.perMinuteLimit())) {
            return Optional.empty();
        }
        getOrCreate(userId, scope.code(), WINDOW_DAY, keys.dayKey(), policy.dailyLimit());
        if (!increment(userId, scope.code(), WINDOW_DAY, keys.dayKey(), policy.dailyLimit())) {
            decrement(userId, scope.code(), WINDOW_MINUTE, keys.minuteKey());
            return Optional.empty();
        }
        return Optional.of(keys);
    }

    @Override
    public void refund(Long userId, FeishuQuotaScope scope, FeishuQuotaWindowKeys windows) {
        if (windows == null) {
            return;
        }
        decrement(userId, scope.code(), WINDOW_MINUTE, windows.minuteKey());
        decrement(userId, scope.code(), WINDOW_DAY, windows.dayKey());
    }

    private FeishuCallQuotaDO getOrCreate(Long userId, String scope, String windowType, String windowKey, long limit) {
        Query query = bucketQuery(userId, scope, windowType, windowKey);
        Update update = new Update()
                .setOnInsert("id", ids.nextId())
                .setOnInsert("userId", userId)
                .setOnInsert("scope", scope)
                .setOnInsert("windowType", windowType)
                .setOnInsert("windowKey", windowKey)
                .setOnInsert("count", 0L)
                .setOnInsert("createTime", LocalDateTime.now())
                .set("limit", Math.max(0, limit))
                .set("updateTime", LocalDateTime.now());
        return mongo.findAndModify(query, update, FindAndModifyOptions.options().returnNew(true).upsert(true),
                FeishuCallQuotaDO.class);
    }

    /** 条件自增：仅当当前计数小于上限时才加一，返回 null 表示额度已耗尽。 */
    private boolean increment(Long userId, String scope, String windowType, String windowKey, long limit) {
        Query query = bucketQuery(userId, scope, windowType, windowKey)
                .addCriteria(Criteria.expr(MongoExpression.create("{$lt: [\"$count\", " + Math.max(0, limit) + "]}")));
        FeishuCallQuotaDO updated = mongo.findAndModify(query,
                new Update().inc("count", 1L).set("updateTime", LocalDateTime.now()),
                FindAndModifyOptions.options().returnNew(true), FeishuCallQuotaDO.class);
        return updated != null;
    }

    private void decrement(Long userId, String scope, String windowType, String windowKey) {
        mongo.updateFirst(bucketQuery(userId, scope, windowType, windowKey).addCriteria(Criteria.where("count").gt(0L)),
                new Update().inc("count", -1L).set("updateTime", LocalDateTime.now()), FeishuCallQuotaDO.class);
    }

    private Query bucketQuery(Long userId, String scope, String windowType, String windowKey) {
        return Query.query(Criteria.where("userId").is(userId)
                .and("scope").is(scope)
                .and("windowType").is(windowType)
                .and("windowKey").is(windowKey));
    }
}
