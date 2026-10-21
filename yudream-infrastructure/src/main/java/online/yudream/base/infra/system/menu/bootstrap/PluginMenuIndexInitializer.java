package online.yudream.base.infra.system.menu.bootstrap;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import online.yudream.base.domain.system.menu.enumerate.MenuSource;
import online.yudream.base.domain.system.menu.service.MenuDomainService;
import online.yudream.base.infra.system.menu.dataobj.MenuDO;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.index.IndexOperations;
import org.springframework.data.mongodb.core.index.PartialIndexFilter;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.stereotype.Component;

/**
 * 菜单唯一索引初始化器。
 * <p>
 * 顺序约束（不能颠倒）：先按 {@link MenuDomainService#dedupeByCode()} 治理历史
 * 同 code 冗余菜单，再确保唯一 {@code uk_menu_code}，否则脏数据会令唯一索引
 * 创建失败；随后照旧确保插件注册的部分唯一索引。本初始化器以
 * HIGHEST_PRECEDENCE + 10 先于 SystemMenuInitializer 等启动监听运行。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class PluginMenuIndexInitializer implements ApplicationListener<ApplicationReadyEvent> {

    /** 插件注册标识唯一索引（仅对 source=PLUGIN 生效）。 */
    private static final String PLUGIN_INDEX_NAME = "uk_plugin_menu_registration";

    /** 菜单 code 全局唯一索引。 */
    private static final String CODE_INDEX_NAME = "uk_menu_code";

    private final MongoTemplate mongoTemplate;
    private final MenuDomainService menuDomainService;

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        int healed = menuDomainService.dedupeByCode();
        if (healed > 0) {
            log.info("Healed {} duplicate menu codes before ensuring unique code index", healed);
        }
        IndexOperations indexOperations = mongoTemplate.indexOps(MenuDO.class);
        Index codeIndex = new Index()
                .on("code", Sort.Direction.ASC)
                .named(CODE_INDEX_NAME)
                .unique();
        String ensuredCodeIndex = indexOperations.ensureIndex(codeIndex);
        log.info("Ensured unique menu code index: {}", ensuredCodeIndex);
        Index pluginIndex = new Index()
                .on("source", Sort.Direction.ASC)
                .on("pluginCode", Sort.Direction.ASC)
                .on("pluginRegistrationKey", Sort.Direction.ASC)
                .named(PLUGIN_INDEX_NAME)
                .unique()
                .partial(PartialIndexFilter.of(Criteria.where("source").is(MenuSource.PLUGIN)));
        String ensuredPluginIndex = indexOperations.ensureIndex(pluginIndex);
        log.info("Ensured plugin menu registration index: {}", ensuredPluginIndex);
    }
}
