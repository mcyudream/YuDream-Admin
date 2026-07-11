package online.yudream.base.application.platform.satori.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class SatoriConversationPageDTO {
    private List<SatoriConversationDTO> records;
    private String next;
}
