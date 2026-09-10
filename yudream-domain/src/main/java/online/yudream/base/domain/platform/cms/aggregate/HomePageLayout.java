package online.yudream.base.domain.platform.cms.aggregate;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import online.yudream.base.domain.common.base.BaseDomain;
import online.yudream.base.domain.platform.cms.valobj.HomeSection;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@EqualsAndHashCode(callSuper = true)
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class HomePageLayout extends BaseDomain {

    /** 内置默认主题的主题编码；插件主题为其插件 code。 */
    public static final String DEFAULT_THEME_CODE = "default";

    /** 归属主题编码：每个主题各持有一套完全独立的首页布局。 */
    private String themeCode;
    private String title;
    private String subtitle;
    /** 该布局当前应用的方案编码（如 plugin:{code}、user-xxx），用于方案列表的「当前」标记。 */
    private String theme;
    private String heroImageUrl;
    private Map<String, String> settings;
    private List<HomeSection> sections;
    private Boolean published;

    public static HomePageLayout defaultLayout(String themeCode) {
        HomePageLayout layout = new HomePageLayout();
        layout.themeCode = themeCode == null ? DEFAULT_THEME_CODE : themeCode;
        layout.title = "YuDream";
        layout.subtitle = "自定义首页";
        layout.theme = DEFAULT_THEME_CODE;
        layout.settings = new HashMap<>();
        layout.sections = new ArrayList<>();
        layout.published = false;
        return layout;
    }

    public void update(String title, String subtitle, String theme, String heroImageUrl,
                       Map<String, String> settings, List<HomeSection> sections, Boolean published) {
        this.title = title;
        this.subtitle = subtitle;
        this.theme = theme;
        this.heroImageUrl = heroImageUrl;
        this.settings = new HashMap<>(settings == null ? Map.of() : settings);
        this.sections = new ArrayList<>(sections == null ? List.of() : sections);
        this.published = Boolean.TRUE.equals(published);
    }
}
