package online.yudream.base.interfaces.system.file.res;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class FileObjectRes {
    private Long id;
    private String originalName;
    private String contentType;
    private Long size;
    private String module;
    private String url;
    private LocalDateTime createTime;
}
