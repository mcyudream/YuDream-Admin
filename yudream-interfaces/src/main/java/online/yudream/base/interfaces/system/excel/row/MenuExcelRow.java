package online.yudream.base.interfaces.system.excel.row;

import com.alibaba.excel.annotation.ExcelProperty;
import lombok.Data;

@Data
public class MenuExcelRow {
    @ExcelProperty("菜单编码")
    private String code;
    @ExcelProperty("菜单名称")
    private String name;
    @ExcelProperty("类型")
    private String type;
    @ExcelProperty("上级编码")
    private String parentCode;
    @ExcelProperty("模块")
    private String module;
    @ExcelProperty("图标")
    private String icon;
    @ExcelProperty("路由地址")
    private String path;
    @ExcelProperty("组件")
    private String component;
    @ExcelProperty("外链")
    private String link;
    @ExcelProperty("排序")
    private Integer sort;
    @ExcelProperty("权限字符")
    private String permission;
    @ExcelProperty("状态")
    private String status;
}
