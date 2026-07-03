package online.yudream.base.infra.system.security.impl;

import lombok.RequiredArgsConstructor;
import online.yudream.base.domain.shared.IdGenerator;
import online.yudream.base.domain.system.security.aggregate.ApiSecurityPolicy;
import online.yudream.base.domain.system.security.repo.ApiSecurityPolicyRepo;
import online.yudream.base.infra.system.security.dataobj.ApiSecurityPolicyDO;
import online.yudream.base.infra.system.security.mapper.ApiSecurityPolicyInfraMapper;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ApiSecurityPolicyRepoImpl implements ApiSecurityPolicyRepo {

    private final MongoTemplate mongoTemplate;
    private final IdGenerator idGenerator;

    @Override
    public ApiSecurityPolicy save(ApiSecurityPolicy policy) {
        ApiSecurityPolicyDO dataObj = ApiSecurityPolicyInfraMapper.toDataObj(policy);
        if (dataObj.getId() == null) {
            dataObj.setId(idGenerator.nextId());
            dataObj.setCreateTime(LocalDateTime.now());
        }
        dataObj.setUpdateTime(LocalDateTime.now());
        return ApiSecurityPolicyInfraMapper.toDomain(mongoTemplate.save(dataObj));
    }

    @Override
    public Optional<ApiSecurityPolicy> findDefault() {
        Query query = Query.query(Criteria.where("code").is(ApiSecurityPolicy.DEFAULT_CODE));
        ApiSecurityPolicyDO dataObj = mongoTemplate.findOne(query, ApiSecurityPolicyDO.class);
        return Optional.ofNullable(ApiSecurityPolicyInfraMapper.toDomain(dataObj));
    }
}
