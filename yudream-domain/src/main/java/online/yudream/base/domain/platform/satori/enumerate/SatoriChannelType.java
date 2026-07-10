package online.yudream.base.domain.platform.satori.enumerate;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

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

    @JsonValue
    public int value() {
        return value;
    }

    @JsonCreator
    public static SatoriChannelType fromValue(int value) {
        for (SatoriChannelType type : values()) {
            if (type.value == value) {
                return type;
            }
        }
        throw new IllegalArgumentException("不支持的 Satori 频道类型: " + value);
    }
}
