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
import java.util.Objects;

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
        PluginModule module = PluginModule.builder()
                .code(descriptor.code())
                .status(PluginStatus.INSTALLED)
                .build();
        DescriptorFields.of(descriptor).applyTo(module);
        return module;
    }

    public void refreshDescriptor(PluginDescriptorInfo descriptor) {
        DescriptorFields.of(descriptor).applyTo(this);
        if (this.status == null) {
            this.status = PluginStatus.INSTALLED;
        }
    }

    /**
     * 声明的元数据是否已与持久化状态一致。
     * <p>
     * 注册表同步据此跳过无变化的写库：读接口并发（表格页同时拉分页与概览、多标签页）
     * 不再各自制造一批版本自增写入，也就不会互撞乐观锁；status 为空的历史记录
     * 一律视为需要补写，保留原有的自愈行为。
     */
    public boolean matchesDescriptor(PluginDescriptorInfo descriptor) {
        return status != null && DescriptorFields.of(descriptor).sameAs(this);
    }

    /**
     * descriptor 派生的持久化字段快照。
     * refreshDescriptor / fromDescriptor / matchesDescriptor 三处共用同一份映射，
     * 避免新增字段时只改一边导致同步判断与写入内容不一致。
     */
    private record DescriptorFields(
            String name,
            String pluginVersion,
            String description,
            String icon,
            String mainClass,
            String jarPath,
            String gitUrl,
            List<String> dependencies,
            List<String> softDependencies,
            List<MobilePlatform> mobilePlatforms,
            String mobileMinHostVersion,
            List<String> mobileRequiredNativeCapabilities,
            String mobileName,
            String mobileDescription,
            String mobileIcon,
            List<MobileHomeCard> mobileHomeCards,
            List<MobileAdminCard> mobileAdminCards,
            String mobileFeedEndpoint,
            String mobileFeedTitle) {

        static DescriptorFields of(PluginDescriptorInfo descriptor) {
            MobilePluginSupport mobile = descriptor.mobileSupport();
            return new DescriptorFields(
                    descriptor.name(),
                    descriptor.version(),
                    descriptor.description(),
                    descriptor.icon(),
                    descriptor.mainClass(),
                    descriptor.jarPath(),
                    descriptor.gitUrl(),
                    descriptor.dependencies(),
                    descriptor.softDependencies(),
                    mobile.declared() ? mobile.platforms() : null,
                    mobile.declared() ? mobile.minHostVersion() : null,
                    mobile.declared() ? mobile.requiredNativeCapabilities() : null,
                    mobile.declared() ? emptyToNull(mobile.name()) : null,
                    mobile.declared() ? emptyToNull(mobile.description()) : null,
                    mobile.declared() ? emptyToNull(mobile.icon()) : null,
                    mobile.declared() && !mobile.homeCards().isEmpty() ? mobile.homeCards() : null,
                    mobile.declared() && !mobile.adminCards().isEmpty() ? mobile.adminCards() : null,
                    mobile.declared() && mobile.homeFeed() != null ? emptyToNull(mobile.homeFeed().endpoint()) : null,
                    mobile.declared() && mobile.homeFeed() != null ? emptyToNull(mobile.homeFeed().title()) : null);
        }

        void applyTo(PluginModule module) {
            module.name = name;
            module.pluginVersion = pluginVersion;
            module.description = description;
            module.icon = icon;
            module.mainClass = mainClass;
            module.jarPath = jarPath;
            module.gitUrl = gitUrl;
            module.dependencies = dependencies;
            module.softDependencies = softDependencies;
            module.mobilePlatforms = mobilePlatforms;
            module.mobileMinHostVersion = mobileMinHostVersion;
            module.mobileRequiredNativeCapabilities = mobileRequiredNativeCapabilities;
            module.mobileName = mobileName;
            module.mobileDescription = mobileDescription;
            module.mobileIcon = mobileIcon;
            module.mobileHomeCards = mobileHomeCards;
            module.mobileAdminCards = mobileAdminCards;
            module.mobileFeedEndpoint = mobileFeedEndpoint;
            module.mobileFeedTitle = mobileFeedTitle;
        }

        boolean sameAs(PluginModule module) {
            return Objects.equals(name, module.name)
                    && Objects.equals(pluginVersion, module.pluginVersion)
                    && Objects.equals(description, module.description)
                    && Objects.equals(icon, module.icon)
                    && Objects.equals(mainClass, module.mainClass)
                    && Objects.equals(jarPath, module.jarPath)
                    && Objects.equals(gitUrl, module.gitUrl)
                    && Objects.equals(dependencies, module.dependencies)
                    && Objects.equals(softDependencies, module.softDependencies)
                    && Objects.equals(mobilePlatforms, module.mobilePlatforms)
                    && Objects.equals(mobileMinHostVersion, module.mobileMinHostVersion)
                    && Objects.equals(mobileRequiredNativeCapabilities, module.mobileRequiredNativeCapabilities)
                    && Objects.equals(mobileName, module.mobileName)
                    && Objects.equals(mobileDescription, module.mobileDescription)
                    && Objects.equals(mobileIcon, module.mobileIcon)
                    && Objects.equals(mobileHomeCards, module.mobileHomeCards)
                    && Objects.equals(mobileAdminCards, module.mobileAdminCards)
                    && Objects.equals(mobileFeedEndpoint, module.mobileFeedEndpoint)
                    && Objects.equals(mobileFeedTitle, module.mobileFeedTitle);
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
