package online.yudream.base.infra.platform.satori.bootstrap;

import lombok.RequiredArgsConstructor;
import online.yudream.base.infra.platform.satori.dataobj.SatoriEventRecordDO;
import online.yudream.base.infra.platform.satori.dataobj.SatoriLoginDO;
import org.bson.Document;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.CompoundIndexDefinition;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@RequiredArgsConstructor
public class SatoriIndexInitializer implements ApplicationListener<ApplicationReadyEvent> {

    private final MongoTemplate mongoTemplate;

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        mongoTemplate.indexOps(SatoriLoginDO.class).ensureIndex(new CompoundIndexDefinition(new Document()
                .append("connectionId", 1)
                .append("platform", 1)
                .append("userId", 1))
                .named("satori_login_unique").unique());
        mongoTemplate.indexOps(SatoriEventRecordDO.class).ensureIndex(new CompoundIndexDefinition(new Document()
                .append("connectionId", 1)
                .append("sequence", 1))
                .named("satori_event_idempotency").unique());
        mongoTemplate.indexOps(SatoriEventRecordDO.class).ensureIndex(new Index()
                .on("expireAt", org.springframework.data.domain.Sort.Direction.ASC)
                .named("satori_event_expire_at")
                .expire(Duration.ZERO));
    }
}
