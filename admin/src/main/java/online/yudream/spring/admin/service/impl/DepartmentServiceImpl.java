package online.yudream.spring.admin.service.impl;

import jakarta.annotation.Resource;
import online.yudream.spring.admin.service.DepartmentService;
import online.yudream.spring.base.common.SearchPageDto;
import online.yudream.spring.base.utils.SearchUtils;
import online.yudream.spring.entity.entity.Department;
import online.yudream.spring.entity.mapper.DepartmentMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.stereotype.Service;

@Service
public class DepartmentServiceImpl implements DepartmentService {
    @Resource
    private DepartmentMapper departmentMapper;
    @Resource
    private SearchUtils searchUtils;

    @Override
    public Page<Department> getAllDepartments(SearchPageDto searchPageDto) {
        Criteria criteria = searchUtils.searchCriteria(searchPageDto, new String[]{"name","id","description"});
        return searchUtils.findPage(Department.class, searchPageDto, criteria);
    }



}
