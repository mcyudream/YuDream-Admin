package online.yudream.base.infra.system.user.impl;

import lombok.RequiredArgsConstructor;
import online.yudream.base.domain.common.PageResult;
import online.yudream.base.domain.shared.IdGenerator;
import online.yudream.base.domain.system.user.aggregate.User;
import online.yudream.base.domain.system.user.enumerate.UserStatus;
import online.yudream.base.domain.system.user.repo.UserRepo;
import online.yudream.base.infra.system.user.dataobj.UserDO;
import online.yudream.base.infra.system.user.mapper.UserInfraMapper;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * 用户仓库实现。
 */
@Service
@RequiredArgsConstructor
public class UserRepoImpl implements UserRepo {

    private final MongoTemplate mongoTemplate;
    private final IdGenerator idGenerator;

    @Override
    public User save(User user) {
        UserDO userDO = UserInfraMapper.toDataObj(user);
        if (userDO.getId() == null) {
            userDO.setId(idGenerator.nextId());
            userDO.setCreateTime(LocalDateTime.now());
        }
        userDO.setUpdateTime(LocalDateTime.now());
        UserDO saved = mongoTemplate.save(userDO);
        return UserInfraMapper.toDomain(saved);
    }

    @Override
    public boolean existsByUsername(String username) {
        Query query = Query.query(Criteria.where("username").is(username));
        return mongoTemplate.exists(query, UserDO.class);
    }

    @Override
    public boolean existsVerifiedByUsername(String username) {
        return existsVerified("username", username);
    }

    @Override
    public boolean existsByEmail(String email) {
        Query query = Query.query(Criteria.where("email").is(email));
        return mongoTemplate.exists(query, UserDO.class);
    }

    @Override
    public boolean existsVerifiedByEmail(String email) {
        return existsVerified("email", email);
    }

    @Override
    public boolean existsByQQ(String qq) {
        if (qq == null || qq.isBlank()) {
            return false;
        }
        Query query = Query.query(Criteria.where("qq").is(qq));
        return mongoTemplate.exists(query, UserDO.class);
    }

    @Override
    public boolean existsByPhone(String phone) {
        if (phone == null || phone.isBlank()) {
            return false;
        }
        Query query = Query.query(Criteria.where("phone").is(phone));
        return mongoTemplate.exists(query, UserDO.class);
    }

    @Override
    public Optional<User> findByUsername(String username) {
        return findByUsernameAll(username).stream().findFirst();
    }

    @Override
    public List<User> findByUsernameAll(String username) {
        return findAllByField("username", username);
    }

    @Override
    public Optional<User> findByEmail(String email) {
        return findByEmailAll(email).stream().findFirst();
    }

    @Override
    public List<User> findByEmailAll(String email) {
        return findAllByField("email", email);
    }

    @Override
    public Optional<User> findById(Long id) {
        UserDO userDO = mongoTemplate.findById(id, UserDO.class);
        return Optional.ofNullable(UserInfraMapper.toDomain(userDO));
    }

    @Override
    public List<User> findByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyList();
        }
        Query query = Query.query(Criteria.where("id").in(ids));
        return mongoTemplate.find(query, UserDO.class).stream()
                .map(UserInfraMapper::toDomain)
                .toList();
    }

    @Override
    public void deleteByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        mongoTemplate.remove(Query.query(Criteria.where("id").in(ids)), UserDO.class);
    }

    @Override
    public PageResult<User> page(String keyword, Long deptId, Long roleId, Boolean emailVerified, UserStatus status, int page, int size) {
        Query query = buildPageQuery(keyword, deptId, roleId, emailVerified, status);
        long total = mongoTemplate.count(query, UserDO.class);
        int currentPage = Math.max(page, 1);
        int pageSize = Math.max(size, 1);
        query.skip((long) (currentPage - 1) * pageSize).limit(pageSize);
        List<User> records = mongoTemplate.find(query, UserDO.class).stream()
                .map(UserInfraMapper::toDomain)
                .toList();
        return new PageResult<>(records, total, currentPage, pageSize);
    }

    @Override
    public boolean existsByUsernameExcludeId(String username, Long excludeId) {
        return existsExcludeId("username", username, excludeId);
    }

    @Override
    public boolean existsByEmailExcludeId(String email, Long excludeId) {
        return existsExcludeId("email", email, excludeId);
    }

    @Override
    public boolean existsByPhoneExcludeId(String phone, Long excludeId) {
        return existsExcludeId("phone", phone, excludeId);
    }

    @Override
    public boolean existsByQQExcludeId(String qq, Long excludeId) {
        return existsExcludeId("qq", qq, excludeId);
    }

    @Override
    public long countByRoleId(Long roleId) {
        if (roleId == null) {
            return 0;
        }
        return mongoTemplate.count(Query.query(Criteria.where("roleIds").is(roleId)), UserDO.class);
    }

    @Override
    public long countByDeptId(Long deptId) {
        if (deptId == null) {
            return 0;
        }
        return mongoTemplate.count(Query.query(Criteria.where("depts.deptId").is(deptId)), UserDO.class);
    }

    private Query buildPageQuery(String keyword, Long deptId, Long roleId, Boolean emailVerified, UserStatus status) {
        Query query = new Query();
        if (StringUtils.hasText(keyword)) {
            String pattern = ".*" + Pattern.quote(keyword.trim()) + ".*";
            query.addCriteria(new Criteria().orOperator(
                    Criteria.where("username").regex(pattern, "i"),
                    Criteria.where("nickname").regex(pattern, "i"),
                    Criteria.where("email").regex(pattern, "i"),
                    Criteria.where("phone").regex(pattern, "i")
            ));
        }
        if (deptId != null) {
            query.addCriteria(Criteria.where("depts.deptId").is(deptId));
        }
        if (roleId != null) {
            query.addCriteria(Criteria.where("roleIds").is(roleId));
        }
        if (emailVerified != null) {
            query.addCriteria(Criteria.where("emailVerified").is(emailVerified));
        }
        if (status != null) {
            query.addCriteria(Criteria.where("status").is(status));
        }
        return query;
    }

    private boolean existsExcludeId(String field, String value, Long excludeId) {
        if (!StringUtils.hasText(value)) {
            return false;
        }
        Query query = Query.query(Criteria.where(field).is(value));
        if (excludeId != null) {
            query.addCriteria(Criteria.where("id").ne(excludeId));
        }
        return mongoTemplate.exists(query, UserDO.class);
    }

    private boolean existsVerified(String field, String value) {
        if (!StringUtils.hasText(value)) {
            return false;
        }
        Query query = Query.query(Criteria.where(field).is(value).and("emailVerified").is(true));
        return mongoTemplate.exists(query, UserDO.class);
    }

    private List<User> findAllByField(String field, String value) {
        if (!StringUtils.hasText(value)) {
            return Collections.emptyList();
        }
        Query query = Query.query(Criteria.where(field).is(value))
                .with(Sort.by(
                        Sort.Order.desc("emailVerified"),
                        Sort.Order.desc("createTime"),
                        Sort.Order.desc("id")
                ));
        return mongoTemplate.find(query, UserDO.class).stream()
                .map(UserInfraMapper::toDomain)
                .toList();
    }
}
