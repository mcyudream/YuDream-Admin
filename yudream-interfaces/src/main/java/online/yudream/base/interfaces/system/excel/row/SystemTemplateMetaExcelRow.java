package online.yudream.base.interfaces.system.excel.row;

import com.alibaba.excel.annotation.ExcelProperty;
import lombok.Data;

/** 版本化系统数据模板包的元数据工作表。 */
@Data
public class SystemTemplateMetaExcelRow {
    @ExcelProperty("格式")
    private String format;
    @ExcelProperty("协议版本")
    private String schemaVersion;
    @ExcelProperty("说明")
    private String description;
}
