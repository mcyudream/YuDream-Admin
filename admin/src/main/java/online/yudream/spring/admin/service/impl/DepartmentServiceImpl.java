package online.yudream.spring.admin.service.impl;

import jakarta.annotation.Resource;
import online.yudream.spring.admin.service.DepartmentService;
import online.yudream.spring.base.common.SearchPageDto;
import online.yudream.spring.base.exception.BaseException;
import online.yudream.spring.base.exception.NotFoundException;
import online.yudream.spring.base.utils.SearchUtils;
import online.yudream.spring.entity.dto.ParamsDepartmentDto;
import online.yudream.spring.entity.entity.Department;
import online.yudream.spring.entity.mapper.DepartmentMapper;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

@Service
public class DepartmentServiceImpl implements DepartmentService {
    @Resource
    private DepartmentMapper departmentMapper;
    @Resource
    private SearchUtils searchUtils;

    @Override
    public List<Department> getAllDepartments(SearchPageDto searchPageDto) {
        Criteria criteria = searchUtils.searchCriteria(searchPageDto);
        return searchUtils.find(Department.class, searchPageDto, criteria);
    }

    @Override
    public Department createDepartment(ParamsDepartmentDto departmentDto) {
        Department department = new Department();
        department.setName(departmentDto.name());
        if (departmentDto.parentId() != null) {
            department.setParentId(departmentDto.parentId());
        }
        department.setDescription(departmentDto.description());
        department = departmentMapper.save(department);
        return department;
    }

    @Override
    public void deleteDepartment(String id) {
        Department department = departmentMapper.findById(id).orElse(null);
        if (department == null) {
            throw new NotFoundException();
        }
        deleteDepartmentByParentId(department.getId());
    }

    private void deleteDepartmentByParentId(String parentId) {
        List<Department> departments = departmentMapper.findByParentId(parentId);
        for (Department department : departments) {
            deleteDepartmentByParentId(department.getId());
            departmentMapper.deleteById(department.getId());
        }
        departmentMapper.deleteById(parentId);
    }

    @Override
    public void editDepartment(ParamsDepartmentDto departmentDto) {
        Department department = departmentMapper.findById(departmentDto.id()).orElse(null);
        if (department == null) {
            throw new NotFoundException();
        }
        if (departmentDto.name() != null && !departmentDto.name().isEmpty()) {
            department.setName(departmentDto.name());
        }
        if (departmentDto.description() != null && !departmentDto.description().isEmpty()) {
            department.setDescription(departmentDto.description());
        }
        if (Objects.equals(departmentDto.parentId(), department.getId())){
            throw new BaseException("exception.department.parentId.index");
        }
        department.setParentId(departmentDto.parentId());
        departmentMapper.save(department);
    }


}
