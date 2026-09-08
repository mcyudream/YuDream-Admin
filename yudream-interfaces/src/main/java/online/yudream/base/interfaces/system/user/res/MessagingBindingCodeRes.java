package online.yudream.base.interfaces.system.user.res;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MessagingBindingCodeRes {
    private String code;
    private Instant expiresAt;
    private String connectionId;
    private String connectionName;
    private String protocol;
    private String botName;
}