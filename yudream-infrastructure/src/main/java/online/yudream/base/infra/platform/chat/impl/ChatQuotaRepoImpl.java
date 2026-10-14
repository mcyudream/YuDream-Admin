package online.yudream.base.infra.platform.chat.impl;

import lombok.RequiredArgsConstructor;
import online.yudream.base.domain.platform.chat.aggregate.UserChatQuota;
import online.yudream.base.domain.platform.chat.repo.ChatQuotaRepo;
import online.yudream.base.domain.shared.IdGenerator;
import online.yudream.base.infra.platform.chat.dataobj.UserChatQuotaDO;
import online.yudream.base.infra.platform.chat.mapper.ChatInfraMapper;
import org.springframework.data.mongodb.MongoExpression;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ChatQuotaRepoImpl implements ChatQuotaRepo {

    private final MongoTemplate mongo;
    private final IdGenerator ids;
    private volatile boolean indexesReady;
    private synchronized void ensureIndexes() {
        if (indexesReady) return;
        mongo.indexOps(UserChatQuotaDO.class).ensureIndex(new org.springframework.data.mongodb.core.index.Index()
                .on("userId", org.springframework.data.domain.Sort.Direction.ASC)
                .on("usageDate", org.springframework.data.domain.Sort.Direction.ASC).unique().named("userDate"));
        indexesReady = true;
    }

    @Override
    public UserChatQuota save(UserChatQuota quota) {
        // 预算收据只由下面的 CAS 端口写入，普通聚合保存不能覆盖并发实耗或清空收据。
        return getOrCreate(quota.getUserId(), quota.getUsageDate(), quota.getLimitTokens());
    }

    @Override
    public Optional<UserChatQuota> findByUserAndDate(Long userId, LocalDate usageDate) {
        Query query = Query.query(Criteria.where("userId").is(userId).and("usageDate").is(usageDate));
        return Optional.ofNullable(ChatInfraMapper.quota(mongo.findOne(query, UserChatQuotaDO.class)));
    }

    @Override
    public UserChatQuota getOrCreate(Long userId, LocalDate usageDate, long limitTokens) {
        ensureIndexes();
        Query query = Query.query(Criteria.where("userId").is(userId).and("usageDate").is(usageDate));
        Update update = new Update()
                .setOnInsert("id", ids.nextId())
                .setOnInsert("userId", userId)
                .setOnInsert("usageDate", usageDate)
                .setOnInsert("usedTokens", 0L)
                .setOnInsert("reservedTokens", 0L)
                .setOnInsert("uncertainTokens", 0L)
                .setOnInsert("createTime", LocalDateTime.now())
                .set("limitTokens", Math.max(0, limitTokens))
                .set("updateTime", LocalDateTime.now());
        UserChatQuotaDO updated = mongo.findAndModify(
                query,
                update,
                FindAndModifyOptions.options().returnNew(true).upsert(true),
                UserChatQuotaDO.class);
        return ChatInfraMapper.quota(updated);
    }

    @Override
    public Optional<UserChatQuota> addUsage(Long userId, LocalDate usageDate, long tokens) {
        long amount = Math.max(0, tokens);
        Query query = Query.query(Criteria.where("userId").is(userId)
                .and("usageDate").is(usageDate)
                .andOperator(Criteria.expr(MongoExpression.create(
                        "{$lte: [\"$usedTokens\", {$subtract: [\"$limitTokens\", " + amount + "]}]}"))));
        Update update = new Update().inc("usedTokens", amount).set("updateTime", LocalDateTime.now());
        UserChatQuotaDO updated = mongo.findAndModify(
                query,
                update,
                FindAndModifyOptions.options().returnNew(true),
                UserChatQuotaDO.class);
        return Optional.ofNullable(ChatInfraMapper.quota(updated));
    }

    @Override
    public Optional<UserChatQuota> reserve(Long userId, LocalDate usageDate, String reservationId, long tokens,
                                          String runId, java.time.Instant leaseExpiresAt) {
        long amount = Math.max(1, tokens);
        Query query = Query.query(Criteria.where("userId").is(userId).and("usageDate").is(usageDate)
                .and("reservations." + reservationId).exists(false)
                .andOperator(Criteria.expr(MongoExpression.create(
                        "{$lte: [{$add:[\"$usedTokens\",{$ifNull:[\"$reservedTokens\",0]},{$ifNull:[\"$uncertainTokens\",0]}," + amount + "]},\"$limitTokens\"]}"))));
        UserChatQuotaDO updated = mongo.findAndModify(query,
                new Update().inc("reservedTokens", amount).set("reservations." + reservationId + ".tokens", amount)
                        .set("reservations." + reservationId + ".runId", runId)
                        .set("reservations." + reservationId + ".leaseExpiresAt", leaseExpiresAt)
                        .set("updateTime", LocalDateTime.now()),
                FindAndModifyOptions.options().returnNew(true), UserChatQuotaDO.class);
        return Optional.ofNullable(ChatInfraMapper.quota(updated));
    }

    @Override
    public Optional<UserChatQuota> settle(Long userId, LocalDate usageDate, String reservationId,
                                         long reservedTokens, long usedTokens) {
        Query query = Query.query(Criteria.where("userId").is(userId).and("usageDate").is(usageDate)
                .and("reservations." + reservationId + ".tokens").is(reservedTokens));
        UserChatQuotaDO updated = mongo.findAndModify(query,
                new Update().inc("reservedTokens", -reservedTokens).inc("usedTokens", Math.max(0, usedTokens))
                        .set("settledReservations." + reservationId, Math.max(0, usedTokens))
                        .unset("reservations." + reservationId).set("updateTime", LocalDateTime.now()),
                FindAndModifyOptions.options().returnNew(true), UserChatQuotaDO.class);
        if (updated == null && usedTokens > 0) {
            // 租约清理只能释放预算；迟到的真实用量仍可从零收费收据 CAS 核销一次。
            updated = mongo.findAndModify(Query.query(Criteria.where("userId").is(userId).and("usageDate").is(usageDate)
                            .and("settledReservations." + reservationId).is(0L)),
                    new Update().inc("usedTokens", usedTokens).set("settledReservations." + reservationId, usedTokens)
                            .set("updateTime", LocalDateTime.now()), FindAndModifyOptions.options().returnNew(true), UserChatQuotaDO.class);
        }
        if (updated == null) {
            UserChatQuotaDO current = mongo.findOne(Query.query(Criteria.where("userId").is(userId).and("usageDate").is(usageDate)), UserChatQuotaDO.class);
            var pending = current == null || current.getUncertainReservations() == null ? null : current.getUncertainReservations().get(reservationId);
            if (pending != null && (pending.getTokens() == reservedTokens || pending.getOriginalTokens() == reservedTokens)
                    && usedTokens >= pending.getKnownTokens()) {
                // 跨实例管理员核对或迟到 actual 只补一次，已确认部分不会二次计费。
                Criteria pendingCriteria = Criteria.where("userId").is(userId).and("usageDate").is(usageDate)
                        .and("uncertainReservations." + reservationId + ".tokens").is(pending.getTokens());
                if (pending.getKnownTokens() > 0) pendingCriteria.and("uncertainReservations." + reservationId + ".knownTokens").is(pending.getKnownTokens());
                updated = mongo.findAndModify(Query.query(pendingCriteria),
                        new Update().inc("uncertainTokens", -pending.getTokens()).inc("usedTokens", usedTokens - pending.getKnownTokens())
                                .set("settledReservations." + reservationId, usedTokens).unset("uncertainReservations." + reservationId)
                                .set("updateTime", LocalDateTime.now()), FindAndModifyOptions.options().returnNew(true), UserChatQuotaDO.class);
            }
        }
        return Optional.ofNullable(ChatInfraMapper.quota(updated));
    }

    @Override
    public java.util.List<online.yudream.base.domain.platform.ai.valobj.AiUsageReservation> reservations(Long userId, LocalDate date) {
        UserChatQuotaDO quota = mongo.findOne(Query.query(Criteria.where("userId").is(userId).and("usageDate").is(date)), UserChatQuotaDO.class);
        if (quota == null || quota.getReservations() == null) return java.util.List.of();
        return quota.getReservations().entrySet().stream().map(entry -> new online.yudream.base.domain.platform.ai.valobj.AiUsageReservation(
                entry.getKey(), userId, date, entry.getValue().getTokens(), entry.getValue().getRunId(), entry.getValue().getLeaseExpiresAt(), entry.getValue().isModelStarted())).toList();
    }

    @Override
    public void renew(Long userId, LocalDate date, String reservationId, java.time.Instant expiresAt) {
        mongo.updateFirst(Query.query(Criteria.where("userId").is(userId).and("usageDate").is(date)
                        .and("reservations." + reservationId).exists(true)),
                new Update().set("reservations." + reservationId + ".leaseExpiresAt", expiresAt), UserChatQuotaDO.class);
    }
    @Override
    public void markStarted(Long userId, LocalDate date, String id) {
        mongo.updateFirst(Query.query(Criteria.where("userId").is(userId).and("usageDate").is(date).and("reservations." + id).exists(true)),
                new Update().set("reservations." + id + ".modelStarted", true), UserChatQuotaDO.class);
    }
    @Override
    public void uncertain(online.yudream.base.domain.platform.ai.valobj.AiUsageReservation reservation) {
        uncertain(reservation, online.yudream.base.domain.platform.ai.valobj.AiUsage.empty());
    }
    @Override
    public void uncertain(online.yudream.base.domain.platform.ai.valobj.AiUsageReservation reservation,
                          online.yudream.base.domain.platform.ai.valobj.AiUsage knownUsage) {
        String id = reservation.id();
        long known = knownUsage == null ? 0 : Math.max(0, knownUsage.totalTokens());
        long held = Math.max(0, reservation.reservedTokens() - known);
        mongo.updateFirst(Query.query(Criteria.where("userId").is(reservation.userId()).and("usageDate").is(reservation.usageDate())
                        .and("reservations." + id + ".tokens").is(reservation.reservedTokens())),
                new Update().inc("reservedTokens", -reservation.reservedTokens()).inc("uncertainTokens", held).inc("usedTokens", known)
                        .set("uncertainReservations." + id + ".tokens", held)
                        .set("uncertainReservations." + id + ".knownTokens", known)
                        .set("uncertainReservations." + id + ".originalTokens", reservation.reservedTokens())
                        .set("uncertainReservations." + id + ".runId", reservation.runId())
                        .set("uncertainReservations." + id + ".modelStarted", true).unset("reservations." + id)
                        .set("updateTime", LocalDateTime.now()), UserChatQuotaDO.class);
    }
    @Override
    public java.util.List<online.yudream.base.domain.platform.ai.valobj.AiUsageReservation> unresolved(Long userId, LocalDate date) {
        UserChatQuotaDO quota = mongo.findOne(Query.query(Criteria.where("userId").is(userId).and("usageDate").is(date)), UserChatQuotaDO.class);
        if (quota == null || quota.getUncertainReservations() == null) return java.util.List.of();
        return quota.getUncertainReservations().entrySet().stream().map(entry -> new online.yudream.base.domain.platform.ai.valobj.AiUsageReservation(
                entry.getKey(), userId, date, entry.getValue().getTokens(), entry.getValue().getRunId(), null, true, entry.getValue().getKnownTokens())).toList();
    }
}
