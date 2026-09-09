package online.yudream.base.application.platform.mail.dto;

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
public class InboundMailAttachmentDTO implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private String filename;
    private String contentType;
    private Long size;
    private Boolean inline;
}
