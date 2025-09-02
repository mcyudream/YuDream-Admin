package online.yudream.spring.admin.controller;

import jakarta.annotation.Resource;
import online.yudream.spring.admin.service.DepartmentService;
import online.yudream.spring.base.common.R;
import online.yudream.spring.base.common.SearchPageDto;
import online.yudream.spring.entity.dto.ParamsDepartmentDto;
import online.yudream.spring.entity.entity.Department;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/department")
public class DepartmentController {
    @Resource
    private DepartmentService departmentService;

    @PutMapping
    public R<Department> createDepartment(@RequestBody ParamsDepartmentDto departmentDto) {
        Department department = departmentService.createDepartment(departmentDto);
        return R.success(department);
    }

    @PostMapping("/getall")
    public R<List<Department>> findAllDepartments(@RequestBody SearchPageDto searchPageDto) {
        List<Department> departments = departmentService.getAllDepartments(searchPageDto);
        return R.success(departments);
    }

    @DeleteMapping("/{id}")
    public R<String> deleteDepartment(@PathVariable(name = "id") String id) {
        departmentService.deleteDepartment(id);
        return R.success();
    }

    @PostMapping
    public R<Department> updateDepartment(@RequestBody ParamsDepartmentDto departmentDto) {
        departmentService.editDepartment(departmentDto);
        return R.success();
    }

}
