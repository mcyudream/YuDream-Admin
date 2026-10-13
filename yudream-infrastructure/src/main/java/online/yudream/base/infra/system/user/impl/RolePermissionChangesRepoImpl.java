package online.yudream.base.infra.system.user.impl;

import lombok.RequiredArgsConstructor;
import online.yudream.base.domain.system.user.aggregate.Role;
import online.yudream.base.domain.system.user.enumerate.SystemRoleType;
import online.yudream.base.domain.system.user.repo.RolePermissionChangesRepo;
import online.yudream.base.infra.system.user.dataobj.RoleDO;
import online.yudream.base.infra.system.user.mapper.RoleInfraMapper;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class RolePermissionChangesRepoImpl implements RolePermissionChangesRepo {

    private final MongoTemplate mongoTemplate;

    @Override
    public Optional<Role> compareAndSet(Long roleId, List<String> expectedPermissions, List<String> permissions) {
        Criteria snapshot = expectedPermissions.isEmpty()
                ? new Criteria().orOperator(Criteria.where("permissions").is(List.of()), Criteria.where("permissions").is(null))
                : Criteria.where("permissions").is(expectedPermissions);
        Query query = Query.query(new Criteria().andOperator(Criteria.where("id").is(roleId), snapshot,
                Criteria.where("systemType").ne(SystemRoleType.SUPER_ADMIN)));
        // MongoTemplate 为 @Version 递增版本；仅更新 permissions，不覆盖角色其他属性。
        RoleDO updated = mongoTemplate.findAndModify(query, new Update().set("permissions", permissions),
                FindAndModifyOptions.options().returnNew(true), RoleDO.class);
        return Optional.ofNullable(updated).map(RoleInfraMapper::toDomain);
    }
}
