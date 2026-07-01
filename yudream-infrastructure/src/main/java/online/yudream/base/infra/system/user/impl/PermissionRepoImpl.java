package online.yudream.base.infra.system.user.impl;

import lombok.RequiredArgsConstructor;
import online.yudream.base.domain.system.user.aggregate.Permission;
import online.yudream.base.domain.system.user.enumerate.PermissionStatus;
import online.yudream.base.domain.system.user.repo.PermissionRepo;
import online.yudream.base.infra.system.user.dataobj.PermissionDO;
import online.yudream.base.infra.system.user.mapper.PermissionInfraMapper;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 权限仓库实现。
 */
@Service
@RequiredArgsConstructor
public class PermissionRepoImpl implements PermissionRepo {

    private final MongoTemplate mongoTemplate;

    @Override
    public Permission save(Permission permission) {
        PermissionDO permissionDO = PermissionInfraMapper.toDataObj(permission);
        PermissionDO existing = mongoTemplate.findOne(
                Query.query(Criteria.where("code").is(permission.getId().getCode())),
                PermissionDO.class
        );
        if (existing != null) {
            permissionDO.setId(existing.getId());
        }
        PermissionDO saved = mongoTemplate.save(permissionDO);
        return PermissionInfraMapper.toDomain(saved);
    }

    @Override
    public Optional<Permission> findByCode(String code) {
        Query query = Query.query(Criteria.where("code").is(code));
        PermissionDO permissionDO = mongoTemplate.findOne(query, PermissionDO.class);
        return Optional.ofNullable(PermissionInfraMapper.toDomain(permissionDO));
    }

    @Override
    public List<Permission> findAll() {
        List<PermissionDO> list = mongoTemplate.findAll(PermissionDO.class);
        return list.stream()
                .map(PermissionInfraMapper::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public void deprecateByCodesNotIn(Collection<String> codes) {
        Query query = Query.query(Criteria.where("code").nin(codes).and("status").ne(PermissionStatus.DEPRECATED));
        Update update = new Update().set("status", PermissionStatus.DEPRECATED);
        mongoTemplate.updateMulti(query, update, PermissionDO.class);
    }
}
