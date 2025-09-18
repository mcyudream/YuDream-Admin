package online.yudream.spring.entity.mapper;

import online.yudream.spring.entity.entity.Permission;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Collection;
import java.util.List;

public interface PermissionMapper extends MongoRepository<Permission, String> {
    List<Permission> findByIdIn(Collection<String> ids);

    List<Permission> deleteAllByIdRegex(String id);
}
