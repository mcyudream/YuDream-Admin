package online.yudream.base.interfaces.platform.satori.res;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class SatoriChatMessagePageRes {
    private List<SatoriChatMessageRes> records;
    private String prev;
    private String next;
}
