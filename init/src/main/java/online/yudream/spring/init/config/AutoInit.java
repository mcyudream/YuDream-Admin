package online.yudream.spring.init.config;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import online.yudream.spring.init.service.InitService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.type.filter.AssignableTypeFilter;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Slf4j
@Configuration
public class AutoInit {

    private static final List<String> INIT_BASE_PACKAGES = List.of(
            "online.yudream.spring.init.service.impl"
    );

    @Resource
    private ApplicationContext applicationContext;

    @Value("${init.mode:normal}")
    private String initMode;

    @Resource
    private MongoTemplate mongoTemplate;


    @PostConstruct
    public void initDB() throws ClassNotFoundException {
        log.info("初始化数据库中...");

        switch (initMode) {
            case "normal":
                for (Class<? extends InitService> initClass: getInitClasses()){
                    if (applicationContext.getBean(initClass).isFirstInit()){
                        applicationContext.getBean(initClass).init();
                    }
                    log.debug("初始化{}", initClass.getName());
                }
                break;
            case "delete":
                dropAllCollections();
            default:
                for (Class<? extends InitService> initClass: getInitClasses()){
                    applicationContext.getBean(initClass).init();
                    log.debug("初始化{}", initClass.getName());
                }
        }
    }


    private void dropAllCollections() {
        Set<String> collectionNames = mongoTemplate.getCollectionNames();
        for (String collectionName : collectionNames) {
            mongoTemplate.dropCollection(collectionName);
            log.info("已清空集合: {}", collectionName);
        }
    }

    public List<Class<? extends InitService>> getInitClasses() throws ClassNotFoundException {
        List<Class<? extends InitService>> classes = new ArrayList<>();
        ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AssignableTypeFilter(InitService.class));
        for (String base : INIT_BASE_PACKAGES) {
            for (BeanDefinition bd : scanner.findCandidateComponents(base)) {
                @SuppressWarnings("unchecked")
                Class<? extends InitService> raw = (Class<? extends InitService>) Class.forName(bd.getBeanClassName());
                classes.add(raw);
            }
        }
        return classes;
    }
}
