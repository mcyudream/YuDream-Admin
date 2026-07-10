package online.yudream.base.domain.platform.satori.message;

import java.util.List;

/**
 * 待发送的不可变 Satori 消息。
 */
public record SatoriMessage(List<SatoriElement> elements) {

    public SatoriMessage {
        elements = elements == null ? List.of() : List.copyOf(elements);
    }

    public static SatoriMessage empty() {
        return new SatoriMessage(List.of());
    }
}
