package online.yudream.base.interfaces.system.security.res;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ExternalLoginProviderRes {
    private String code;
    private String name;
    private String protocol;
    private String appId;
    private String callbackUrl;
    private String endpoint;
    private boolean enabled;
    private String supportedTypes;
    private String icon;
    private boolean pluginManaged;
    /** 插件登录入口的呈现方式：ICON（图标按钮）/ TAB（与 Passkey 并列的登录 Tab）。 */
    private String presentation;
    /** 插件登录入口的排序位；登录 Tab 按它升序排列（内置 Tab 基线：账号密码 100、Passkey 200）。 */
    private int sort;
}
