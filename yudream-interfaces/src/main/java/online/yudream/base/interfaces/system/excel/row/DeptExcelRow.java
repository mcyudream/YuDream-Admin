package online.yudream.base.interfaces.system.excel.row;

import com.alibaba.excel.annotation.ExcelProperty;
import lombok.Data;

@Data
public class DeptExcelRow {
    @ExcelProperty("部门ID")
    private Long id;
    @ExcelProperty("部门名称")
    private String name;
    @ExcelProperty("上级部门ID")
    private Long parentId;
    @ExcelProperty("负责人ID")
    private Long leaderId;
    @ExcelProperty("负责人")
    private String leaderName;
    @ExcelProperty("电话")
    private String phone;
    @ExcelProperty("排序")
    private Integer sortOrder;
    @ExcelProperty("描述")
    private String description;
    @ExcelProperty("系统部门")
    private String systemDept;
    @ExcelProperty("状态")
    private String status;
    @ExcelProperty("创建时间")
    private String createTime;
}
