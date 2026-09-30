package online.yudream.base.domain.platform.plugin.aggregate;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import online.yudream.base.domain.common.base.BaseDomain;
import online.yudream.base.domain.platform.mobile.enumerate.MobilePlatform;
import online.yudream.base.domain.platform.mobile.valobj.MobileAdminCard;
import online.yudream.base.domain.platform.mobile.valobj.MobileHomeCard;
import online.yudream.base.domain.platform.mobile.valobj.MobileHomeFeed;
import online.yudream.base.domain.platform.mobile.valobj.MobilePluginSupport;
import online.yudream.base.domain.platform.plugin.enumerate.PluginStatus;
import online.yudream.base.domain.platform.plugin.valobj.PluginDescriptorInfo;

import java.time.LocalDateTime;
import java.util.List;

@EqualsAndHashCode(callSuper = true)
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class PluginModule extends BaseDomain {

    private String code;
    private String name;
    private String pluginVersion;
    private String description;
    private String icon;
    private String mainClass;
    private String jarPath;
    /** plugin.yml 可选 git 声明的源码仓库地址，仅展示用途。 */
    private String gitUrl;
    private String backupJarPath;
    private String backupName;
    private String backupPluginVersion;
    private String backupDescription;
    private String backupMainClass;
    private String backupSha256;
    private List<String> backupDependencies;
    private List<String> backupSoftDependencies;
    private PluginStatus backupStatus;
    private String backupErrorMessage;
    private LocalDateTime backupLoadedAt;
    private LocalDateTime backupEnabledAt;
    private Boolean backupMenusInitialized;
    private Boolean backupRestoreIntentActive;
    private Boolean restoreIntentActive;
    private List<String> dependencies;
    private List<String> softDependencies;
    private PluginStatus status;
    private String errorMessage;
    private LocalDateTime loadedAt;
    private LocalDateTime enabledAt;
    private Boolean menusInitialized;
    /** 最近一次成功启用时声明的主题作用域（SITE/ADMIN），用于主题中心识别已安装但未启用的主题插件。 */
    private List<String> themeScopes;
    /** 安装来源市场源 code；本地上传或历史数据为 null，registry 同步不回写该字段。 */
    private String marketSourceCode;
    /** plugin.yml mobile 块持久化：支持平台（未声明移动支持时为 null）。 */
    private List<MobilePlatform> mobilePlatforms;
    /** plugin.yml mobile 块持久化：宿主最低版本（未声明时为 null）。 */
    private String mobileMinHostVersion;
    /** plugin.yml mobile 块持久化：要求的原生能力（未声明时为 null）。 */
    private List<String> mobileRequiredNativeCapabilities;
    /** plugin.yml mobile 块持久化：移动端展示名（未声明时为 null）。 */
    private String mobileName;
    /** plugin.yml mobile 块持久化：移动端描述（未声明时为 null）。 */
    private String mobileDescription;
    /** plugin.yml mobile 块持久化：移动端图标（Ionicons 名，未声明时为 null）。 */
    private String mobileIcon;
    /** plugin.yml mobile 块持久化：移动端主页卡片声明（未声明时为 null）。 */
    private List<MobileHomeCard> mobileHomeCards;
    /** plugin.yml mobile.admin.cards 管理入口卡声明（未声明时为 null）。 */
    private List<MobileAdminCard> mobileAdminCards;
    /** plugin.yml mobile 块持久化：移动端首页信息流内容源端点（未声明时为 null）。 */
    private String mobileFeedEndpoint;
    /** plugin.yml mobile 块持久化：信息流分节标题（未声明时为 null）。 */
    private String mobileFeedTitle;

    public static PluginModule fromDescriptor(PluginDescriptorInfo descriptor) {
        MobilePluginSupport mobile = descriptor.mobileSupport();
        return PluginModule.builder()
                .code(descriptor.code())
                .name(descriptor.name())
                .pluginVersion(descriptor.version())
                .description(descriptor.description())
                .icon(descriptor.icon())
                .mainClass(descriptor.mainClass())
                .jarPath(descriptor.jarPath())
                .gitUrl(descriptor.gitUrl())
                .dependencies(descriptor.dependencies())
                .softDependencies(descriptor.softDependencies())
                .mobilePlatforms(mobile.declared() ? mobile.platforms() : null)
                .mobileMinHostVersion(mobile.declared() ? mobile.minHostVersion() : null)
                .mobileRequiredNativeCapabilities(mobile.declared() ? mobile.requiredNativeCapabilities() : null)
                .mobileName(mobile.declared() ? emptyToNull(mobile.name()) : null)
                .mobileDescription(mobile.declared() ? emptyToNull(mobile.description()) : null)
                .mobileIcon(mobile.declared() ? emptyToNull(mobile.icon()) : null)
                .mobileHomeCards(mobile.declared() && !mobile.homeCards().isEmpty() ? mobile.homeCards() : null)
                .mobileAdminCards(mobile.declared() && !mobile.adminCards().isEmpty() ? mobile.adminCards() : null)
                .mobileFeedEndpoint(mobile.declared() && mobile.homeFeed() != null
                        ? emptyToNull(mobile.homeFeed().endpoint()) : null)
                .mobileFeedTitle(mobile.declared() && mobile.homeFeed() != null
                        ? emptyToNull(mobile.homeFeed().title()) : null)
                .status(PluginStatus.INSTALLED)
                .build();
    }

    public void refreshDescriptor(PluginDescriptorInfo descriptor) {
        MobilePluginSupport mobile = descriptor.mobileSupport();
        this.name = descriptor.name();
        this.pluginVersion = descriptor.version();
        this.description = descriptor.description();
        this.icon = descriptor.icon();
        this.mainClass = descriptor.mainClass();
        this.jarPath = descriptor.jarPath();
        this.gitUrl = descriptor.gitUrl();
        this.dependencies = descriptor.dependencies();
        this.softDependencies = descriptor.softDependencies();
        this.mobilePlatforms = mobile.declared() ? mobile.platforms() : null;
        this.mobileMinHostVersion = mobile.declared() ? mobile.minHostVersion() : null;
        this.mobileRequiredNativeCapabilities = mobile.declared() ? mobile.requiredNativeCapabilities() : null;
        this.mobileName = mobile.declared() ? emptyToNull(mobile.name()) : null;
        this.mobileDescription = mobile.declared() ? emptyToNull(mobile.description()) : null;
        this.mobileIcon = mobile.declared() ? emptyToNull(mobile.icon()) : null;
        this.mobileHomeCards = mobile.declared() && !mobile.homeCards().isEmpty() ? mobile.homeCards() : null;
        this.mobileAdminCards = mobile.declared() && !mobile.adminCards().isEmpty() ? mobile.adminCards() : null;
        this.mobileFeedEndpoint = mobile.declared() && mobile.homeFeed() != null
                ? emptyToNull(mobile.homeFeed().endpoint()) : null;
        this.mobileFeedTitle = mobile.declared() && mobile.homeFeed() != null
                ? emptyToNull(mobile.homeFeed().title()) : null;
        if (this.status == null) {
            this.status = PluginStatus.INSTALLED;
        }
    }

    /** 持久化字段重建移动支持声明；从未声明 mobile 时返回 undeclared。 */
    public MobilePluginSupport mobileSupport() {
        if (mobilePlatforms == null || mobilePlatforms.isEmpty()) {
            return MobilePluginSupport.undeclared();
        }
        MobileHomeFeed homeFeed = mobileFeedEndpoint == null
                ? null
                : new MobileHomeFeed(mobileFeedEndpoint, mobileFeedTitle);
        return MobilePluginSupport.declared(mobilePlatforms, mobileMinHostVersion, mobileRequiredNativeCapabilities,
                mobileName, mobileDescription, mobileIcon, mobileHomeCards, homeFeed, mobileAdminCards);
    }

    /** 空串归一为 null，与未声明字段保持同一持久化形态。 */
    private static String emptyToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    public void markLoaded() {
        this.status = PluginStatus.LOADED;
        this.errorMessage = null;
        this.loadedAt = LocalDateTime.now();
    }

    public void markEnabled() {
        this.status = PluginStatus.ENABLED;
        this.errorMessage = null;
        this.enabledAt = LocalDateTime.now();
    }

    public void markDisabled() {
        this.status = PluginStatus.DISABLED;
        this.enabledAt = null;
    }

    public void markUnloaded() {
        this.status = PluginStatus.INSTALLED;
        this.loadedAt = null;
        this.enabledAt = null;
    }

    public void markError(String message) {
        this.status = PluginStatus.ERROR;
        this.errorMessage = message;
    }

    public boolean menusInitialized() {
        return Boolean.TRUE.equals(menusInitialized);
    }

    public void markMenusInitialized() {
        this.menusInitialized = true;
    }

    public boolean enabled() {
        return this.status == PluginStatus.ENABLED;
    }

}
