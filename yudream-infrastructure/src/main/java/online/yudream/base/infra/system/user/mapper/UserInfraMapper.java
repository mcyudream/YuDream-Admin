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
        return UserDO.builder()
                .id(user.getId())
                .username(user.getUsername())
                .nickname(user.getNickname())
                .email(user.getEmail() == null ? null : user.getEmail().getValue())
                .phone(user.getPhone() == null ? null : user.getPhone().getValue())
                .qq(user.getQq() == null ? null : user.getQq().getValue())
                .password(user.getPassword() == null ? null : user.getPassword().getEncodedPassword())
                .emailVerified(user.isEmailVerified())
                .version(user.getVersion())
                .updateTime(user.getUpdateTime())
                .createTime(user.getCreateTime())
                .build();
    }

    public static User toDomain(UserDO userDO) {
        if (userDO == null) return null;
        return User.builder()
                .username(userDO.getUsername())
                .nickname(userDO.getNickname())
                .email(userDO.getEmail() == null ? null : Email.fromTrusted(userDO.getEmail()))
                .phone(userDO.getPhone() == null ? null : Phone.fromTrusted(userDO.getPhone()))
                .qq(userDO.getQq() == null ? null : QQ.fromTrusted(userDO.getQq()))
                .password(userDO.getPassword() == null ? null : Password.fromEncoded(userDO.getPassword()))
                .emailVerified(userDO.isEmailVerified())
                .id(userDO.getId()).version(userDO.getVersion())
                .createTime(userDO.getCreateTime())
                .updateTime(userDO.getUpdateTime())
                .build();
    }
}
