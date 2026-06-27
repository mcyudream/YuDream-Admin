package online.yudream.base.domain.system.user.aggregate;

import lombok.*;
import lombok.experimental.SuperBuilder;
import online.yudream.base.domain.common.base.BaseDomain;
import online.yudream.base.domain.valobj.Email;
import online.yudream.base.domain.valobj.Password;
import online.yudream.base.domain.valobj.Phone;
import online.yudream.base.domain.valobj.QQ;
import online.yudream.base.domain.system.user.valobj.DeptID;

import java.util.List;


@EqualsAndHashCode(callSuper = true)
@Data
@AllArgsConstructor
@NoArgsConstructor
@SuperBuilder
public class User extends BaseDomain {

    private String username;

    private String nickname;

    private Email email;

    private Phone phone;

    private QQ qq;

    private Password password;

    private List<DeptID> depts;
}
