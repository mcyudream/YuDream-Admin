package online.yudream.base.application.platform.plugin.cmd;

import lombok.Data;

/** 测试尚未保存的市场源表单：按给定地址做一次只读目录探测。 */
@Data
public class PluginMarketSourceTestCmd {

    private String rootUrl;
    private String token;
}
