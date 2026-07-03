package online.yudream.base.interfaces.system.excel.row;

import com.alibaba.excel.annotation.ExcelProperty;
import lombok.Data;

@Data
public class RoleExcelRow {
    @ExcelProperty("角色ID")
    private Long id;
    @ExcelProperty("角色名称")
    private String name;
    @ExcelProperty("角色编码")
    private String code;
    @ExcelProperty("部门ID")
    private Long deptId;
    @ExcelProperty("部门名称")
    private String deptName;
    @ExcelProperty("等级")
    private String level;
    @ExcelProperty("权限字符")
    private String permissions;
    @ExcelProperty("系统角色")
    private String systemRole;
    @ExcelProperty("状态")
    private String status;
    @ExcelProperty("创建时间")
    private String createTime;
}
