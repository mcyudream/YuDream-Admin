package online.yudream.spring.init.service.impl;

import jakarta.annotation.Resource;
import online.yudream.spring.entity.mapper.DepartmentMapper;
import online.yudream.spring.init.initenums.SysDepartment;
import online.yudream.spring.init.service.InitService;
import org.springframework.stereotype.Service;

@Service
public class DepartmentInitServiceImpl implements InitService {
    @Resource
    private DepartmentMapper departmentMapper;
    @Override
    public void init() {
        for (SysDepartment sysDepartment : SysDepartment.values()) {
            departmentMapper.save(sysDepartment.getDepartment());
        }
    }

    @Override
    public boolean isFirstInit() {
        return departmentMapper.count()==0;
    }
}
