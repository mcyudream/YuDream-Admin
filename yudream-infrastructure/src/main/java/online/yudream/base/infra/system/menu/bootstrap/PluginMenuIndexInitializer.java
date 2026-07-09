package online.yudream.base.infra.system.menu.bootstrap;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import online.yudream.base.domain.system.menu.enumerate.MenuSource;
import online.yudream.base.infra.system.menu.dataobj.MenuDO;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.index.PartialIndexFilter;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class PluginMenuIndexInitializer implements ApplicationListener<ApplicationReadyEvent> {

    private static final String INDEX_NAME = "uk_plugin_menu_registration";

    private final MongoTemplate mongoTemplate;

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        Index index = new Index()
                .on("source", Sort.Direction.ASC)
                .on("pluginCode", Sort.Direction.ASC)
                .on("pluginRegistrationKey", Sort.Direction.ASC)
                .named(INDEX_NAME)
                .unique()
                .partial(PartialIndexFilter.of(Criteria.where("source").is(MenuSource.PLUGIN)));
        String ensuredIndex = mongoTemplate.indexOps(MenuDO.class).ensureIndex(index);
        log.info("Ensured plugin menu registration index: {}", ensuredIndex);
    }
}
