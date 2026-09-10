package online.yudream.base.infra.platform.cms.bootstrap;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import online.yudream.base.domain.shared.IdGenerator;
import org.bson.Document;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 一次性幂等迁移：CMS 首页布局/页面/方案从「全局唯一」迁移为「按主题（themeCode）隔离」。
 * 直接在 Document 层操作，避免旧枚举值（如已废弃的 THEME_BACKUP）导致领域映射失败。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CmsThemeBindingInitializer implements ApplicationRunner {

    private static final String DEFAULT_THEME = "default";
    private static final String PLUGIN_PREFIX = "plugin:";
    private static final String BACKUP_PREFIX = "plugin-backup:";

    private final MongoTemplate mongo;
    private final IdGenerator idGenerator;

    @Override
    public void run(ApplicationArguments args) {
        convertThemeBackups();
        bindLayouts();
        bindPresets();
        bindPages();
    }

    /** 主题接管前备份（plugin-backup:*）落为归属主题的布局，随后删除备份。 */
    private void convertThemeBackups() {
        List<Document> backups = mongo.getCollection("platformCmsHomePreset")
                .find(new Document("code", new Document("$regex", "^" + BACKUP_PREFIX)))
                .sort(new Document("updateTime", -1))
                .into(new ArrayList<>());
        Set<String> themesWithLayout = new HashSet<>();
        for (Document layout : mongo.getCollection("platformHomePageLayout").find()) {
            String themeCode = layout.getString("themeCode");
            if (themeCode != null) {
                themesWithLayout.add(themeCode);
            }
        }
        for (Document backup : backups) {
            String themeCode = themeCodeOf(backup.getString("theme"));
            if (themesWithLayout.add(themeCode)) {
                Document layout = new Document()
                        .append("_id", idGenerator.nextId())
                        .append("themeCode", themeCode)
                        .append("title", backup.getString("title"))
                        .append("subtitle", backup.getString("subtitle"))
                        .append("theme", backup.getString("theme"))
                        .append("heroImageUrl", backup.getString("heroImageUrl"))
                        .append("settings", backup.get("settings", new Document()))
                        .append("sections", backup.get("sections", new ArrayList<>()))
                        .append("published", true)
                        .append("version", 0L)
                        .append("createTime", LocalDateTime.now())
                        .append("updateTime", LocalDateTime.now());
                mongo.getCollection("platformHomePageLayout").insertOne(layout);
                log.info("CMS 主题迁移：接管前备份已落为「{}」主题的首页布局", themeCode);
            }
            mongo.getCollection("platformCmsHomePreset").deleteOne(new Document("_id", backup.get("_id")));
        }
    }

    /** 布局补齐 themeCode（按旧 theme 字段推导），并按主题去重保留最新。 */
    private void bindLayouts() {
        var collection = mongo.getCollection("platformHomePageLayout");
        for (Document layout : collection.find(new Document("themeCode", new Document("$exists", false)))) {
            collection.updateOne(new Document("_id", layout.get("_id")),
                    new Document("$set", new Document("themeCode", themeCodeOf(layout.getString("theme")))));
        }
        Map<String, List<Document>> byTheme = new HashMap<>();
        for (Document layout : collection.find()) {
            byTheme.computeIfAbsent(layout.getString("themeCode"), key -> new ArrayList<>()).add(layout);
        }
        byTheme.forEach((themeCode, layouts) -> {
            if (layouts.size() > 1) {
                // 新旧判定走单调递增的雪花 _id（raw Document 里 updateTime 是 Date，类型不稳）
                layouts.stream()
                        .sorted(Comparator.comparing((Document doc) -> doc.get("_id", Number.class),
                                        Comparator.nullsFirst(Comparator.comparingLong(Number::longValue)))
                                .reversed())
                        .skip(1)
                        .forEach(stale -> {
                            collection.deleteOne(new Document("_id", stale.get("_id")));
                            log.info("CMS 主题迁移：删除「{}」主题的冗余首页布局 {}", themeCode, stale.get("_id"));
                        });
            }
        });
    }

    /** 方案补齐 themeCode：plugin:{code} 归该插件主题，其余归默认主题。 */
    private void bindPresets() {
        var collection = mongo.getCollection("platformCmsHomePreset");
        for (Document preset : collection.find(new Document("themeCode", new Document("$exists", false)))) {
            collection.updateOne(new Document("_id", preset.get("_id")),
                    new Document("$set", new Document("themeCode", themeCodeOf(preset.getString("code")))));
        }
    }

    /** 页面补齐 themeCode：插件托管页归来源插件主题，其余归默认主题。 */
    private void bindPages() {
        var collection = mongo.getCollection("platformCmsPage");
        for (Document page : collection.find(new Document("themeCode", new Document("$exists", false)))) {
            String sourcePlugin = page.getString("sourcePluginCode");
            String themeCode = sourcePlugin == null || sourcePlugin.isBlank() ? DEFAULT_THEME : sourcePlugin;
            collection.updateOne(new Document("_id", page.get("_id")),
                    new Document("$set", new Document("themeCode", themeCode)));
        }
    }

    private String themeCodeOf(String legacyThemeOrCode) {
        return legacyThemeOrCode != null && legacyThemeOrCode.startsWith(PLUGIN_PREFIX)
                ? legacyThemeOrCode.substring(PLUGIN_PREFIX.length())
                : DEFAULT_THEME;
    }
}
