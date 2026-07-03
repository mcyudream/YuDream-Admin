package online.yudream.base.infra.system.security.impl;

import lombok.RequiredArgsConstructor;
import online.yudream.base.domain.shared.IdGenerator;
import online.yudream.base.domain.system.security.aggregate.RefreshTokenCredential;
import online.yudream.base.domain.system.security.repo.RefreshTokenCredentialRepo;
import online.yudream.base.infra.system.security.dataobj.RefreshTokenCredentialDO;
import online.yudream.base.infra.system.security.mapper.ApiKeyCredentialInfraMapper;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class RefreshTokenCredentialRepoImpl implements RefreshTokenCredentialRepo {

    private final MongoTemplate mongoTemplate;
    private final IdGenerator idGenerator;

    @Override
    public RefreshTokenCredential save(RefreshTokenCredential credential) {
        RefreshTokenCredentialDO dataObj = ApiKeyCredentialInfraMapper.toDataObj(credential);
        if (dataObj.getId() == null) {
            dataObj.setId(idGenerator.nextId());
            dataObj.setCreateTime(LocalDateTime.now());
        }
        dataObj.setUpdateTime(LocalDateTime.now());
        return ApiKeyCredentialInfraMapper.toDomain(mongoTemplate.save(dataObj));
    }

    @Override
    public Optional<RefreshTokenCredential> findByTokenHash(String tokenHash) {
        Query query = Query.query(Criteria.where("tokenHash").is(tokenHash));
        return Optional.ofNullable(ApiKeyCredentialInfraMapper.toDomain(mongoTemplate.findOne(query, RefreshTokenCredentialDO.class)));
    }
}
