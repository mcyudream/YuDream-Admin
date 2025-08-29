package online.yudream.spring.entity.mapper;

import online.yudream.spring.entity.entity.Department;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface DepartmentMapper extends MongoRepository<Department, String> {
}
