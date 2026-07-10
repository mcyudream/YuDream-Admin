package online.yudream.base.domain.platform.satori.enumerate;

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

    public int value() {
        return value;
    }

    public static SatoriOpcode fromValue(int value) {
        for (SatoriOpcode opcode : values()) {
            if (opcode.value == value) {
                return opcode;
            }
        }
        throw new IllegalArgumentException("不支持的 Satori 操作码: " + value);
    }
}
