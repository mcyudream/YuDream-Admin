package online.yudream.base.domain.platform.milky.aggregate;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;
import online.yudream.base.domain.common.base.BaseDomain;
import online.yudream.base.domain.common.exception.BizException;

@EqualsAndHashCode(callSuper = true)
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class MilkyConnection extends BaseDomain {
    private String name;
    private String baseUrl;
    @ToString.Exclude
    private String token;
    private boolean enabled;
    private String commandMenuImageMode;
    private String commandMenuPublicBaseUrl;

    public static MilkyConnection create(String name, String baseUrl, String token, String commandMenuImageMode, String commandMenuPublicBaseUrl) {
        return MilkyConnection.builder()
                .name(required(name, "连接名称不能为空"))
                .baseUrl(required(baseUrl, "Milky 地址不能为空").replaceAll("/+$", ""))
                .token(required(token, "Access Token 不能为空"))
                .enabled(true)
                .commandMenuImageMode(normalizeMode(commandMenuImageMode))
                .commandMenuPublicBaseUrl(commandMenuPublicBaseUrl)
                .build();
    }

    public void update(String name, String baseUrl, String token, String commandMenuImageMode, String commandMenuPublicBaseUrl) {
        this.name = required(name, "连接名称不能为空");
        this.baseUrl = required(baseUrl, "Milky 地址不能为空").replaceAll("/+$", "");
        if (token != null && !token.isBlank()) {
            this.token = token.trim();
        }
        this.commandMenuImageMode = normalizeMode(commandMenuImageMode);
        this.commandMenuPublicBaseUrl = commandMenuPublicBaseUrl == null ? null : commandMenuPublicBaseUrl.trim();
    }

    private static String normalizeMode(String mode) { return "url".equalsIgnoreCase(mode) ? "url" : "base64"; }

    private static String required(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new BizException(message);
        }
        return value.trim();
    }
}
