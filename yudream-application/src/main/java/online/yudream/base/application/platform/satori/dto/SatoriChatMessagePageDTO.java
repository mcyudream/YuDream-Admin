package online.yudream.base.application.platform.satori.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class SatoriChatMessagePageDTO {
    private List<SatoriChatMessageDTO> records;
    private String prev;
    private String next;
}
