package online.yudream.base.infra.system.security.impl;

import lombok.RequiredArgsConstructor;
import online.yudream.base.domain.shared.IdGenerator;
import online.yudream.base.domain.system.security.aggregate.OAuthAccessToken;
import online.yudream.base.domain.system.security.repo.OAuthAccessTokenRepo;
import online.yudream.base.infra.system.security.dataobj.OAuthAccessTokenDO;
import online.yudream.base.infra.system.security.mapper.OAuthSecurityInfraMapper;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class OAuthAccessTokenRepoImpl implements OAuthAccessTokenRepo {

    private final MongoTemplate mongoTemplate;
    private final IdGenerator idGenerator;

    @Override
    public OAuthAccessToken save(OAuthAccessToken token) {
        OAuthAccessTokenDO dataObj = OAuthSecurityInfraMapper.toDataObj(token);
        if (dataObj.getId() == null) {
            dataObj.setId(idGenerator.nextId());
            dataObj.setCreateTime(LocalDateTime.now());
        }
        dataObj.setUpdateTime(LocalDateTime.now());
        return OAuthSecurityInfraMapper.toDomain(mongoTemplate.save(dataObj));
    }

    @Override
    public Optional<OAuthAccessToken> findByRefreshTokenHash(String refreshTokenHash) {
        Query query = Query.query(Criteria.where("refreshTokenHash").is(refreshTokenHash));
        return Optional.ofNullable(OAuthSecurityInfraMapper.toDomain(mongoTemplate.findOne(query, OAuthAccessTokenDO.class)));
    }
}
