package online.yudream.base.interfaces.platform.mail.res;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class InboundMailAttachmentRes {
    private String filename;
    private String contentType;
    private Long size;
    private Boolean inline;
}
