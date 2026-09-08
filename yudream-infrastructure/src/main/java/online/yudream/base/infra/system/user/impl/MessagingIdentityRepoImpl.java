package online.yudream.base.infra.system.user.impl;

import lombok.RequiredArgsConstructor;
import online.yudream.base.domain.platform.milky.enumerate.MilkyConnectionProtocol;
import online.yudream.base.domain.shared.IdGenerator;
import online.yudream.base.domain.system.user.aggregate.MessagingIdentity;
import online.yudream.base.domain.system.user.enumerate.MessagingIdentityType;
import online.yudream.base.domain.system.user.repo.MessagingIdentityRepo;
import online.yudream.base.infra.system.user.dataobj.MessagingIdentityDO;
import online.yudream.base.infra.system.user.mapper.MessagingIdentityInfraMapper;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class MessagingIdentityRepoImpl implements MessagingIdentityRepo {

    private final MongoTemplate mongoTemplate;
    private final IdGenerator idGenerator;

    @Override
    public MessagingIdentity save(MessagingIdentity identity) {
        MessagingIdentityDO data = MessagingIdentityInfraMapper.toDataObj(identity);
        if (data.getId() == null) {
            data.setId(idGenerator.nextId());
            data.setCreateTime(LocalDateTime.now());
        }
        data.setUpdateTime(LocalDateTime.now());
        return MessagingIdentityInfraMapper.toDomain(mongoTemplate.save(data));
    }

    @Override
    public Optional<MessagingIdentity> findById(Long id) {
        if (id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(MessagingIdentityInfraMapper.toDomain(mongoTemplate.findById(id, MessagingIdentityDO.class)));
    }

    @Override
    public List<MessagingIdentity> findByUserId(Long userId) {
        if (userId == null) {
            return List.of();
        }
        return mongoTemplate.find(Query.query(Criteria.where("userId").is(userId)), MessagingIdentityDO.class).stream()
                .map(MessagingIdentityInfraMapper::toDomain)
                .toList();
    }

    @Override
    public List<MessagingIdentity> findByUserIds(List<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return List.of();
        }
        List<Long> ids = userIds.stream().filter(id -> id != null).distinct().toList();
        if (ids.isEmpty()) {
            return List.of();
        }
        return mongoTemplate.find(Query.query(Criteria.where("userId").in(ids)), MessagingIdentityDO.class).stream()
                .map(MessagingIdentityInfraMapper::toDomain)
                .toList();
    }

    @Override
    public Optional<MessagingIdentity> findMilkyQq(String identity) {
        if (!StringUtils.hasText(identity)) {
            return Optional.empty();
        }
        Query query = Query.query(Criteria.where("identityType").is(MessagingIdentityType.QQ.code())
                .and("identity").is(identity.trim()));
        return Optional.ofNullable(MessagingIdentityInfraMapper.toDomain(mongoTemplate.findOne(query, MessagingIdentityDO.class)));
    }

    @Override
    public Optional<MessagingIdentity> findOfficialUserOpenid(String appId, String identity) {
        if (!StringUtils.hasText(identity)) {
            return Optional.empty();
        }
        Criteria criteria = Criteria.where("identityType").is(MessagingIdentityType.USER_OPENID.code())
                .and("identity").is(identity.trim());
        if (StringUtils.hasText(appId)) {
            criteria = criteria.and("appId").is(appId.trim());
        } else {
            criteria = criteria.and("appId").is(null);
        }
        return Optional.ofNullable(MessagingIdentityInfraMapper.toDomain(
                mongoTemplate.findOne(Query.query(criteria), MessagingIdentityDO.class)));
    }

    @Override
    public Optional<MessagingIdentity> findOfficialMemberOpenid(String appId, String groupOpenid, String identity) {
        if (!StringUtils.hasText(identity) || !StringUtils.hasText(groupOpenid)) {
            return Optional.empty();
        }
        Criteria criteria = Criteria.where("identityType").is(MessagingIdentityType.MEMBER_OPENID.code())
                .and("identity").is(identity.trim())
                .and("groupOpenid").is(groupOpenid.trim());
        if (StringUtils.hasText(appId)) {
            criteria = criteria.and("appId").is(appId.trim());
        } else {
            criteria = criteria.and("appId").is(null);
        }
        return Optional.ofNullable(MessagingIdentityInfraMapper.toDomain(
                mongoTemplate.findOne(Query.query(criteria), MessagingIdentityDO.class)));
    }

    @Override
    public List<MessagingIdentity> findByIdentity(String identity) {
        if (!StringUtils.hasText(identity)) {
            return List.of();
        }
        return mongoTemplate.find(Query.query(Criteria.where("identity").is(identity.trim())), MessagingIdentityDO.class)
                .stream()
                .map(MessagingIdentityInfraMapper::toDomain)
                .toList();
    }

    @Override
    public Optional<MessagingIdentity> findExact(MilkyConnectionProtocol protocol, MessagingIdentityType identityType,
                                                 String appId, String groupOpenid, String identity) {
        if (identityType == null) {
            return Optional.empty();
        }
        return switch (identityType) {
            case QQ -> findMilkyQq(identity);
            case USER_OPENID -> findOfficialUserOpenid(appId, identity);
            case MEMBER_OPENID -> findOfficialMemberOpenid(appId, groupOpenid, identity);
        };
    }

    @Override
    public void deleteById(Long id) {
        if (id == null) {
            return;
        }
        mongoTemplate.remove(Query.query(Criteria.where("id").is(id)), MessagingIdentityDO.class);
    }

    @Override
    public void deleteMilkyQqByUserId(Long userId) {
        if (userId == null) {
            return;
        }
        mongoTemplate.remove(Query.query(Criteria.where("userId").is(userId)
                .and("identityType").is(MessagingIdentityType.QQ.code())), MessagingIdentityDO.class);
    }
}
