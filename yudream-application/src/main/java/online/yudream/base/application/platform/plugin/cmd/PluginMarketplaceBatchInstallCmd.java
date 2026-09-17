package online.yudream.base.application.platform.plugin.cmd;

import lombok.Data;

import java.util.List;

/** 批量安装市场插件：按传入顺序逐项安装（依赖必须排在消费方之前），任一项失败整体回滚。 */
@Data
public class PluginMarketplaceBatchInstallCmd {

    private List<Item> items;

    @Data
    public static class Item {

        private String code;
        private String releaseVersion;
        private String sourceCode;
    }
}
