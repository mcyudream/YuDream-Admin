package online.yudream.base.infra.platform.feishu.impl;

import online.yudream.base.domain.platform.feishu.enumerate.FeishuQuotaScope;
import online.yudream.base.domain.platform.feishu.valobj.FeishuQuotaPolicy;
import online.yudream.base.domain.platform.feishu.valobj.FeishuQuotaSnapshot;
import online.yudream.base.domain.platform.feishu.valobj.FeishuQuotaWindowKeys;
import online.yudream.base.infra.platform.feishu.dataobj.FeishuCallQuotaDO;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.mongodb.MongoExpression;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.index.IndexOperations;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 额度仓储的存储层契约：预占必须是「条件自增（CAS）+ 唯一窗口键」，不是读改写；
 * 日窗口拒绝要退还分钟窗口；退还只作用于预占返回的窗口键。
 * <p>
 * 多实例原子性由真实 Mongo 端到端测试证明（{@code FeishuCallQuotaRepoMongoE2eTest}），
 * 本测试锁定交给存储层的查询与更新形态。
 */
class FeishuCallQuotaRepoImplTest {

    private static final Instant NOW = Instant.parse("2026-10-16T03:00:00Z");
    private static final FeishuQuotaPolicy POLICY = new FeishuQuotaPolicy(20, 500);

    private final MongoTemplate mongo = mock(MongoTemplate.class);
    private final IndexOperations indexOps = mock(IndexOperations.class);
    private final AtomicLong ids = new AtomicLong(1000L);
    private final FeishuCallQuotaRepoImpl repo = new FeishuCallQuotaRepoImpl(mongo, ids::incrementAndGet);

    @Test
    void createsUniqueWindowIndexOnlyOnce() {
        when(mongo.indexOps(FeishuCallQuotaDO.class)).thenReturn(indexOps);
        stubFindAndModify(Set.of());

        repo.snapshot(7L, FeishuQuotaScope.CLOUD_DOC, NOW, POLICY);
        repo.snapshot(7L, FeishuQuotaScope.CLOUD_DOC, NOW, POLICY);

        ArgumentCaptor<Index> index = ArgumentCaptor.forClass(Index.class);
        verify(indexOps, times(1)).ensureIndex(index.capture());
        assertTrue(index.getValue().getIndexOptions().getBoolean("unique", false));
        assertEquals(List.of("userId", "scope", "windowType", "windowKey"),
                new ArrayList<>(index.getValue().getIndexKeys().keySet()));
    }

    @Test
    void snapshotReadsBothWindowsWithConfiguredLimits() {
        when(mongo.indexOps(FeishuCallQuotaDO.class)).thenReturn(indexOps);
        stubFindAndModify(Set.of());

        FeishuQuotaSnapshot snapshot = repo.snapshot(7L, FeishuQuotaScope.CLOUD_DOC, NOW, POLICY);

        FeishuQuotaWindowKeys keys = FeishuQuotaWindowKeys.of(NOW);
        assertEquals(keys.minuteKey(), snapshot.minuteKey());
        assertEquals(keys.dayKey(), snapshot.dayKey());
        assertEquals(0L, snapshot.minuteUsed());
        assertEquals(20L, snapshot.minuteLimit());
        assertEquals(0L, snapshot.dayUsed());
        assertEquals(500L, snapshot.dayLimit());
        assertEquals(FeishuQuotaScope.CLOUD_DOC, snapshot.scope());
        assertEquals(7L, snapshot.userId());
    }

    @Test
    void reserveConditionallyIncrementsBothWindows() {
        when(mongo.indexOps(FeishuCallQuotaDO.class)).thenReturn(indexOps);
        stubFindAndModify(Set.of());

        Optional<FeishuQuotaWindowKeys> reserved = repo.reserve(7L, FeishuQuotaScope.CLOUD_DOC, NOW, POLICY);

        assertTrue(reserved.isPresent());
        assertEquals(FeishuQuotaWindowKeys.of(NOW), reserved.get());
        List<Captured> increments = increments();
        assertEquals(List.of("MINUTE", "DAY"), windowTypesOf(increments));
        for (Captured increment : increments) {
            assertEquals(1L, increment.update().getUpdateObject().get("$inc", Document.class).get("count"));
        }
        // 自增带 $lt 上限条件，并发调用不会突破额度
        String minuteCondition = limitCondition(increments.get(0));
        assertTrue(minuteCondition.contains("$lt"));
        assertTrue(minuteCondition.contains("$count"));
        assertTrue(minuteCondition.contains("20"));
        assertTrue(limitCondition(increments.get(1)).contains("500"));
    }

    @Test
    void reserveRejectsOnMinuteExhaustionWithoutTouchingDayWindow() {
        when(mongo.indexOps(FeishuCallQuotaDO.class)).thenReturn(indexOps);
        stubFindAndModify(Set.of("MINUTE"));

        Optional<FeishuQuotaWindowKeys> reserved = repo.reserve(7L, FeishuQuotaScope.CLOUD_DOC, NOW, POLICY);

        assertFalse(reserved.isPresent());
        List<Captured> increments = increments();
        assertEquals(List.of("MINUTE"), windowTypesOf(increments));
        verify(mongo, times(0)).updateFirst(any(Query.class), any(Update.class), eq(FeishuCallQuotaDO.class));
    }

    @Test
    void reserveRefundsMinuteWindowWhenDayWindowExhausted() {
        when(mongo.indexOps(FeishuCallQuotaDO.class)).thenReturn(indexOps);
        stubFindAndModify(Set.of("DAY"));

        Optional<FeishuQuotaWindowKeys> reserved = repo.reserve(7L, FeishuQuotaScope.CLOUD_DOC, NOW, POLICY);

        assertFalse(reserved.isPresent());
        assertEquals(List.of("MINUTE", "DAY"), windowTypesOf(increments()));
        ArgumentCaptor<Query> query = ArgumentCaptor.forClass(Query.class);
        ArgumentCaptor<Update> update = ArgumentCaptor.forClass(Update.class);
        verify(mongo).updateFirst(query.capture(), update.capture(), eq(FeishuCallQuotaDO.class));
        assertTrue(query.getValue().getQueryObject().toJson().contains("MINUTE"));
        assertTrue(query.getValue().getQueryObject().toJson().contains("$gt"));
        assertEquals(-1L, update.getValue().getUpdateObject().get("$inc", Document.class).get("count"));
    }

    @Test
    void refundOnlyTouchesWindowsReturnedByReserve() {
        when(mongo.indexOps(FeishuCallQuotaDO.class)).thenReturn(indexOps);
        stubFindAndModify(Set.of());

        FeishuQuotaWindowKeys windows = repo.reserve(7L, FeishuQuotaScope.CLOUD_DOC, NOW, POLICY).orElseThrow();
        repo.refund(7L, FeishuQuotaScope.CLOUD_DOC, windows);

        ArgumentCaptor<Query> query = ArgumentCaptor.forClass(Query.class);
        ArgumentCaptor<Update> update = ArgumentCaptor.forClass(Update.class);
        verify(mongo, times(2)).updateFirst(query.capture(), update.capture(), eq(FeishuCallQuotaDO.class));
        List<String> keys = query.getAllValues().stream()
                .map(item -> String.valueOf(item.getQueryObject().get("windowKey")))
                .toList();
        assertEquals(List.of(windows.minuteKey(), windows.dayKey()), keys);
        for (Update item : update.getAllValues()) {
            assertEquals(-1L, item.getUpdateObject().get("$inc", Document.class).get("count"));
        }
    }

    @Test
    void refundWithoutReservationIsIgnored() {
        repo.refund(7L, FeishuQuotaScope.CLOUD_DOC, null);
        verifyNoInteractions(mongo);
    }

    private void stubFindAndModify(Set<String> exhaustedWindowTypes) {
        when(mongo.findAndModify(any(Query.class), any(Update.class), any(FindAndModifyOptions.class), eq(FeishuCallQuotaDO.class)))
                .thenAnswer(invocation -> {
                    Query query = invocation.getArgument(0);
                    Update update = invocation.getArgument(1);
                    Document document = update.getUpdateObject();
                    String windowType = String.valueOf(query.getQueryObject().get("windowType"));
                    FeishuCallQuotaDO result = new FeishuCallQuotaDO();
                    result.setWindowType(windowType);
                    result.setWindowKey(String.valueOf(query.getQueryObject().get("windowKey")));
                    if (document.containsKey("$inc")) {
                        if (exhaustedWindowTypes.contains(windowType)) {
                            return null;
                        }
                        result.setCount(1L);
                        return result;
                    }
                    Document set = document.get("$set", Document.class);
                    result.setCount(0L);
                    result.setLimit(set == null ? 0L : ((Number) set.get("limit")).longValue());
                    return result;
                });
    }

    private List<Captured> findAndModifyCalls() {
        ArgumentCaptor<Query> queries = ArgumentCaptor.forClass(Query.class);
        ArgumentCaptor<Update> updates = ArgumentCaptor.forClass(Update.class);
        verify(mongo, atLeastOnce()).findAndModify(queries.capture(), updates.capture(),
                any(FindAndModifyOptions.class), eq(FeishuCallQuotaDO.class));
        List<Captured> captured = new ArrayList<>();
        for (int index = 0; index < queries.getAllValues().size(); index++) {
            captured.add(new Captured(queries.getAllValues().get(index), updates.getAllValues().get(index)));
        }
        return captured;
    }

    private List<Captured> increments() {
        return findAndModifyCalls().stream()
                .filter(call -> call.update().getUpdateObject().containsKey("$inc"))
                .toList();
    }

    private List<String> windowTypesOf(List<Captured> calls) {
        return calls.stream()
                .map(call -> String.valueOf(call.query().getQueryObject().get("windowType")))
                .toList();
    }

    /** 取条件自增查询里的 $expr 上限表达式（$lt: ["$count", limit]），Document 本身不可直接 toJson。 */
    private String limitCondition(Captured call) {
        return findExpression(call.query().getQueryObject());
    }

    private String findExpression(Object node) {
        if (node instanceof MongoExpression expression) {
            return expression.toDocument().toJson();
        }
        if (node instanceof Document document) {
            for (Object value : document.values()) {
                String found = findExpression(value);
                if (!found.isEmpty()) {
                    return found;
                }
            }
        }
        if (node instanceof List<?> list) {
            for (Object value : list) {
                String found = findExpression(value);
                if (!found.isEmpty()) {
                    return found;
                }
            }
        }
        if (node instanceof Object[] array) {
            for (Object value : array) {
                String found = findExpression(value);
                if (!found.isEmpty()) {
                    return found;
                }
            }
        }
        return "";
    }

    private record Captured(Query query, Update update) {
    }
}
