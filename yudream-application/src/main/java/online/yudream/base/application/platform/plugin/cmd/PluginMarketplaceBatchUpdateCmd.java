package online.yudream.base.application.platform.plugin.cmd;

import lombok.Data;

import java.util.List;

/** 批量更新市场插件：按传入顺序逐项更新（调用方已剔除更新受阻项），单项失败不影响其余。 */
@Data
public class PluginMarketplaceBatchUpdateCmd {

    private List<Item> items;

    @Data
    public static class Item {

        private String code;
        private String releaseVersion;
        private String sourceCode;
    }
}
