package online.yudream.base.domain.platform.satori.enumerate;

/** Satori 定义的频道类型。 */
public enum SatoriChannelType {
    TEXT(0),
    DIRECT(1),
    CATEGORY(2),
    VOICE(3);

    private final int value;

    SatoriChannelType(int value) {
        this.value = value;
    }

    public int value() {
        return value;
    }

    public static SatoriChannelType fromValue(int value) {
        for (SatoriChannelType type : values()) {
            if (type.value == value) {
                return type;
            }
        }
        throw new IllegalArgumentException("不支持的 Satori 频道类型: " + value);
    }
}
