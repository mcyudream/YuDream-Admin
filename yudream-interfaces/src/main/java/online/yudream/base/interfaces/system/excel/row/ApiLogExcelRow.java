package online.yudream.base.interfaces.system.excel.row;

import com.alibaba.excel.annotation.ExcelProperty;
import lombok.Data;

@Data
public class ApiLogExcelRow {
    @ExcelProperty("日志ID")
    private Long id;
    @ExcelProperty("方法")
    private String method;
    @ExcelProperty("路径")
    private String path;
    @ExcelProperty("状态码")
    private Integer status;
    @ExcelProperty("是否成功")
    private String success;
    @ExcelProperty("耗时(ms)")
    private Long costMs;
    @ExcelProperty("用户ID")
    private Long loginId;
    @ExcelProperty("用户名")
    private String username;
    @ExcelProperty("昵称")
    private String nickname;
    @ExcelProperty("IP")
    private String ip;
    @ExcelProperty("错误信息")
    private String errorMessage;
    @ExcelProperty("请求时间")
    private String createTime;
}
