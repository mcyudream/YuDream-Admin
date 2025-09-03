package online.yudream.spring.admin.controller;

import jakarta.annotation.Resource;
import online.yudream.spring.admin.service.UserService;
import online.yudream.spring.base.common.R;
import online.yudream.spring.base.common.SearchPageDto;
import online.yudream.spring.entity.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/user")
public class UserController {
    @Resource
    private UserService userService;

    @GetMapping("/info")
    public R<User> userInfo(){
        return R.success(userService.userInfo());
    }

    @PostMapping("/page")
    public R<Page<User>> getUsersPage(@RequestBody SearchPageDto searchPageDto){
        return R.success(userService.getUsersPage(searchPageDto));
    }

    @PostMapping
    public R<String> updateUser(@RequestBody User user){
        userService.editUser(user);
        return R.success();
    }

    @DeleteMapping("/{id}")
    public R<String> deleteUser(@PathVariable(name = "id") String id){
        userService.deleteUser(id);
        return R.success();
    }
}
