package online.yudream.base.interfaces.system.excel.row;

import com.alibaba.excel.annotation.ExcelProperty;
import lombok.Data;

/**
 * 部门模板行。路径是跨系统关联的稳定标识，ID 仅用于同源回导定位。
 */
@Data
public class DeptExcelRow {
    @ExcelProperty("部门ID")
    private String id;
    @ExcelProperty("部门名称")
    private String name;
    /** 历史兼容列，优先级低于上级部门路径。 */
    @ExcelProperty("上级部门ID")
    private String parentId;
    @ExcelProperty("上级部门路径")
    private String parentPath;
    @ExcelProperty("部门路径")
    private String path;
    /** 历史兼容列，优先级低于负责人用户名。 */
    @ExcelProperty("负责人ID")
    private String leaderId;
    @ExcelProperty("负责人")
    private String leaderName;
    @ExcelProperty("负责人用户名")
    private String leaderUsername;
    @ExcelProperty("电话")
    private String phone;
    @ExcelProperty("排序")
    private Integer sortOrder;
    @ExcelProperty("描述")
    private String description;
    @ExcelProperty("代表色")
    private String color;
    @ExcelProperty("系统部门")
    private String systemDept;
    @ExcelProperty("状态")
    private String status;
    @ExcelProperty("创建时间")
    private String createTime;
}
