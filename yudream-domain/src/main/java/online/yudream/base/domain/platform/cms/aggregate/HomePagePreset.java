package online.yudream.base.domain.platform.cms.aggregate;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import online.yudream.base.domain.common.base.BaseDomain;
import online.yudream.base.domain.platform.cms.enumerate.HomePagePresetSource;
import online.yudream.base.domain.platform.cms.valobj.HomeSection;

import java.util.List;
import java.util.Map;

/**
 * 首页内容定制方案：一份可储存、可一键切换的 {@link HomePageLayout} 快照。
 * 应用方案时宿主先对当前定制做自动快照（SNAPSHOT 来源），再整体回写，
 * 因此切换永远可回滚。
 */
@EqualsAndHashCode(callSuper = true)
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class HomePagePreset extends BaseDomain {

    /** 方案编码，全局唯一；插件自带方案固定为 plugin:{pluginCode}。 */
    private String code;
    private String name;
    private String description;
    private HomePagePresetSource source;
    /** 来源插件编码，仅 PLUGIN 来源有值。 */
    private String pluginCode;

    private String title;
    private String subtitle;
    private String theme;
    private String heroImageUrl;
    private Map<String, String> settings;
    private List<HomeSection> sections;

    /**
     * 方案快照与当前布局内容是否一致（用于自动快照去重，与名称/来源无关）。
     */
    public boolean sameContent(HomePageLayout layout) {
        return java.util.Objects.equals(title, layout.getTitle())
                && java.util.Objects.equals(subtitle, layout.getSubtitle())
                && java.util.Objects.equals(theme, layout.getTheme())
                && java.util.Objects.equals(heroImageUrl, layout.getHeroImageUrl())
                && java.util.Objects.equals(settings, layout.getSettings())
                && java.util.Objects.equals(sections, layout.getSections());
    }

    public static HomePagePreset snapshotOf(String code, String name, String description,
                                            HomePagePresetSource source, String pluginCode,
                                            HomePageLayout layout) {
        return HomePagePreset.builder()
                .code(code)
                .name(name)
                .description(description)
                .source(source)
                .pluginCode(pluginCode)
                .title(layout.getTitle())
                .subtitle(layout.getSubtitle())
                .theme(layout.getTheme())
                .heroImageUrl(layout.getHeroImageUrl())
                .settings(layout.getSettings() == null ? Map.of() : Map.copyOf(layout.getSettings()))
                .sections(layout.getSections() == null ? List.of() : List.copyOf(layout.getSections()))
                .build();
    }
}
