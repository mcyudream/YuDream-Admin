package online.yudream.base.interfaces.system.excel.row;

import com.alibaba.excel.annotation.ExcelProperty;
import lombok.Data;

@Data
public class LoginLogExcelRow {
    @ExcelProperty("日志ID")
    private Long id;
    @ExcelProperty("用户名")
    private String username;
    @ExcelProperty("用户ID")
    private Long userId;
    @ExcelProperty("是否成功")
    private String success;
    @ExcelProperty("消息")
    private String message;
    @ExcelProperty("IP")
    private String ip;
    @ExcelProperty("登录时间")
    private String createTime;
}
