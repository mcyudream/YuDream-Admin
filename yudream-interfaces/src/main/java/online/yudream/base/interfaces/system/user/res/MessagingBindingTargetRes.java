package online.yudream.base.interfaces.system.user.res;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MessagingBindingTargetRes {
    private String connectionId;
    private String name;
    private String protocol;
    private String botName;
    private String appId;
    private boolean bound;
}