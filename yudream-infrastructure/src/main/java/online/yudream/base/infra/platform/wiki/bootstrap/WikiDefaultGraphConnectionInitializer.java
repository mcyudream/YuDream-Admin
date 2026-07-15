package online.yudream.base.infra.platform.wiki.bootstrap;

import lombok.RequiredArgsConstructor;
import online.yudream.base.domain.platform.graph.aggregate.GraphConnection;
import online.yudream.base.domain.platform.graph.repo.GraphConnectionRepo;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "yudream.platform.capabilities.neo4j", name = "enabled", havingValue = "true")
public class WikiDefaultGraphConnectionInitializer {
    private static final String CODE = "wiki-default";

    private final GraphConnectionRepo connections;

    @Value("${yudream.platform.wiki.neo4j.uri:bolt://localhost:7687}")
    private String uri;
    @Value("${yudream.platform.wiki.neo4j.username:neo4j}")
    private String username;
    @Value("${yudream.platform.wiki.neo4j.password:}")
    private String password;
    @Value("${yudream.platform.wiki.neo4j.database:neo4j}")
    private String database;

    @EventListener(ApplicationReadyEvent.class)
    public void initialize() {
        if (connections.findByCode(CODE).isEmpty() && password != null && !password.isBlank()) {
            connections.save(GraphConnection.create("Wiki 默认 Neo4j", CODE, uri, username, password, database));
        }
    }
}
