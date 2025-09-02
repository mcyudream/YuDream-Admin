package online.yudream.spring.admin.service;

import online.yudream.spring.base.common.SearchPageDto;
import online.yudream.spring.entity.dto.ParamsDepartmentDto;
import online.yudream.spring.entity.entity.Department;

import java.util.List;

public interface DepartmentService {
    List<Department> getAllDepartments(SearchPageDto searchPageDto);

    Department createDepartment(ParamsDepartmentDto departmentDto);

    void deleteDepartment(String id);

    void editDepartment(ParamsDepartmentDto departmentDto);
}
