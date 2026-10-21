package online.yudream.base.interfaces.system.excel.row;

import com.alibaba.excel.annotation.ExcelProperty;
import lombok.Data;

/**
 * 角色模板行。Snowflake 标识均保持文本格式。
 */
@Data
public class RoleExcelRow {
    @ExcelProperty("角色ID")
    private String id;
    @ExcelProperty("角色名称")
    private String name;
    @ExcelProperty("角色编码")
    private String code;
    /** 历史兼容列，优先级低于所属部门路径。 */
    @ExcelProperty("部门ID")
    private String deptId;
    @ExcelProperty("部门名称")
    private String deptName;
    @ExcelProperty("所属部门路径")
    private String deptPath;
    @ExcelProperty("等级")
    private String level;
    @ExcelProperty("权限字符")
    private String permissions;
    @ExcelProperty("图标")
    private String icon;
    @ExcelProperty("代表色")
    private String color;
    @ExcelProperty("系统角色")
    private String systemRole;
    @ExcelProperty("状态")
    private String status;
    @ExcelProperty("创建时间")
    private String createTime;
}
