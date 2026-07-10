package online.yudream.base.domain.platform.satori.model;

import java.util.List;

/** Satori 的单向游标分页响应。 */
public record SatoriPage<T>(List<T> data, String next) {

    public SatoriPage {
        data = data == null ? List.of() : List.copyOf(data);
    }
}
