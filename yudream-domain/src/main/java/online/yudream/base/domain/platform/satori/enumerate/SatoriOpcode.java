package online.yudream.base.domain.platform.satori.enumerate;

/** Satori WebSocket 协议操作码。 */
public enum SatoriOpcode {
    EVENT,
    PING,
    PONG,
    IDENTIFY,
    READY,
    META
}
