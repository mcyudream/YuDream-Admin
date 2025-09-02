package online.yudream.spring.admin.service;

import online.yudream.spring.base.common.SearchPageDto;
import online.yudream.spring.entity.entity.Role;
import org.springframework.data.domain.Page;

public interface RoleService {
    Page<Role> getAllRoles(SearchPageDto searchPageDto);

    Role createRole(Role role);

    void editRole(Role role);

    void deleteRole(String id);
}
