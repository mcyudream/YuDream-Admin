package online.yudream.base.infra.system.user.impl;

import lombok.RequiredArgsConstructor;
import online.yudream.base.domain.shared.IdGenerator;
import online.yudream.base.domain.system.user.aggregate.User;
import online.yudream.base.domain.system.user.repo.UserRepo;
import online.yudream.base.infra.system.user.dataobj.UserDO;
import online.yudream.base.infra.system.user.mapper.UserInfraMapper;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;

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
    public boolean existsByEmail(String email) {
        Query query = Query.query(Criteria.where("email").is(email));
        return mongoTemplate.exists(query, UserDO.class);
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
        Query query = Query.query(Criteria.where("username").is(username));
        UserDO userDO = mongoTemplate.findOne(query, UserDO.class);
        return Optional.ofNullable(UserInfraMapper.toDomain(userDO));
    }

    @Override
    public Optional<User> findByEmail(String email) {
        Query query = Query.query(Criteria.where("email").is(email));
        UserDO userDO = mongoTemplate.findOne(query, UserDO.class);
        return Optional.ofNullable(UserInfraMapper.toDomain(userDO));
    }
}
