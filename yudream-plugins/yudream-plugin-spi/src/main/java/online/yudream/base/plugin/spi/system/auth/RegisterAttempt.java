package online.yudream.base.plugin.spi.system.auth;

import java.util.Map;

/**
 * 一次注册尝试的快照。attributes 为可扩展兜底字段，宿主只增不改不删；
 * 当前版本为空 Map，未来可携带来源渠道、客户端元数据等。
 */
public record RegisterAttempt(String username, String nickname, String email, Map<String, Object> attributes) {

    public RegisterAttempt {
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
    }
}
