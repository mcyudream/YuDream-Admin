package online.yudream.base.infra.platform.plugin.impl;

import lombok.RequiredArgsConstructor;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.plugin.aggregate.PluginMarketSource;
import online.yudream.base.domain.platform.plugin.repo.PluginMarketSourceRepo;
import online.yudream.base.domain.shared.IdGenerator;
import online.yudream.base.infra.platform.capability.service.CapabilityCredentialCipher;
import online.yudream.base.infra.platform.plugin.dataobj.PluginMarketSourceDO;
import online.yudream.base.infra.platform.plugin.mapper.PluginMarketSourceInfraMapper;
import org.springframework.data.domain.Sort;
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
public class PluginMarketSourceRepoImpl implements PluginMarketSourceRepo {

    private static final String CIPHER_CODE = "plugin-market-source";

    private final MongoTemplate mongoTemplate;
    private final IdGenerator idGenerator;
    private final CapabilityCredentialCipher credentialCipher;

    @Override
    public PluginMarketSource save(PluginMarketSource source) {
        PluginMarketSourceDO dataObj = PluginMarketSourceInfraMapper.toDataObj(source);
        dataObj.setToken(encryptToken(source.getCode(), source.getToken()));
        if (dataObj.getId() == null) {
            dataObj.setId(idGenerator.nextId());
            dataObj.setCreateTime(LocalDateTime.now());
        }
        dataObj.setUpdateTime(LocalDateTime.now());
        PluginMarketSourceDO saved = mongoTemplate.save(dataObj);
        source.setVersion(saved.getVersion());
        PluginMarketSource domain = PluginMarketSourceInfraMapper.toDomain(saved);
        domain.setToken(source.getToken());
        return domain;
    }

    @Override
    public Optional<PluginMarketSource> findById(Long id) {
        PluginMarketSourceDO dataObj = mongoTemplate.findById(id, PluginMarketSourceDO.class);
        return Optional.ofNullable(decryptDomain(dataObj));
    }

    @Override
    public Optional<PluginMarketSource> findByCode(String code) {
        Query query = Query.query(Criteria.where("code").is(code));
        PluginMarketSourceDO dataObj = mongoTemplate.findOne(query, PluginMarketSourceDO.class);
        return Optional.ofNullable(decryptDomain(dataObj));
    }

    @Override
    public List<PluginMarketSource> findAll() {
        Query query = new Query().with(Sort.by(Sort.Direction.ASC, "sortOrder").and(Sort.by(Sort.Direction.ASC, "id")));
        return mongoTemplate.find(query, PluginMarketSourceDO.class).stream()
                .map(this::decryptDomain)
                .toList();
    }

    @Override
    public void deleteById(Long id) {
        mongoTemplate.remove(Query.query(Criteria.where("_id").is(id)), PluginMarketSourceDO.class);
    }

    private PluginMarketSource decryptDomain(PluginMarketSourceDO dataObj) {
        if (dataObj == null) {
            return null;
        }
        PluginMarketSource domain = PluginMarketSourceInfraMapper.toDomain(dataObj);
        // 主密钥缺失或轮换时不把密文当令牌使用：置空让下一次同步以鉴权错误暴露，而不是把 v1: 密文发出去
        domain.setToken(decryptToken(domain.getCode(), dataObj.getToken()));
        return domain;
    }

    private String encryptToken(String code, String token) {
        if (!StringUtils.hasText(token)) {
            return null;
        }
        return credentialCipher.encryptSecret(CIPHER_CODE, "token:" + code, token);
    }

    private String decryptToken(String code, String token) {
        if (!StringUtils.hasText(token)) {
            return null;
        }
        try {
            return credentialCipher.decryptSecret(CIPHER_CODE, "token:" + code, token);
        } catch (BizException e) {
            return null;
        }
    }
}
