package online.yudream.spring.admin.component;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.stp.StpInterface;
import jakarta.annotation.Resource;
import online.yudream.spring.base.exception.AuthException;
import online.yudream.spring.base.exception.NotFoundException;
import online.yudream.spring.entity.entity.Role;
import online.yudream.spring.entity.entity.User;
import online.yudream.spring.entity.mapper.UserMapper;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;


@Component
public class StpInterfaceImpl implements StpInterface {

    @Resource
    private UserMapper userMapper;

    /**
     * 获取权限列表
     * @param loginId 登录id
     * @param loginType 登录方式
     * @return 权限集合
     */
    @Override
    public List<String> getPermissionList(Object loginId, String loginType) {
        String username = loginId.toString();
        User user = userMapper.findById(username).orElse(null);
        if (user == null) {
            throw new NotFoundException();
        }
        Set<String> permissions = new HashSet<>();
        for (Role role : user.getRoles()) {
            if (role.getPermissionId()!=null) {
                permissions.addAll(role.getPermissionId());
            }
        }
        return permissions.stream().toList();
    }

    @Override
    public List<String> getRoleList(Object loginId, String loginType) {
        String username = loginId.toString();
        User user = userMapper.findById(username).orElse(null);
        if (user == null) {
            throw new NotFoundException();
        }
        System.out.println(user);
        System.out.println(user.getRoles());
        return user.getRoles().stream().map(Role::getId).collect(Collectors.toList());
    }
}
