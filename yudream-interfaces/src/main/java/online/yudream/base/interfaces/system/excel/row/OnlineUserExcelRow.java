package online.yudream.base.interfaces.system.excel.row;

import com.alibaba.excel.annotation.ExcelProperty;
import lombok.Data;

@Data
public class OnlineUserExcelRow {
    @ExcelProperty("用户ID")
    private Long userId;
    @ExcelProperty("用户名")
    private String username;
    @ExcelProperty("昵称")
    private String nickname;
    @ExcelProperty("邮箱")
    private String email;
    @ExcelProperty("设备")
    private String device;
    @ExcelProperty("剩余有效期")
    private Long timeout;
    @ExcelProperty("活跃有效期")
    private Long activeTimeout;
    @ExcelProperty("Token")
    private String token;
}
