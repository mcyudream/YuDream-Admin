package online.yudream.base.interfaces.platform.satori.res;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class SatoriConversationPageRes {
    private List<SatoriConversationRes> records;
    private String next;
}
