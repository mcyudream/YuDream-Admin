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

    @Override
    public UserChatQuota save(UserChatQuota quota) {
        UserChatQuotaDO dataObj = ChatInfraMapper.quota(quota);
        if (dataObj.getId() == null) {
            dataObj.setId(ids.nextId());
            dataObj.setCreateTime(LocalDateTime.now());
        }
        dataObj.setUpdateTime(LocalDateTime.now());
        return ChatInfraMapper.quota(mongo.save(dataObj));
    }

    @Override
    public Optional<UserChatQuota> findByUserAndDate(Long userId, LocalDate usageDate) {
        Query query = Query.query(Criteria.where("userId").is(userId).and("usageDate").is(usageDate));
        return Optional.ofNullable(ChatInfraMapper.quota(mongo.findOne(query, UserChatQuotaDO.class)));
    }

    @Override
    public UserChatQuota getOrCreate(Long userId, LocalDate usageDate, long limitTokens) {
        Query query = Query.query(Criteria.where("userId").is(userId).and("usageDate").is(usageDate));
        Update update = new Update()
                .setOnInsert("id", ids.nextId())
                .setOnInsert("userId", userId)
                .setOnInsert("usageDate", usageDate)
                .setOnInsert("usedTokens", 0L)
                .setOnInsert("limitTokens", Math.max(0, limitTokens))
                .setOnInsert("createTime", LocalDateTime.now())
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
}
