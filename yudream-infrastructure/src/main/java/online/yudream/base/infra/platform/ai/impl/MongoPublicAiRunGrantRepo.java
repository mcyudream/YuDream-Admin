package online.yudream.base.infra.platform.ai.impl;

import lombok.RequiredArgsConstructor;
import online.yudream.base.domain.platform.ai.repo.PublicAiRunGrantRepo;
import online.yudream.base.domain.platform.ai.valobj.PublicAiRunGrant;
import online.yudream.base.infra.platform.ai.dataobj.PublicAiRunGrantDO;
import online.yudream.base.infra.platform.ai.mapper.PublicAiRunGrantInfraMapper;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;
import java.time.Instant;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class MongoPublicAiRunGrantRepo implements PublicAiRunGrantRepo {
    private final MongoTemplate mongo;
    private volatile boolean indexed;

    @Override public void save(PublicAiRunGrant grant) {
        ensureIndexes();
        mongo.insert(PublicAiRunGrantInfraMapper.toData(grant));
    }
    private synchronized void ensureIndexes() {
        if (indexed) return;
        var indexes = mongo.indexOps(PublicAiRunGrantDO.class);
        indexes.ensureIndex(new org.springframework.data.mongodb.core.index.Index().on("expiresAt", org.springframework.data.domain.Sort.Direction.ASC)
                .expire(java.time.Duration.ZERO).named("ai_public_grant_expiry"));
        indexes.ensureIndex(new org.springframework.data.mongodb.core.index.Index().on("tokenHash", org.springframework.data.domain.Sort.Direction.ASC)
                .on("spaceSlug", org.springframework.data.domain.Sort.Direction.ASC).on("expiresAt", org.springframework.data.domain.Sort.Direction.ASC)
                .named("ai_public_grant_conversation"));
        indexed = true;
    }
    @Override public Optional<PublicAiRunGrant> find(String id, String hash, String slug, Instant now) {
        return read(new Query(valid(hash, slug, now).and("_id").is(id)));
    }
    @Override public Optional<PublicAiRunGrant> conversation(String hash, String slug, Instant now) {
        return read(new Query(valid(hash, slug, now)).limit(1));
    }
    private Criteria valid(String hash, String slug, Instant now) {
        return Criteria.where("tokenHash").is(hash).and("spaceSlug").is(slug).and("expiresAt").gt(now);
    }
    private Optional<PublicAiRunGrant> read(Query query) {
        return Optional.ofNullable(mongo.findOne(query, PublicAiRunGrantDO.class))
                .map(PublicAiRunGrantInfraMapper::toDomain);
    }
}
