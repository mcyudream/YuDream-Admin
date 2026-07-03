package online.yudream.base.infra.system.security.impl;

import lombok.RequiredArgsConstructor;
import online.yudream.base.domain.shared.IdGenerator;
import online.yudream.base.domain.system.security.aggregate.ApiKeyCredential;
import online.yudream.base.domain.system.security.repo.ApiKeyCredentialRepo;
import online.yudream.base.infra.system.security.dataobj.ApiKeyCredentialDO;
import online.yudream.base.infra.system.security.mapper.ApiKeyCredentialInfraMapper;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class ApiKeyCredentialRepoImpl implements ApiKeyCredentialRepo {

    private final MongoTemplate mongoTemplate;
    private final IdGenerator idGenerator;

    @Override
    public ApiKeyCredential save(ApiKeyCredential credential) {
        ApiKeyCredentialDO dataObj = ApiKeyCredentialInfraMapper.toDataObj(credential);
        if (dataObj.getId() == null) {
            dataObj.setId(idGenerator.nextId());
            dataObj.setCreateTime(LocalDateTime.now());
        }
        dataObj.setUpdateTime(LocalDateTime.now());
        return ApiKeyCredentialInfraMapper.toDomain(mongoTemplate.save(dataObj));
    }

    @Override
    public Optional<ApiKeyCredential> findById(Long id) {
        return Optional.ofNullable(ApiKeyCredentialInfraMapper.toDomain(mongoTemplate.findById(id, ApiKeyCredentialDO.class)));
    }

    @Override
    public Optional<ApiKeyCredential> findByPrefix(String prefix) {
        Query query = Query.query(Criteria.where("keyPrefix").is(prefix));
        return Optional.ofNullable(ApiKeyCredentialInfraMapper.toDomain(mongoTemplate.findOne(query, ApiKeyCredentialDO.class)));
    }

    @Override
    public List<ApiKeyCredential> page(String keyword, int page, int size) {
        Query query = buildPageQuery(keyword)
                .with(Sort.by(Sort.Direction.DESC, "createTime"));
        int currentPage = Math.max(page, 1);
        int pageSize = Math.max(size, 1);
        query.skip((long) (currentPage - 1) * pageSize).limit(pageSize);
        return mongoTemplate.find(query, ApiKeyCredentialDO.class).stream()
                .map(ApiKeyCredentialInfraMapper::toDomain)
                .toList();
    }

    @Override
    public long count(String keyword) {
        return mongoTemplate.count(buildPageQuery(keyword), ApiKeyCredentialDO.class);
    }

    private Query buildPageQuery(String keyword) {
        Query query = new Query();
        if (StringUtils.hasText(keyword)) {
            String pattern = ".*" + Pattern.quote(keyword.trim()) + ".*";
            query.addCriteria(new Criteria().orOperator(
                    Criteria.where("name").regex(pattern, "i"),
                    Criteria.where("keyPrefix").regex(pattern, "i")
            ));
        }
        return query;
    }
}
