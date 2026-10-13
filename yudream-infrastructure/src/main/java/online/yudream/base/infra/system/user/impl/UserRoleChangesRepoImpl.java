package online.yudream.base.infra.system.user.impl;

import lombok.RequiredArgsConstructor;
import online.yudream.base.domain.system.user.repo.UserRoleChangesRepo;
import online.yudream.base.infra.system.user.dataobj.UserDO;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class UserRoleChangesRepoImpl implements UserRoleChangesRepo {

    private final MongoTemplate mongoTemplate;

    @Override
    public boolean compareAndSet(Long userId, List<Long> expectedRoleIds, List<Long> roleIds, List<Long> requiredDeptIds) {
        Criteria snapshot = expectedRoleIds.isEmpty()
                ? new Criteria().orOperator(Criteria.where("roleIds").is(List.of()), Criteria.where("roleIds").is(null))
                : Criteria.where("roleIds").is(expectedRoleIds);
        Query query = Query.query(new Criteria().andOperator(Criteria.where("id").is(userId), snapshot));
        if (!requiredDeptIds.isEmpty()) {
            query.addCriteria(Criteria.where("depts.deptId").all(requiredDeptIds));
        }
        // @Version 由 MongoTemplate 递增，原编辑的整对象 save 无法覆盖本次增量分配。
        return mongoTemplate.findAndModify(query, new Update().set("roleIds", roleIds),
                FindAndModifyOptions.options().returnNew(true), UserDO.class) != null;
    }
}
