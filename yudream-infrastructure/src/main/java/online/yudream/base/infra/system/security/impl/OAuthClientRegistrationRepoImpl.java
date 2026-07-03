package online.yudream.base.infra.system.security.impl;

import lombok.RequiredArgsConstructor;
import online.yudream.base.domain.shared.IdGenerator;
import online.yudream.base.domain.system.security.aggregate.OAuthClientRegistration;
import online.yudream.base.domain.system.security.repo.OAuthClientRegistrationRepo;
import online.yudream.base.infra.system.security.dataobj.OAuthClientRegistrationDO;
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
public class OAuthClientRegistrationRepoImpl implements OAuthClientRegistrationRepo {

    private final MongoTemplate mongoTemplate;
    private final IdGenerator idGenerator;

    @Override
    public OAuthClientRegistration save(OAuthClientRegistration registration) {
        OAuthClientRegistrationDO dataObj = OAuthSecurityInfraMapper.toDataObj(registration);
        if (dataObj.getId() == null) {
            dataObj.setId(idGenerator.nextId());
            dataObj.setCreateTime(LocalDateTime.now());
        }
        dataObj.setUpdateTime(LocalDateTime.now());
        return OAuthSecurityInfraMapper.toDomain(mongoTemplate.save(dataObj));
    }

    @Override
    public Optional<OAuthClientRegistration> findById(Long id) {
        return Optional.ofNullable(OAuthSecurityInfraMapper.toDomain(mongoTemplate.findById(id, OAuthClientRegistrationDO.class)));
    }

    @Override
    public Optional<OAuthClientRegistration> findByClientId(String clientId) {
        Query query = Query.query(Criteria.where("clientId").is(clientId));
        return Optional.ofNullable(OAuthSecurityInfraMapper.toDomain(mongoTemplate.findOne(query, OAuthClientRegistrationDO.class)));
    }

    @Override
    public List<OAuthClientRegistration> findAll() {
        Query query = new Query().with(Sort.by(Sort.Direction.DESC, "createTime"));
        return mongoTemplate.find(query, OAuthClientRegistrationDO.class).stream()
                .map(OAuthSecurityInfraMapper::toDomain)
                .toList();
    }
}
