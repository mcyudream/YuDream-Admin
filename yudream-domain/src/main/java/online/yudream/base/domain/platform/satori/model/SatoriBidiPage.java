package online.yudream.base.domain.platform.satori.model;

import java.util.List;

/** Satori 的双向游标分页响应。 */
public record SatoriBidiPage<T>(List<T> data, String prev, String next) {

    public SatoriBidiPage {
        data = data == null ? List.of() : List.copyOf(data);
    }
}
