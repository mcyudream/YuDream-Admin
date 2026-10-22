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
    /** 分段读取时：文件总长度与本段起止（闭区间）；整文件读取时为 null */
    private Long totalLength;
    private Long rangeStart;
    private Long rangeEnd;

    public boolean partial() {
        return rangeStart != null;
    }
}
