package online.yudream.spring.entity.mapper;

import online.yudream.spring.entity.entity.Department;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface DepartmentMapper extends MongoRepository<Department, String> {
    List<Department> findByParentId(String parentId);
}
