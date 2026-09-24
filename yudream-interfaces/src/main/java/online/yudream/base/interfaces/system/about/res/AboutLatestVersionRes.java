package online.yudream.base.interfaces.system.about.res;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 单个契约包的最新版本探测结果视图。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AboutLatestVersionRes {

    private String key;
    private String name;
    private String kind;
    private String latest;
    private String sourceUrl;
    private Instant checkedAt;
    private String error;
}
