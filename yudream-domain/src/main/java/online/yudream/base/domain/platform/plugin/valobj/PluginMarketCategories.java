package online.yudream.base.domain.platform.plugin.valobj;

import java.util.List;

/** 市场源内置插件分类清单：发布/编辑按此校验，v2 协议 /categories 下发。 */
public final class PluginMarketCategories {

    public static final List<String> ALL = List.of(
            "AI 与对话",
            "支付与钱包",
            "Minecraft",
            "消息与社区",
            "数据与看板",
            "主题与皮肤",
            "效率工具",
            "其他");

    private PluginMarketCategories() {
    }

    /** 空值视为未分类，合法；非清单值拒绝。 */
    public static boolean isValid(String category) {
        return category == null || category.isBlank() || ALL.contains(category.trim());
    }
}
