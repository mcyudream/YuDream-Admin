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

    private String title;
    private String subtitle;
    private String theme;
    private String heroImageUrl;
    private Map<String, String> settings;
    private List<HomeSection> sections;
    private Boolean published;

    public static HomePageLayout defaultLayout() {
        HomePageLayout layout = new HomePageLayout();
        layout.title = "YuDream";
        layout.subtitle = "自定义首页";
        layout.theme = "default";
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
