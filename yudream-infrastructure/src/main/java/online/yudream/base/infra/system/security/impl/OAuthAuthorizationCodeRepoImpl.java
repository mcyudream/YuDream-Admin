package online.yudream.base.infra.system.security.impl;

import lombok.RequiredArgsConstructor;
import online.yudream.base.domain.shared.IdGenerator;
import online.yudream.base.domain.system.security.aggregate.OAuthAuthorizationCode;
import online.yudream.base.domain.system.security.repo.OAuthAuthorizationCodeRepo;
import online.yudream.base.infra.system.security.dataobj.OAuthAuthorizationCodeDO;
import online.yudream.base.infra.system.security.mapper.OAuthSecurityInfraMapper;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class OAuthAuthorizationCodeRepoImpl implements OAuthAuthorizationCodeRepo {

    private final MongoTemplate mongoTemplate;
    private final IdGenerator idGenerator;

    @Override
    public OAuthAuthorizationCode save(OAuthAuthorizationCode authorizationCode) {
        OAuthAuthorizationCodeDO dataObj = OAuthSecurityInfraMapper.toDataObj(authorizationCode);
        if (dataObj.getId() == null) {
            dataObj.setId(idGenerator.nextId());
            dataObj.setCreateTime(LocalDateTime.now());
        }
        dataObj.setUpdateTime(LocalDateTime.now());
        return OAuthSecurityInfraMapper.toDomain(mongoTemplate.save(dataObj));
    }

    @Override
    public Optional<OAuthAuthorizationCode> findByCode(String code) {
        Query query = Query.query(Criteria.where("code").is(code));
        return Optional.ofNullable(OAuthSecurityInfraMapper.toDomain(mongoTemplate.findOne(query, OAuthAuthorizationCodeDO.class)));
    }
}
