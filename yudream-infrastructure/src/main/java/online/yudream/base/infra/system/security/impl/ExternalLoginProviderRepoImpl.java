package online.yudream.base.infra.system.security.impl;

import lombok.RequiredArgsConstructor;
import online.yudream.base.domain.shared.IdGenerator;
import online.yudream.base.domain.system.security.aggregate.ExternalLoginProvider;
import online.yudream.base.domain.system.security.repo.ExternalLoginProviderRepo;
import online.yudream.base.infra.system.security.dataobj.ExternalLoginProviderDO;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime; import java.util.List; import java.util.Optional;

@Service @RequiredArgsConstructor
public class ExternalLoginProviderRepoImpl implements ExternalLoginProviderRepo {
 private final MongoTemplate mongoTemplate; private final IdGenerator idGenerator;
 public ExternalLoginProvider save(ExternalLoginProvider p) { ExternalLoginProviderDO d=toDO(p); if(d.getId()==null){d.setId(idGenerator.nextId());d.setCreateTime(LocalDateTime.now());}d.setUpdateTime(LocalDateTime.now());return toDomain(mongoTemplate.save(d)); }
 public Optional<ExternalLoginProvider> findByCode(String code){return Optional.ofNullable(toDomain(mongoTemplate.findOne(Query.query(Criteria.where("code").is(code)),ExternalLoginProviderDO.class)));}
 public List<ExternalLoginProvider> findAll(){return mongoTemplate.find(new Query().with(Sort.by("code")),ExternalLoginProviderDO.class).stream().map(this::toDomain).toList();}
 private ExternalLoginProviderDO toDO(ExternalLoginProvider p){if(p==null)return null; ExternalLoginProviderDO d=new ExternalLoginProviderDO();d.setId(p.getId());d.setVersion(p.getVersion());d.setCreateTime(p.getCreateTime());d.setUpdateTime(p.getUpdateTime());d.setCode(p.getCode());d.setName(p.getName());d.setProtocol(p.getProtocol());d.setAppId(p.getAppId());d.setAppKey(p.getAppKey());d.setCallbackUrl(p.getCallbackUrl());d.setEnabled(p.isEnabled());d.setSupportedTypes(p.getSupportedTypes());return d;}
 private ExternalLoginProvider toDomain(ExternalLoginProviderDO d){return d==null?null:ExternalLoginProvider.builder().id(d.getId()).version(d.getVersion()).createTime(d.getCreateTime()).updateTime(d.getUpdateTime()).code(d.getCode()).name(d.getName()).protocol(d.getProtocol()).appId(d.getAppId()).appKey(d.getAppKey()).callbackUrl(d.getCallbackUrl()).enabled(d.isEnabled()).supportedTypes(d.getSupportedTypes()).build();}
}
