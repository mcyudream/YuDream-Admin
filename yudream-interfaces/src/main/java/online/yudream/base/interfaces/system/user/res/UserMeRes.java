package online.yudream.base.interfaces.system.user.res;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 当前登录用户极简信息：移动 App 展示昵称/头像/用户名。
 * 头像为文件 URL，可能是相对资产路径，由客户端自行拼接 baseUrl。
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class UserMeRes implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String id;
    private String username;
    private String nickname;
    private String avatar;
}
