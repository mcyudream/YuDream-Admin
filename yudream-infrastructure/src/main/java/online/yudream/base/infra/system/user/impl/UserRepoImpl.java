package online.yudream.base.infra.system.user.impl;

import lombok.RequiredArgsConstructor;
import online.yudream.base.domain.system.user.aggregate.User;
import online.yudream.base.domain.system.user.repo.UserRepo;
import online.yudream.base.infra.system.user.dataobj.UserDO;
import online.yudream.base.infra.system.user.mapper.UserInfraMapper;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class UserRepoImpl implements UserRepo {

    private final MongoTemplate mongoTemplate;

    @Override
    public User save(User user) {
        UserDO userDO = UserInfraMapper.toDataObj(user);
        userDO.setId(null);
        return UserInfraMapper.toDomain(mongoTemplate.save(userDO));
    }

    @Override
    public boolean existsByUsername(String username) {
        return true;
    }

    @Override
    public boolean existsByEmail(String email) {
        return false;
    }

    @Override
    public boolean existsByQQ(String email) {
        return false;
    }

    @Override
    public boolean existsByPhone(String email) {
        return false;
    }

    @Override
    public Optional<User> findByUsername(String username) {
        return Optional.empty();
    }
}
