package online.yudream.base.infra.platform.docs.impl;

import lombok.RequiredArgsConstructor;
import online.yudream.base.domain.platform.docs.aggregate.ApiDocSettings;
import online.yudream.base.domain.platform.docs.repo.ApiDocSettingsRepo;
import online.yudream.base.domain.shared.IdGenerator;
import online.yudream.base.infra.platform.docs.dataobj.ApiDocSettingsDO;
import online.yudream.base.infra.platform.docs.mapper.ApiDocSettingsInfraMapper;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ApiDocSettingsRepoImpl implements ApiDocSettingsRepo {

    private final MongoTemplate mongoTemplate;
    private final IdGenerator idGenerator;

    @Override
    public ApiDocSettings save(ApiDocSettings settings) {
        ApiDocSettingsDO dataObj = ApiDocSettingsInfraMapper.toDataObj(settings);
        if (dataObj.getId() == null) {
            dataObj.setId(idGenerator.nextId());
            dataObj.setCreateTime(LocalDateTime.now());
        }
        dataObj.setUpdateTime(LocalDateTime.now());
        return ApiDocSettingsInfraMapper.toDomain(mongoTemplate.save(dataObj));
    }

    @Override
    public Optional<ApiDocSettings> findDefault() {
        Query query = Query.query(Criteria.where("code").is(ApiDocSettings.DEFAULT_CODE));
        return Optional.ofNullable(ApiDocSettingsInfraMapper.toDomain(mongoTemplate.findOne(query, ApiDocSettingsDO.class)));
    }
}
