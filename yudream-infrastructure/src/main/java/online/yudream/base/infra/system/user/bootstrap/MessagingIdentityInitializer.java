package online.yudream.base.infra.system.user.bootstrap;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import online.yudream.base.domain.system.user.aggregate.MessagingIdentity;
import online.yudream.base.domain.system.user.enumerate.MessagingIdentityType;
import online.yudream.base.domain.system.user.repo.MessagingIdentityRepo;
import online.yudream.base.domain.system.user.service.MessagingIdentityClassifier;
import online.yudream.base.infra.system.user.dataobj.MessagingIdentityDO;
import online.yudream.base.infra.system.user.dataobj.UserDO;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.index.PartialIndexFilter;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 确保消息身份唯一索引，并把历史 {@code sysUser.qq} 迁到 {@code sysMessagingIdentity}。
 * 数字 QQ 写成 milky/qq；已经误写入的官方 openid 写成 official/user_openid，查找仍能命中。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class MessagingIdentityInitializer implements ApplicationListener<ApplicationReadyEvent> {

    private final MongoTemplate mongoTemplate;
    private final MessagingIdentityRepo identityRepo;
    private final online.yudream.base.domain.shared.IdGenerator idGenerator;

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        ensureIndexes();
        migrateLegacyQq();
    }

    private void ensureIndexes() {
        mongoTemplate.indexOps(MessagingIdentityDO.class).ensureIndex(new Index()
                .on("identityType", Sort.Direction.ASC)
                .on("identity", Sort.Direction.ASC)
                .named("uk_messaging_identity_milky_qq")
                .unique()
                .partial(PartialIndexFilter.of(Criteria.where("identityType").is(MessagingIdentityType.QQ.code()))));
        mongoTemplate.indexOps(MessagingIdentityDO.class).ensureIndex(new Index()
                .on("identityType", Sort.Direction.ASC)
                .on("appId", Sort.Direction.ASC)
                .on("identity", Sort.Direction.ASC)
                .named("uk_messaging_identity_official_user")
                .unique()
                .partial(PartialIndexFilter.of(Criteria.where("identityType").is(MessagingIdentityType.USER_OPENID.code()))));
        mongoTemplate.indexOps(MessagingIdentityDO.class).ensureIndex(new Index()
                .on("identityType", Sort.Direction.ASC)
                .on("appId", Sort.Direction.ASC)
                .on("groupOpenid", Sort.Direction.ASC)
                .on("identity", Sort.Direction.ASC)
                .named("uk_messaging_identity_official_member")
                .unique()
                .partial(PartialIndexFilter.of(Criteria.where("identityType").is(MessagingIdentityType.MEMBER_OPENID.code()))));
        mongoTemplate.indexOps(MessagingIdentityDO.class).ensureIndex(new Index()
                .on("userId", Sort.Direction.ASC)
                .named("idx_messaging_identity_user"));
    }

    private void migrateLegacyQq() {
        Query query = Query.query(Criteria.where("qq").exists(true).ne(""));
        List<UserDO> users = mongoTemplate.find(query, UserDO.class);
        int migrated = 0;
        int skipped = 0;
        int official = 0;
        for (UserDO user : users) {
            if (user.getId() == null || !StringUtils.hasText(user.getQq())) {
                skipped++;
                continue;
            }
            MessagingIdentityClassifier.Classification classification;
            try {
                classification = MessagingIdentityClassifier.classifyStoredQq(user.getQq());
            } catch (RuntimeException ignored) {
                skipped++;
                continue;
            }
            if (identityRepo.findExact(classification.protocol(), classification.identityType(),
                    classification.appId(), classification.groupOpenid(), classification.identity()).isPresent()) {
                skipped++;
                continue;
            }
            MessagingIdentity identity = MessagingIdentity.bind(user.getId(), classification.protocol(), null,
                    classification.appId(), classification.identityType(), classification.identity(), classification.groupOpenid());
            identity.setId(idGenerator.nextId());
            identity.setCreateTime(LocalDateTime.now());
            identity.setUpdateTime(identity.getCreateTime());
            identityRepo.save(identity);
            migrated++;
            if (classification.identityType().officialUserOpenid()) {
                official++;
            }
        }
        log.info("Migrated legacy User.qq into messaging identities: migrated={}, skipped={}, officialOpenid={}",
                migrated, skipped, official);
    }
}
