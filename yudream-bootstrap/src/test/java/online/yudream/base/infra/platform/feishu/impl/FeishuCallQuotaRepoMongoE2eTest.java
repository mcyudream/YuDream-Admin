package online.yudream.base.infra.platform.feishu.impl;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import online.yudream.base.domain.platform.feishu.enumerate.FeishuQuotaScope;
import online.yudream.base.domain.platform.feishu.valobj.FeishuQuotaPolicy;
import online.yudream.base.domain.platform.feishu.valobj.FeishuQuotaSnapshot;
import online.yudream.base.domain.platform.feishu.valobj.FeishuQuotaWindowKeys;
import online.yudream.base.domain.shared.IdGenerator;
import online.yudream.base.infra.platform.feishu.dataobj.FeishuCallQuotaDO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 真实 Mongo 下的额度原子性验证：两个独立 MongoTemplate（模拟两个应用实例）并发预占，
 * 成功次数恰好等于上限；计数对第三个读取实例可见，证明额度不是进程内计数。
 * <p>
 * 默认不执行，需要本机 Mongo 时用
 * {@code -Dyudream.feishu.mongoE2e=true -Dyudream.feishu.mongoUri=mongodb://127.0.0.1:27017} 打开。
 */
@EnabledIfSystemProperty(named = "yudream.feishu.mongoE2e", matches = "true")
class FeishuCallQuotaRepoMongoE2eTest {

    private static final String URI = System.getProperty("yudream.feishu.mongoUri", "mongodb://127.0.0.1:27017");
    private static final String DATABASE = "yudream_feishu_quota_e2e";

    private MongoClient clientA;
    private MongoClient clientB;
    private MongoTemplate templateA;
    private MongoTemplate templateB;
    private FeishuCallQuotaRepoImpl instanceA;
    private FeishuCallQuotaRepoImpl instanceB;

    @BeforeEach
    void connect() {
        clientA = MongoClients.create(URI);
        clientB = MongoClients.create(URI);
        templateA = new MongoTemplate(clientA, DATABASE);
        templateB = new MongoTemplate(clientB, DATABASE);
        templateA.dropCollection(FeishuCallQuotaDO.class);
        instanceA = new FeishuCallQuotaRepoImpl(templateA, ids());
        instanceB = new FeishuCallQuotaRepoImpl(templateB, ids());
    }

    @AfterEach
    void disconnect() {
        if (clientA != null) {
            clientA.close();
        }
        if (clientB != null) {
            clientB.close();
        }
    }

    @Test
    void concurrentReservesAcrossTwoInstancesNeverExceedMinuteLimit() throws Exception {
        FeishuQuotaPolicy policy = new FeishuQuotaPolicy(50, 5_000);
        Instant now = Instant.now();

        int granted = race(200, policy, now);

        assertEquals(50, granted);
        FeishuQuotaSnapshot snapshot = instanceB.snapshot(7L, FeishuQuotaScope.CLOUD_DOC, now, policy);
        assertEquals(50L, snapshot.minuteUsed());
        assertEquals(50L, snapshot.dayUsed());
        assertEquals(50L, snapshot.minuteLimit());
    }

    @Test
    void dailyLimitBindsAcrossMinuteWindows() throws Exception {
        FeishuQuotaPolicy policy = new FeishuQuotaPolicy(1_000, 30);
        Instant now = Instant.now();

        int granted = race(200, policy, now);

        assertEquals(30, granted);
        assertEquals(30L, instanceA.snapshot(7L, FeishuQuotaScope.CLOUD_DOC, now, policy).dayUsed());
    }

    @Test
    void refundReturnsReservedCapacityToBothWindows() {
        FeishuQuotaPolicy policy = new FeishuQuotaPolicy(1, 1);
        Instant now = Instant.now();

        FeishuQuotaWindowKeys windows = instanceA.reserve(9L, FeishuQuotaScope.CLOUD_DOC, now, policy).orElseThrow();
        assertTrue(instanceB.reserve(9L, FeishuQuotaScope.CLOUD_DOC, now, policy).isEmpty());

        instanceB.refund(9L, FeishuQuotaScope.CLOUD_DOC, windows);

        assertTrue(instanceA.reserve(9L, FeishuQuotaScope.CLOUD_DOC, now, policy).isPresent());
        FeishuQuotaSnapshot snapshot = instanceB.snapshot(9L, FeishuQuotaScope.CLOUD_DOC, now, policy);
        assertEquals(1L, snapshot.minuteUsed());
        assertEquals(1L, snapshot.dayUsed());
    }

    @Test
    void differentUsersAndScopesDoNotShareBuckets() {
        FeishuQuotaPolicy policy = new FeishuQuotaPolicy(1, 1);
        Instant now = Instant.now();

        assertTrue(instanceA.reserve(1L, FeishuQuotaScope.CLOUD_DOC, now, policy).isPresent());
        assertTrue(instanceA.reserve(2L, FeishuQuotaScope.CLOUD_DOC, now, policy).isPresent());
        assertTrue(instanceB.reserve(1L, FeishuQuotaScope.CLOUD_DOC, now, policy).isEmpty());
        assertEquals(1L, instanceB.snapshot(2L, FeishuQuotaScope.CLOUD_DOC, now, policy).minuteUsed());
    }

    private int race(int attempts, FeishuQuotaPolicy policy, Instant now) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(16);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger granted = new AtomicInteger();
        List<Future<?>> futures = new ArrayList<>();
        try {
            for (int index = 0; index < attempts; index++) {
                FeishuCallQuotaRepoImpl target = index % 2 == 0 ? instanceA : instanceB;
                futures.add(pool.submit(() -> {
                    start.await();
                    if (target.reserve(7L, FeishuQuotaScope.CLOUD_DOC, now, policy).isPresent()) {
                        granted.incrementAndGet();
                    }
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> future : futures) {
                future.get();
            }
        }
        finally {
            pool.shutdownNow();
        }
        return granted.get();
    }

    private static IdGenerator ids() {
        AtomicLong sequence = new AtomicLong(System.currentTimeMillis());
        return sequence::incrementAndGet;
    }
}
