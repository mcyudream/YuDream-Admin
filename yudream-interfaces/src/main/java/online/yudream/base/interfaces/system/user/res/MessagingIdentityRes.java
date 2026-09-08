package online.yudream.base.interfaces.system.user.res;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MessagingIdentityRes {
    private String protocol;
    private String identityType;
    private String identity;
    private String groupOpenid;
    private String appId;
    private String connectionId;
}
