package online.yudream.spring.admin.service;

import online.yudream.spring.base.common.SearchPageDto;
import online.yudream.spring.entity.entity.Department;
import org.springframework.data.domain.Page;

public interface DepartmentService {
    Page<Department> getAllDepartments(SearchPageDto searchPageDto);
}
