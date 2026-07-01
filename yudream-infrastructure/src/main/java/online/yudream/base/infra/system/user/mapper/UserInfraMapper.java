package online.yudream.base.infra.system.user.mapper;

import lombok.NoArgsConstructor;
import online.yudream.base.domain.system.user.aggregate.User;
import online.yudream.base.domain.valobj.Email;
import online.yudream.base.domain.valobj.Password;
import online.yudream.base.domain.valobj.Phone;
import online.yudream.base.domain.valobj.QQ;
import online.yudream.base.infra.system.user.dataobj.UserDO;

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
        userDO.setEmailVerified(user.isEmailVerified());
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
        user.setEmailVerified(userDO.isEmailVerified());
        user.setVersion(userDO.getVersion());
        user.setCreateTime(userDO.getCreateTime());
        user.setUpdateTime(userDO.getUpdateTime());
        return user;
    }
}
