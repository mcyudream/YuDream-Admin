package online.yudream.base.infra.system.security.impl;

import lombok.RequiredArgsConstructor;
import online.yudream.base.domain.shared.IdGenerator;
import online.yudream.base.domain.system.security.aggregate.OAuthProviderRegistration;
import online.yudream.base.domain.system.security.repo.OAuthProviderRegistrationRepo;
import online.yudream.base.infra.system.security.dataobj.OAuthProviderRegistrationDO;
import online.yudream.base.infra.system.security.mapper.OAuthSecurityInfraMapper;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class OAuthProviderRegistrationRepoImpl implements OAuthProviderRegistrationRepo {

    private final MongoTemplate mongoTemplate;
    private final IdGenerator idGenerator;

    @Override
    public OAuthProviderRegistration save(OAuthProviderRegistration registration) {
        OAuthProviderRegistrationDO dataObj = OAuthSecurityInfraMapper.toDataObj(registration);
        if (dataObj.getId() == null) {
            dataObj.setId(idGenerator.nextId());
            dataObj.setCreateTime(LocalDateTime.now());
        }
        dataObj.setUpdateTime(LocalDateTime.now());
        return OAuthSecurityInfraMapper.toDomain(mongoTemplate.save(dataObj));
    }

    @Override
    public Optional<OAuthProviderRegistration> findById(Long id) {
        return Optional.ofNullable(OAuthSecurityInfraMapper.toDomain(mongoTemplate.findById(id, OAuthProviderRegistrationDO.class)));
    }

    @Override
    public Optional<OAuthProviderRegistration> findByCode(String code) {
        Query query = Query.query(Criteria.where("code").is(code));
        return Optional.ofNullable(OAuthSecurityInfraMapper.toDomain(mongoTemplate.findOne(query, OAuthProviderRegistrationDO.class)));
    }

    @Override
    public List<OAuthProviderRegistration> findAll() {
        Query query = new Query().with(Sort.by(Sort.Direction.DESC, "createTime"));
        return mongoTemplate.find(query, OAuthProviderRegistrationDO.class).stream()
                .map(OAuthSecurityInfraMapper::toDomain)
                .toList();
    }
}
