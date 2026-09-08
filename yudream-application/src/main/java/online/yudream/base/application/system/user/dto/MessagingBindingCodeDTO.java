package online.yudream.base.application.system.user.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MessagingBindingCodeDTO implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private String code;
    private Instant expiresAt;
    private String connectionId;
    private String connectionName;
    private String protocol;
    private String botName;
}