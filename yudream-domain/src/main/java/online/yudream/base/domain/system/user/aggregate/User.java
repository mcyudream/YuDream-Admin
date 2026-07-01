package online.yudream.base.domain.system.user.aggregate;

import lombok.*;
import lombok.experimental.SuperBuilder;
import online.yudream.base.domain.common.base.BaseDomain;
import online.yudream.base.domain.system.user.valobj.DeptID;
import online.yudream.base.domain.system.user.valobj.RoleID;
import online.yudream.base.domain.system.user.valobj.UserDept;
import online.yudream.base.domain.valobj.Email;
import online.yudream.base.domain.valobj.Password;
import online.yudream.base.domain.valobj.Phone;
import online.yudream.base.domain.valobj.QQ;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;


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

    private boolean emailVerified;

    private List<UserDept> depts = new ArrayList<>();

    private List<RoleID> roles = new ArrayList<>();

    public void joinDept(DeptID deptID, boolean isDefault) {
        if (depts == null) {
            depts = new ArrayList<>();
        }
        depts.removeIf(d -> d.id().equals(deptID));
        depts.add(new UserDept(deptID, isDefault));
    }

    public void verifyEmail() {
        this.emailVerified = true;
    }

    public void assignRoles(RoleID roleID) {
        if (roles == null) {
            roles = new ArrayList<>();
        }
        if (!roles.contains(roleID)) {
            roles.add(roleID);
        }
    }

    public boolean belongsToDept(DeptID deptID) {
        return depts != null && depts.stream().anyMatch(d -> d.id().equals(deptID));
    }

    public DeptID getDefaultDeptID() {
        if (depts == null || depts.isEmpty()) {
            return null;
        }
        return depts.stream()
                .filter(UserDept::isDefault)
                .findFirst()
                .map(UserDept::id)
                .orElse(depts.getFirst().id());
    }

    public List<RoleID> getRoleInDept(DeptID deptID, Function<RoleID, DeptID> roleDeptLoader) {
        if (roles == null) {
            return new ArrayList<>();
        }
        return roles.stream().filter(
                rid -> {
                    DeptID id = roleDeptLoader.apply(rid);
                    return id != null && id.equals(deptID);
                }
        ).toList();
    }
}
