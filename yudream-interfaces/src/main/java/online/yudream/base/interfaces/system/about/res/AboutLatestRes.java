package online.yudream.base.interfaces.system.about.res;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 最新版本探测整体视图：enabled=false 表示出站探测被配置关闭。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AboutLatestRes {

    private boolean enabled;
    private List<AboutLatestVersionRes> entries;
}
