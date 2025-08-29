package online.yudream.spring.entity.mapper;

import online.yudream.spring.entity.entity.Permission;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface PermissionMapper extends MongoRepository<Permission, String> {
}
