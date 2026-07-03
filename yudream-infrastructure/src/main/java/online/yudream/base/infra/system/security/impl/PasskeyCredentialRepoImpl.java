package online.yudream.base.infra.system.security.impl;

import lombok.RequiredArgsConstructor;
import online.yudream.base.domain.shared.IdGenerator;
import online.yudream.base.domain.system.security.aggregate.PasskeyCredential;
import online.yudream.base.domain.system.security.repo.PasskeyCredentialRepo;
import online.yudream.base.infra.system.security.dataobj.PasskeyCredentialDO;
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
public class PasskeyCredentialRepoImpl implements PasskeyCredentialRepo {

    private final MongoTemplate mongoTemplate;
    private final IdGenerator idGenerator;

    @Override
    public PasskeyCredential save(PasskeyCredential credential) {
        PasskeyCredentialDO dataObj = OAuthSecurityInfraMapper.toDataObj(credential);
        if (dataObj.getId() == null) {
            dataObj.setId(idGenerator.nextId());
            dataObj.setCreateTime(LocalDateTime.now());
        }
        dataObj.setUpdateTime(LocalDateTime.now());
        return OAuthSecurityInfraMapper.toDomain(mongoTemplate.save(dataObj));
    }

    @Override
    public Optional<PasskeyCredential> findById(Long id) {
        return Optional.ofNullable(OAuthSecurityInfraMapper.toDomain(mongoTemplate.findById(id, PasskeyCredentialDO.class)));
    }

    @Override
    public Optional<PasskeyCredential> findByCredentialId(String credentialId) {
        Query query = Query.query(Criteria.where("credentialId").is(credentialId));
        return Optional.ofNullable(OAuthSecurityInfraMapper.toDomain(mongoTemplate.findOne(query, PasskeyCredentialDO.class)));
    }

    @Override
    public List<PasskeyCredential> findByUserId(Long userId) {
        Query query = Query.query(Criteria.where("userId").is(userId)).with(Sort.by(Sort.Direction.DESC, "createTime"));
        return mongoTemplate.find(query, PasskeyCredentialDO.class).stream()
                .map(OAuthSecurityInfraMapper::toDomain)
                .toList();
    }
}
