package online.yudream.base.infra.system.user.mapper;

import lombok.NoArgsConstructor;
import online.yudream.base.domain.system.user.aggregate.User;
import online.yudream.base.domain.system.user.enumerate.UserStatus;
import online.yudream.base.domain.system.user.valobj.DeptID;
import online.yudream.base.domain.system.user.valobj.RoleID;
import online.yudream.base.domain.system.user.valobj.UserDept;
import online.yudream.base.domain.valobj.Email;
import online.yudream.base.domain.valobj.Password;
import online.yudream.base.domain.valobj.Phone;
import online.yudream.base.domain.valobj.QQ;
import online.yudream.base.infra.system.user.dataobj.UserDO;
import online.yudream.base.infra.system.user.dataobj.UserDeptDO;

import java.util.ArrayList;
import java.util.List;

@NoArgsConstructor
public class UserInfraMapper {
    public static UserDO toDataObj(User user) {
        if (user == null) return null;
        UserDO userDO = new UserDO();
        userDO.setId(user.getId());
        userDO.setUsername(user.getUsername());
        userDO.setNickname(user.getNickname());
        userDO.setEmail(user.getEmail() == null ? null : user.getEmail().getValue());
        userDO.setPhone(user.getPhone() == null ? null : user.getPhone().getValue());
        userDO.setQq(user.getQq() == null ? null : user.getQq().getValue());
        userDO.setPassword(user.getPassword() == null ? null : user.getPassword().getEncodedPassword());
        userDO.setAvatarFileId(user.getAvatarFileId());
        userDO.setEmailVerified(user.isEmailVerified());
        userDO.setStatus(user.getStatus() == null ? UserStatus.ACTIVE : user.getStatus());
        userDO.setDepts(toDeptDOs(user.getDepts()));
        userDO.setRoleIds(toRoleIds(user.getRoles()));
        userDO.setVersion(user.getVersion());
        userDO.setUpdateTime(user.getUpdateTime());
        userDO.setCreateTime(user.getCreateTime());
        return userDO;
    }

    public static User toDomain(UserDO userDO) {
        if (userDO == null) return null;
        User user = new User();
        user.setId(userDO.getId());
        user.setUsername(userDO.getUsername());
        user.setNickname(userDO.getNickname());
        user.setEmail(userDO.getEmail() == null ? null : Email.fromTrusted(userDO.getEmail()));
        user.setPhone(userDO.getPhone() == null ? null : Phone.fromTrusted(userDO.getPhone()));
        user.setQq(userDO.getQq() == null ? null : QQ.fromTrusted(userDO.getQq()));
        user.setPassword(userDO.getPassword() == null ? null : Password.fromEncoded(userDO.getPassword()));
        user.setAvatarFileId(userDO.getAvatarFileId());
        user.setEmailVerified(userDO.isEmailVerified());
        user.setStatus(userDO.getStatus() == null ? UserStatus.ACTIVE : userDO.getStatus());
        user.setDepts(toDomainDepts(userDO.getDepts()));
        user.setRoles(toDomainRoles(userDO.getRoleIds()));
        user.setVersion(userDO.getVersion());
        user.setCreateTime(userDO.getCreateTime());
        user.setUpdateTime(userDO.getUpdateTime());
        return user;
    }

    private static List<UserDeptDO> toDeptDOs(List<UserDept> depts) {
        if (depts == null) {
            return new ArrayList<>();
        }
        return depts.stream().map(d -> new UserDeptDO(d.id().getValue(), d.isDefault())).toList();
    }

    private static List<UserDept> toDomainDepts(List<UserDeptDO> depts) {
        if (depts == null) {
            return new ArrayList<>();
        }
        return depts.stream().map(d -> new UserDept(DeptID.of(d.getDeptId()), Boolean.TRUE.equals(d.getDefaultDept()))).toList();
    }

    private static List<Long> toRoleIds(List<RoleID> roles) {
        if (roles == null) {
            return new ArrayList<>();
        }
        return roles.stream().map(RoleID::getValue).toList();
    }

    private static List<RoleID> toDomainRoles(List<Long> roleIds) {
        if (roleIds == null) {
            return new ArrayList<>();
        }
        return roleIds.stream().map(RoleID::of).toList();
    }
}
