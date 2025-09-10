package online.yudream.spring.admin.service;

import online.yudream.spring.base.common.SearchPageDto;
import online.yudream.spring.entity.entity.User;
import org.springframework.data.domain.Page;

public interface UserService {
    User userInfo();

    Page<User> getUsersPage(SearchPageDto searchPageDto);

    void editUser(User user);

    void deleteUser(String id);

    User addToDepartment(String userId, String departmentId);


    User deleteDepartment(String userId, String departmentId);
}
