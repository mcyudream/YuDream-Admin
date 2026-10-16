package online.yudream.base.plugin.spi.system.feishu;

import java.util.List;

/**
 * 飞书云文档分页结果：固定携带下一页游标与是否还有更多。
 *
 * @param items         本页条目
 * @param nextPageToken 下一页游标；无下一页时为空串
 * @param hasMore       是否还有更多
 * @param <T>           条目类型
 */
public record PluginFeishuDocPage<T>(
        List<T> items,
        String nextPageToken,
        boolean hasMore
) {
    public PluginFeishuDocPage {
        items = items == null ? List.of() : List.copyOf(items);
        nextPageToken = nextPageToken == null ? "" : nextPageToken;
    }

    public static <T> PluginFeishuDocPage<T> empty() {
        return new PluginFeishuDocPage<>(List.of(), "", false);
    }
}
