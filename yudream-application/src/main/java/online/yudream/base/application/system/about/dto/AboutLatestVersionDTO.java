package online.yudream.base.application.system.about.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 契约包最新版本探测结果。latest 为空表示探测失败（error 带原因）；前端与本地当前版本比对得出「可更新」。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AboutLatestVersionDTO {

    private String key;
    private String name;
    /** MAVEN / NPM */
    private String kind;
    private String latest;
    private String sourceUrl;
    private Instant checkedAt;
    private String error;
}
