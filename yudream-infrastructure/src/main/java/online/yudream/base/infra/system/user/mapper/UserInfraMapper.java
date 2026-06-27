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
                .qq(user.getQq().getValue())
                .email(user.getEmail().getValue())
                .phone(user.getPhone().getValue())
                .username(user.getUsername())
                .nickname(user.getNickname())
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
                .email(Email.fromTrusted(userDO.getEmail()))
                .phone(Phone.fromTrusted(userDO.getPhone()))
                .qq(QQ.fromTrusted(userDO.getQq()))
                .password(Password.fromEncoded(userDO.getPassword()))
                .id(userDO.getId()).version(userDO.getVersion())
                .createTime(userDO.getCreateTime())
                .updateTime(userDO.getUpdateTime())
                .build();
    }
}
