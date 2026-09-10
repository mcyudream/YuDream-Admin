package online.yudream.base.infra.platform.cms.bootstrap;

import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.result.DeleteResult;
import com.mongodb.client.result.UpdateResult;
import online.yudream.base.domain.shared.IdGenerator;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CmsThemeBindingInitializerTest {

    private FakeMongo mongo;
    private AtomicLong ids;
    private CmsThemeBindingInitializer initializer;

    @BeforeEach
    void setUp() {
        mongo = new FakeMongo();
        ids = new AtomicLong(1000);
        IdGenerator idGenerator = ids::incrementAndGet;
        initializer = new CmsThemeBindingInitializer(mongo.template, idGenerator);
    }

    @Test
    void convertsBackupsIntoThemeLayoutsAndRemovesThem() {
        mongo.layouts.add(new Document()
                .append("_id", 1L)
                .append("themeCode", "neco-pixel")
                .append("theme", "plugin:neco-pixel"));
        mongo.presets.add(backup(10L, "plugin-backup:neco-pixel", "plugin:neco-pixel", "Neco 接管前首页"));
        mongo.presets.add(backup(11L, "plugin-backup:default", "default", "默认旧首页"));

        initializer.run(null);

        assertThat(mongo.presets).isEmpty();
        assertThat(mongo.layouts).hasSize(2);
        Document restored = mongo.layouts.stream()
                .filter(doc -> "default".equals(doc.getString("themeCode")))
                .findFirst().orElseThrow();
        assertThat(restored.get("_id")).isEqualTo(1001L);
        assertThat(restored.getString("title")).isEqualTo("默认旧首页");
        assertThat(restored.getString("subtitle")).isEqualTo("副标题-plugin-backup:default");
        assertThat(restored.getString("theme")).isEqualTo("default");
        assertThat(restored.getString("heroImageUrl")).isEqualTo("hero.png");
        assertThat(restored.get("settings", Document.class)).isEqualTo(new Document("accent", "#fff"));
        assertThat(restored.get("sections", List.class)).hasSize(1);
        assertThat(restored.getBoolean("published")).isTrue();
        assertThat(restored.get("version")).isEqualTo(0L);
        // neco-pixel 已有布局：其备份只删除、不重复落布局
        assertThat(mongo.layouts.stream()
                .filter(doc -> "neco-pixel".equals(doc.getString("themeCode")))).hasSize(1);
    }

    @Test
    void backfillsLayoutThemeCodeAndKeepsNewestPerTheme() {
        mongo.layouts.add(new Document().append("_id", 100L).append("theme", "plugin:neco-pixel").append("title", "旧"));
        mongo.layouts.add(new Document().append("_id", 200L).append("theme", "plugin:neco-pixel").append("title", "新"));
        mongo.layouts.add(new Document().append("_id", 300L).append("theme", "default").append("title", "默认"));

        initializer.run(null);

        assertThat(mongo.layouts).extracting(doc -> doc.get("_id")).containsExactlyInAnyOrder(200L, 300L);
        Document newest = layoutById(200L);
        assertThat(newest.getString("themeCode")).isEqualTo("neco-pixel");
        assertThat(newest.getString("title")).isEqualTo("新");
        assertThat(layoutById(300L).getString("themeCode")).isEqualTo("default");
    }

    @Test
    void backfillsPresetThemeCodeFromPresetCode() {
        mongo.presets.add(new Document().append("_id", 1L).append("code", "plugin:neco-pixel"));
        mongo.presets.add(new Document().append("_id", 2L).append("code", "user-1"));
        mongo.presets.add(new Document().append("_id", 3L).append("code", "snapshot-x"));

        initializer.run(null);

        assertThat(presetById(1L).getString("themeCode")).isEqualTo("neco-pixel");
        assertThat(presetById(2L).getString("themeCode")).isEqualTo("default");
        assertThat(presetById(3L).getString("themeCode")).isEqualTo("default");
    }

    @Test
    void backfillsPageThemeCodeFromSourcePlugin() {
        mongo.pages.add(new Document().append("_id", 1L).append("sourcePluginCode", "neco-pixel"));
        mongo.pages.add(new Document().append("_id", 2L));
        mongo.pages.add(new Document().append("_id", 3L)
                .append("themeCode", "pixel-flat").append("sourcePluginCode", "neco-pixel"));

        initializer.run(null);

        assertThat(pageById(1L).getString("themeCode")).isEqualTo("neco-pixel");
        assertThat(pageById(2L).getString("themeCode")).isEqualTo("default");
        // 已绑定 themeCode 的页面不动
        assertThat(pageById(3L).getString("themeCode")).isEqualTo("pixel-flat");
    }

    @Test
    void noopsWhenEverythingAlreadyBound() {
        mongo.layouts.add(new Document().append("_id", 1L).append("themeCode", "default"));
        mongo.presets.add(new Document().append("_id", 2L).append("code", "user-1").append("themeCode", "default"));
        mongo.pages.add(new Document().append("_id", 3L).append("themeCode", "default"));

        initializer.run(null);

        verify(mongo.layoutCollection, never()).insertOne(any(Document.class));
        verify(mongo.layoutCollection, never()).updateOne(any(Bson.class), any(Bson.class));
        verify(mongo.layoutCollection, never()).deleteOne(any(Bson.class));
        verify(mongo.presetCollection, never()).insertOne(any(Document.class));
        verify(mongo.presetCollection, never()).updateOne(any(Bson.class), any(Bson.class));
        verify(mongo.presetCollection, never()).deleteOne(any(Bson.class));
        verify(mongo.pageCollection, never()).updateOne(any(Bson.class), any(Bson.class));
        verify(mongo.pageCollection, never()).deleteOne(any(Bson.class));
    }

    private Document backup(long id, String code, String theme, String title) {
        return new Document()
                .append("_id", id)
                .append("code", code)
                .append("theme", theme)
                .append("title", title)
                .append("subtitle", "副标题-" + code)
                .append("heroImageUrl", "hero.png")
                .append("settings", new Document("accent", "#fff"))
                .append("sections", new ArrayList<>(List.of(new Document("type", "hero"))));
    }

    private Document layoutById(long id) {
        return mongo.layouts.stream().filter(doc -> Objects.equals(doc.get("_id"), id)).findFirst().orElseThrow();
    }

    private Document presetById(long id) {
        return mongo.presets.stream().filter(doc -> Objects.equals(doc.get("_id"), id)).findFirst().orElseThrow();
    }

    private Document pageById(long id) {
        return mongo.pages.stream().filter(doc -> Objects.equals(doc.get("_id"), id)).findFirst().orElseThrow();
    }

    /** 只模拟本迁移用到的 Mongo 操作面：find（含 $regex 前缀与 $exists 过滤）、updateOne $set、insertOne、deleteOne。 */
    @SuppressWarnings("unchecked")
    private static final class FakeMongo {
        private final MongoTemplate template = mock(MongoTemplate.class);
        private final MongoCollection<Document> presetCollection = mock(MongoCollection.class);
        private final MongoCollection<Document> layoutCollection = mock(MongoCollection.class);
        private final MongoCollection<Document> pageCollection = mock(MongoCollection.class);
        private final List<Document> presets = new ArrayList<>();
        private final List<Document> layouts = new ArrayList<>();
        private final List<Document> pages = new ArrayList<>();

        private FakeMongo() {
            when(template.getCollection("platformCmsHomePreset")).thenReturn(presetCollection);
            when(template.getCollection("platformHomePageLayout")).thenReturn(layoutCollection);
            when(template.getCollection("platformCmsPage")).thenReturn(pageCollection);
            bind(presetCollection, presets);
            bind(layoutCollection, layouts);
            bind(pageCollection, pages);
        }

        private void bind(MongoCollection<Document> collection, List<Document> docs) {
            when(collection.find()).thenAnswer(invocation -> iterable(docs));
            when(collection.find(any(Bson.class))).thenAnswer(invocation -> {
                Bson filter = invocation.getArgument(0);
                List<Document> matched = new ArrayList<>();
                for (Document doc : docs) {
                    if (matches(doc, filter)) {
                        matched.add(doc);
                    }
                }
                return iterable(matched);
            });
            when(collection.updateOne(any(Bson.class), any(Bson.class))).thenAnswer(invocation -> {
                Document filter = invocation.getArgument(0);
                Document update = invocation.getArgument(1);
                Document set = (Document) update.get("$set");
                docs.stream()
                        .filter(doc -> Objects.equals(doc.get("_id"), filter.get("_id")))
                        .findFirst()
                        .ifPresent(doc -> doc.putAll(set));
                return UpdateResult.acknowledged(1, 1L, null);
            });
            when(collection.deleteOne(any(Bson.class))).thenAnswer(invocation -> {
                Document filter = invocation.getArgument(0);
                docs.removeIf(doc -> Objects.equals(doc.get("_id"), filter.get("_id")));
                return DeleteResult.acknowledged(1);
            });
            doAnswer(invocation -> {
                docs.add(invocation.getArgument(0));
                return null;
            }).when(collection).insertOne(any(Document.class));
        }

        private FindIterable<Document> iterable(List<Document> docs) {
            FindIterable<Document> iterable = mock(FindIterable.class);
            when(iterable.sort(any(Bson.class))).thenReturn(iterable);
            when(iterable.into(any())).thenAnswer(invocation -> {
                Collection<Document> target = invocation.getArgument(0);
                target.addAll(docs);
                return target;
            });
            when(iterable.iterator()).thenAnswer(invocation -> {
                Iterator<Document> iterator = new ArrayList<>(docs).iterator();
                MongoCursor<Document> cursor = mock(MongoCursor.class);
                when(cursor.hasNext()).thenAnswer(ignored -> iterator.hasNext());
                when(cursor.next()).thenAnswer(ignored -> iterator.next());
                return cursor;
            });
            return iterable;
        }

        private boolean matches(Document doc, Bson filter) {
            if (!(filter instanceof Document condition)) {
                return true;
            }
            for (Map.Entry<String, Object> entry : condition.entrySet()) {
                Object expected = entry.getValue();
                if (expected instanceof Document nested && nested.containsKey("$regex")) {
                    // 迁移只用 ^prefix 形式的正则
                    String prefix = nested.getString("$regex").substring(1);
                    String value = doc.getString(entry.getKey());
                    if (value == null || !value.startsWith(prefix)) {
                        return false;
                    }
                } else if (expected instanceof Document nested && Boolean.FALSE.equals(nested.get("$exists"))) {
                    if (doc.containsKey(entry.getKey())) {
                        return false;
                    }
                } else if (!Objects.equals(doc.get(entry.getKey()), expected)) {
                    return false;
                }
            }
            return true;
        }
    }
}
