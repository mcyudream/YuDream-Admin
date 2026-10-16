package online.yudream.base.domain.platform.feishu.valobj;

import java.util.List;

/**
 * 飞书分页结果：items + 下一页游标 + 是否还有更多。
 */
public record FeishuDocPage<T>(List<T> items, String nextPageToken, boolean hasMore) {

    public FeishuDocPage {
        items = items == null ? List.of() : List.copyOf(items);
    }

    public static <T> FeishuDocPage<T> empty() {
        return new FeishuDocPage<>(List.of(), null, false);
    }
}
