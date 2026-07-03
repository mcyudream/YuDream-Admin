package online.yudream.base.domain.system.user.aggregate;

import lombok.*;
import lombok.experimental.SuperBuilder;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.common.base.BaseDomain;
import online.yudream.base.domain.system.user.enumerate.UserStatus;
import online.yudream.base.domain.system.user.valobj.DeptID;
import online.yudream.base.domain.system.user.valobj.RoleID;
import online.yudream.base.domain.system.user.valobj.UserDept;
import online.yudream.base.domain.valobj.Email;
import online.yudream.base.domain.valobj.Password;
import online.yudream.base.domain.valobj.Phone;
import online.yudream.base.domain.valobj.QQ;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
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

    private Long avatarFileId;

    private boolean emailVerified;

    @Builder.Default
    private UserStatus status = UserStatus.ACTIVE;

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

    public void updateProfile(String nickname, Email email, Phone phone, QQ qq, Boolean emailVerified) {
        this.nickname = nickname;
        this.email = email;
        this.phone = phone;
        this.qq = qq;
        if (emailVerified != null) {
            this.emailVerified = emailVerified;
        }
    }

    public void updateAvatar(Long avatarFileId) {
        this.avatarFileId = avatarFileId;
    }

    public void assignRoles(RoleID roleID) {
        if (roles == null) {
            roles = new ArrayList<>();
        }
        if (!roles.contains(roleID)) {
            roles.add(roleID);
        }
    }

    public void replaceRoles(List<RoleID> roleIds) {
        this.roles = roleIds == null ? new ArrayList<>() : new ArrayList<>(roleIds);
    }

    public void replaceDepts(List<UserDept> userDepts) {
        if (userDepts == null || userDepts.isEmpty()) {
            this.depts = new ArrayList<>();
            return;
        }
        long defaultCount = userDepts.stream().filter(UserDept::isDefault).count();
        if (defaultCount != 1) {
            throw new BizException("必须设置一个默认部门");
        }
        List<DeptID> ids = userDepts.stream().map(UserDept::id).toList();
        long distinctCount = ids.stream().filter(Objects::nonNull).distinct().count();
        if (distinctCount != userDepts.size()) {
            throw new BizException("用户部门不能重复");
        }
        this.depts = new ArrayList<>(userDepts);
    }

    public void activate() {
        this.status = UserStatus.ACTIVE;
    }

    public void disable() {
        this.status = UserStatus.DISABLED;
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
