package online.yudream.base.interfaces.platform.satori.res;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SatoriChatMemberRes {
    private String userId;
    private String name;
    private String avatar;
}
