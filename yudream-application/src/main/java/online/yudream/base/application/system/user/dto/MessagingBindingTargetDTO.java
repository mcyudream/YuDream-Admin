package online.yudream.base.application.system.user.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MessagingBindingTargetDTO implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private String connectionId;
    private String name;
    private String protocol;
    private String botName;
    private String appId;
    private boolean bound;
}