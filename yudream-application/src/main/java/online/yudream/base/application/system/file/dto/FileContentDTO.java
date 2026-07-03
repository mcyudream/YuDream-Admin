package online.yudream.base.application.system.file.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.InputStream;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FileContentDTO {

    private String originalName;
    private String contentType;
    private Long contentLength;
    private InputStream inputStream;
}
