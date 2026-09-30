package online.yudream.base.infra.platform.plugin.mapper;

import online.yudream.base.domain.platform.mobile.enumerate.MobilePlatform;
import online.yudream.base.domain.platform.mobile.valobj.MobileHomeCard;
import online.yudream.base.domain.platform.plugin.aggregate.PluginModule;
import online.yudream.base.infra.platform.plugin.dataobj.PluginModuleDO;

import java.util.List;

public class PluginModuleInfraMapper {

    private PluginModuleInfraMapper() {
    }

    public static PluginModuleDO toDataObj(PluginModule module) {
        if (module == null) {
            return null;
        }
        PluginModuleDO dataObj = new PluginModuleDO();
        dataObj.setId(module.getId());
        dataObj.setCode(module.getCode());
        dataObj.setName(module.getName());
        dataObj.setPluginVersion(module.getPluginVersion());
        dataObj.setDescription(module.getDescription());
        dataObj.setIcon(module.getIcon());
        dataObj.setMainClass(module.getMainClass());
        dataObj.setJarPath(module.getJarPath());
        dataObj.setGitUrl(module.getGitUrl());
        dataObj.setBackupJarPath(module.getBackupJarPath());
        dataObj.setBackupName(module.getBackupName());
        dataObj.setBackupPluginVersion(module.getBackupPluginVersion());
        dataObj.setBackupDescription(module.getBackupDescription());
        dataObj.setBackupMainClass(module.getBackupMainClass());
        dataObj.setBackupSha256(module.getBackupSha256());
        dataObj.setBackupDependencies(module.getBackupDependencies());
        dataObj.setBackupSoftDependencies(module.getBackupSoftDependencies());
        dataObj.setBackupStatus(module.getBackupStatus());
        dataObj.setBackupErrorMessage(module.getBackupErrorMessage());
        dataObj.setBackupLoadedAt(module.getBackupLoadedAt());
        dataObj.setBackupEnabledAt(module.getBackupEnabledAt());
        dataObj.setBackupMenusInitialized(module.getBackupMenusInitialized());
        dataObj.setBackupRestoreIntentActive(module.getBackupRestoreIntentActive());
        dataObj.setRestoreIntentActive(module.getRestoreIntentActive());
        dataObj.setDependencies(module.getDependencies());
        dataObj.setSoftDependencies(module.getSoftDependencies());
        dataObj.setStatus(module.getStatus());
        dataObj.setErrorMessage(module.getErrorMessage());
        dataObj.setLoadedAt(module.getLoadedAt());
        dataObj.setEnabledAt(module.getEnabledAt());
        dataObj.setMenusInitialized(module.getMenusInitialized());
        dataObj.setThemeScopes(module.getThemeScopes());
        dataObj.setMarketSourceCode(module.getMarketSourceCode());
        dataObj.setMobilePlatforms(toPlatformTokens(module.getMobilePlatforms()));
        dataObj.setMobileMinHostVersion(module.getMobileMinHostVersion());
        dataObj.setMobileRequiredNativeCapabilities(module.getMobileRequiredNativeCapabilities());
        dataObj.setMobileName(module.getMobileName());
        dataObj.setMobileDescription(module.getMobileDescription());
        dataObj.setMobileIcon(module.getMobileIcon());
        dataObj.setMobileHomeCards(toHomeCardDOs(module.getMobileHomeCards()));
        dataObj.setMobileAdminCards(toAdminCardDOs(module.getMobileAdminCards()));
        dataObj.setMobileFeedEndpoint(module.getMobileFeedEndpoint());
        dataObj.setMobileFeedTitle(module.getMobileFeedTitle());
        dataObj.setVersion(module.getVersion());
        dataObj.setCreateTime(module.getCreateTime());
        dataObj.setUpdateTime(module.getUpdateTime());
        return dataObj;
    }

    public static PluginModule toDomain(PluginModuleDO dataObj) {
        if (dataObj == null) {
            return null;
        }
        return PluginModule.builder()
                .id(dataObj.getId())
                .code(dataObj.getCode())
                .name(dataObj.getName())
                .pluginVersion(dataObj.getPluginVersion())
                .description(dataObj.getDescription())
                .icon(dataObj.getIcon())
                .mainClass(dataObj.getMainClass())
                .jarPath(dataObj.getJarPath())
                .gitUrl(dataObj.getGitUrl())
                .backupJarPath(dataObj.getBackupJarPath())
                .backupName(dataObj.getBackupName())
                .backupPluginVersion(dataObj.getBackupPluginVersion())
                .backupDescription(dataObj.getBackupDescription())
                .backupMainClass(dataObj.getBackupMainClass())
                .backupSha256(dataObj.getBackupSha256())
                .backupDependencies(dataObj.getBackupDependencies())
                .backupSoftDependencies(dataObj.getBackupSoftDependencies())
                .backupStatus(dataObj.getBackupStatus())
                .backupErrorMessage(dataObj.getBackupErrorMessage())
                .backupLoadedAt(dataObj.getBackupLoadedAt())
                .backupEnabledAt(dataObj.getBackupEnabledAt())
                .backupMenusInitialized(dataObj.getBackupMenusInitialized())
                .backupRestoreIntentActive(dataObj.getBackupRestoreIntentActive())
                .restoreIntentActive(dataObj.getRestoreIntentActive())
                .dependencies(dataObj.getDependencies())
                .softDependencies(dataObj.getSoftDependencies())
                .status(dataObj.getStatus())
                .errorMessage(dataObj.getErrorMessage())
                .loadedAt(dataObj.getLoadedAt())
                .enabledAt(dataObj.getEnabledAt())
                .menusInitialized(dataObj.getMenusInitialized())
                .themeScopes(dataObj.getThemeScopes())
                .marketSourceCode(dataObj.getMarketSourceCode())
                .mobilePlatforms(toPlatforms(dataObj.getMobilePlatforms()))
                .mobileMinHostVersion(dataObj.getMobileMinHostVersion())
                .mobileRequiredNativeCapabilities(dataObj.getMobileRequiredNativeCapabilities())
                .mobileName(dataObj.getMobileName())
                .mobileDescription(dataObj.getMobileDescription())
                .mobileIcon(dataObj.getMobileIcon())
                .mobileHomeCards(toHomeCards(dataObj.getMobileHomeCards()))
                .mobileAdminCards(toAdminCards(dataObj.getMobileAdminCards()))
                .mobileFeedEndpoint(dataObj.getMobileFeedEndpoint())
                .mobileFeedTitle(dataObj.getMobileFeedTitle())
                .version(dataObj.getVersion())
                .createTime(dataObj.getCreateTime())
                .updateTime(dataObj.getUpdateTime())
                .build();
    }

    private static List<String> toPlatformTokens(List<MobilePlatform> platforms) {
        if (platforms == null) {
            return null;
        }
        return platforms.stream().map(MobilePlatform::token).toList();
    }

    private static List<MobilePlatform> toPlatforms(List<String> tokens) {
        if (tokens == null) {
            return null;
        }
        return tokens.stream()
                .filter(token -> token != null && !token.isBlank())
                .map(MobilePlatform::fromToken)
                .toList();
    }

    private static List<PluginModuleDO.MobileHomeCardDO> toHomeCardDOs(List<MobileHomeCard> cards) {
        if (cards == null) {
            return null;
        }
        return cards.stream()
                .filter(card -> card != null)
                .map(card -> new PluginModuleDO.MobileHomeCardDO(
                        card.id(), card.title(), card.description(), card.icon(), card.route(), card.permission()))
                .toList();
    }

    private static List<MobileHomeCard> toHomeCards(List<PluginModuleDO.MobileHomeCardDO> cards) {
        if (cards == null) {
            return null;
        }
        return cards.stream()
                .filter(card -> card != null)
                .map(card -> new MobileHomeCard(
                        card.getId(), card.getTitle(), card.getDescription(), card.getIcon(), card.getRoute(), card.getPermission()))
                .toList();
    }

    private static List<PluginModuleDO.MobileHomeCardDO> toAdminCardDOs(List<online.yudream.base.domain.platform.mobile.valobj.MobileAdminCard> cards) {
        if (cards == null) {
            return null;
        }
        return cards.stream()
                .filter(card -> card != null)
                .map(card -> new PluginModuleDO.MobileHomeCardDO(
                        card.id(), card.title(), card.description(), card.icon(), card.route(), card.permission()))
                .toList();
    }

    private static List<online.yudream.base.domain.platform.mobile.valobj.MobileAdminCard> toAdminCards(List<PluginModuleDO.MobileHomeCardDO> cards) {
        if (cards == null) {
            return null;
        }
        return cards.stream()
                .filter(card -> card != null)
                .map(card -> new online.yudream.base.domain.platform.mobile.valobj.MobileAdminCard(
                        card.getId(), card.getTitle(), card.getDescription(), card.getIcon(), card.getRoute(), card.getPermission()))
                .toList();
    }
}
