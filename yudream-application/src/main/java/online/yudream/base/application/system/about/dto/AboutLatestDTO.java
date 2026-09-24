package online.yudream.base.application.system.about.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 最新版本探测整体返回：enabled=false 表示管理员关闭了出站探测，entries 为空。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AboutLatestDTO {

    private boolean enabled;
    private List<AboutLatestVersionDTO> entries;
}
