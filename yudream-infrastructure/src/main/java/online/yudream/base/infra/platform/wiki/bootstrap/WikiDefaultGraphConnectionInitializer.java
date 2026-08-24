package online.yudream.base.infra.platform.wiki.bootstrap;

import org.bson.Document;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Component;
import java.time.LocalDateTime;

/** One-time, idempotent migration from connection records to secret-free logical graph tables. */
@Component
public class WikiDefaultGraphConnectionInitializer implements ApplicationRunner {
    private static final String MARKER = "logicalGraphTableMigratedAt";
    private final MongoTemplate mongo;
    public WikiDefaultGraphConnectionInitializer(MongoTemplate mongo) { this.mongo = mongo; }
    @Override public void run(ApplicationArguments args) {
        for (Document legacy : mongo.getCollection("platformGraphConnection").find()) {
            Object id = legacy.get("_id");
            String code = legacy.getString("code");
            if (code != null && !code.isBlank() && mongo.getCollection("platformGraphTable").countDocuments(new Document("code", code)) == 0) {
                Document table = new Document("_id", id).append("name", legacy.getString("name")).append("code", code)
                        .append("description", "从历史图数据库连接迁移").append("status", legacy.get("status", "ACTIVE"))
                        .append("authorizedPluginCodes", legacy.get("authorizedPluginCodes", java.util.List.of()))
                        .append("version", legacy.get("version", 0L)).append("createTime", legacy.get("createTime", LocalDateTime.now()))
                        .append("updateTime", LocalDateTime.now()).append(MARKER, LocalDateTime.now());
                mongo.getCollection("platformGraphTable").insertOne(table);
            }
            // Keep an auditable legacy shell while removing credentials permanently.
            mongo.getCollection("platformGraphConnection").updateOne(new Document("_id", id), new Document("$unset", new Document("uri", "").append("username", "").append("password", "").append("database", "")).append("$set", new Document(MARKER, LocalDateTime.now())));
        }
        mongo.getCollection("platformWikiSpace").updateMany(new Document("graphTableCode", new Document("$exists", false)).append("neo4jConnectionCode", new Document("$exists", true)), new Document("$rename", new Document("neo4jConnectionCode", "graphTableCode")));
        mongo.getCollection("platformGraphQueryLog").updateMany(new Document("tableId", new Document("$exists", false)).append("connectionId", new Document("$exists", true)), new Document("$rename", new Document("connectionId", "tableId").append("connectionCode", "tableCode")));
    }
}
