package online.yudream.spring.entity.mapper;

import online.yudream.spring.entity.entity.Role;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface RoleMapper extends MongoRepository<Role, String> {
}
