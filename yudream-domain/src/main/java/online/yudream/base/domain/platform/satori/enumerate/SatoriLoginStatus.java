package online.yudream.base.domain.platform.satori.enumerate;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/** Satori 登录状态。 */
public enum SatoriLoginStatus {
    OFFLINE(0),
    ONLINE(1),
    CONNECT(2),
    DISCONNECT(3),
    RECONNECT(4);

    private final int value;

    SatoriLoginStatus(int value) {
        this.value = value;
    }

    @JsonValue
    public int value() {
        return value;
    }

    @JsonCreator
    public static SatoriLoginStatus fromValue(int value) {
        for (SatoriLoginStatus status : values()) {
            if (status.value == value) {
                return status;
            }
        }
        throw new IllegalArgumentException("不支持的 Satori 登录状态: " + value);
    }
}
