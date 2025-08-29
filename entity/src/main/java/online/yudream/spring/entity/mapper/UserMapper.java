package online.yudream.spring.entity.mapper;

import online.yudream.spring.entity.entity.User;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface UserMapper extends MongoRepository<User, String> {
}
