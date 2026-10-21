package online.yudream.base.interfaces.system.excel.row;

import com.alibaba.excel.annotation.ExcelProperty;
import lombok.Data;

/**
 * 用户模板行。所有 Snowflake 标识均使用文本单元格，避免第三方表格软件精度丢失。
 */
@Data
public class UserExcelRow {
    @ExcelProperty("用户ID")
    private String id;
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
    /** 仅兼容历史手工新建模板；系统导出永远为空，跨系统新用户改走初始化邮件。 */
    @ExcelProperty("初始密码")
    private String password;
    @ExcelProperty("邮箱已验证")
    private String emailVerified;
    /** 历史兼容列，优先级低于部门路径。 */
    @ExcelProperty("部门ID")
    private String deptIds;
    @ExcelProperty("部门名称")
    private String deptNames;
    @ExcelProperty("部门路径")
    private String deptPaths;
    @ExcelProperty("默认部门ID")
    private String defaultDeptId;
    @ExcelProperty("默认部门路径")
    private String defaultDeptPath;
    /** 历史兼容列，优先级低于角色编码。 */
    @ExcelProperty("角色ID")
    private String roleIds;
    @ExcelProperty("角色名称")
    private String roleNames;
    @ExcelProperty("角色编码")
    private String roleCodes;
    @ExcelProperty("状态")
    private String status;
    @ExcelProperty("创建时间")
    private String createTime;
}
