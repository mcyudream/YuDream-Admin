package online.yudream.base.interfaces.system.excel.row;

import com.alibaba.excel.annotation.ExcelProperty;
import lombok.Data;

@Data
public class UserExcelRow {
    @ExcelProperty("用户ID")
    private Long id;
    @ExcelProperty("用户名")
    private String username;
    @ExcelProperty("昵称")
    private String nickname;
    @ExcelProperty("邮箱")
    private String email;
    @ExcelProperty("手机号")
    private String phone;
    @ExcelProperty("QQ")
    private String qq;
    @ExcelProperty("初始密码")
    private String password;
    @ExcelProperty("邮箱已验证")
    private String emailVerified;
    @ExcelProperty("部门ID")
    private String deptIds;
    @ExcelProperty("部门名称")
    private String deptNames;
    @ExcelProperty("默认部门ID")
    private Long defaultDeptId;
    @ExcelProperty("角色ID")
    private String roleIds;
    @ExcelProperty("角色名称")
    private String roleNames;
    @ExcelProperty("状态")
    private String status;
    @ExcelProperty("创建时间")
    private String createTime;
}
