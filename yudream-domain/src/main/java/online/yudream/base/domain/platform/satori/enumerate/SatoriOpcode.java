package online.yudream.base.domain.platform.satori.enumerate;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/** Satori WebSocket 协议操作码。 */
public enum SatoriOpcode {
    EVENT(0),
    PING(1),
    PONG(2),
    IDENTIFY(3),
    READY(4),
    META(5);

    private final int value;

    SatoriOpcode(int value) {
        this.value = value;
    }

    @JsonValue
    public int value() {
        return value;
    }

    @JsonCreator
    public static SatoriOpcode fromValue(int value) {
        for (SatoriOpcode opcode : values()) {
            if (opcode.value == value) {
                return opcode;
            }
        }
        throw new IllegalArgumentException("不支持的 Satori 操作码: " + value);
    }
}
