package online.yudream.base.application.platform.satori.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SatoriChatMemberDTO {
    private String userId;
    private String name;
    private String avatar;
}
