package online.yudream.base.infra.system.backup.service;

import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.system.backup.service.BackupArchiveReader;
import online.yudream.base.domain.system.backup.valobj.ArchiveFileEntry;
import online.yudream.base.domain.system.backup.valobj.BackupManifest;
import online.yudream.base.domain.system.backup.valobj.SnapshotDocument;
import online.yudream.base.domain.system.backup.valobj.SnapshotDocument;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.bson.Document;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * YDBA 归档读取器：基于 ZipFile 随机读。打开即校验清单格式与协议版本，
 * 对象与插件文件索引一次性载入，条目内容按需流式读取。
 */
public class ZipBackupArchiveReader implements BackupArchiveReader {

    private static final String OBJECTS_INDEX = "objects.index.json";
    private static final String PLUGINS_INDEX = "plugins.index.json";

    private final ZipFile zipFile;
    private final ObjectMapper mapper = new ObjectMapper();
    private final BackupManifest manifest;
    private final List<ArchiveFileEntry> objectEntries = new ArrayList<>();
    private final Map<String, List<ArchiveFileEntry>> pluginFilesByScope = new LinkedHashMap<>();

    public ZipBackupArchiveReader(Path archiveFile) {
        ZipFile opened = null;
        try {
            opened = new ZipFile(archiveFile.toFile(), StandardCharsets.UTF_8);
            ZipEntry manifestEntry = opened.getEntry("manifest.json");
            if (manifestEntry == null) {
                throw new BizException("不是有效的系统备份归档（缺少 manifest.json）");
            }
            BackupManifest parsedManifest =
                    parseManifest(mapper.readTree(read(opened.getInputStream(manifestEntry))));
            parsedManifest.ensureSupported();
            ZipEntry objectIndex = opened.getEntry(OBJECTS_INDEX);
            if (objectIndex != null) {
                parseObjectIndex(mapper.readTree(read(opened.getInputStream(objectIndex))));
            }
            ZipEntry pluginIndex = opened.getEntry(PLUGINS_INDEX);
            if (pluginIndex != null) {
                parsePluginIndex(mapper.readTree(read(opened.getInputStream(pluginIndex))));
            }
            this.manifest = parsedManifest;
            this.zipFile = opened;
        } catch (IOException e) {
            closeQuietly(opened);
            throw new BizException("无法读取备份归档：" + e.getMessage());
        } catch (RuntimeException e) {
            closeQuietly(opened);
            throw e;
        }
    }

    private static void closeQuietly(ZipFile file) {
        if (file != null) {
            try {
                file.close();
            } catch (IOException ignored) {
                // 关闭失败不掩盖原始异常
            }
        }
    }

    @Override
    public BackupManifest manifest() {
        return manifest;
    }

    @Override
    public List<String> collections() {
        return manifest.collections().stream().map(BackupManifest.ManifestCollection::name).toList();
    }

    @Override
    public void streamCollection(String name, Consumer<SnapshotDocument> documentConsumer) {
        ArchivePaths.validateCollection(name);
        ZipEntry entry = zipFile.getEntry("mongo/" + name + ".ndjson");
        if (entry == null) {
            throw new BizException("归档中不存在集合：" + name);
        }
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(zipFile.getInputStream(entry), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                documentConsumer.accept(new SnapshotDocument(parseDocumentId(line), line));
            }
        } catch (IOException e) {
            throw new BizException("读取归档集合失败：" + name);
        }
    }

    /** 解析 EJSON 行的 _id 为规范化字符串，解析失败抛出业务异常。 */
    private static String parseDocumentId(String ejson) {
        try {
            return MongoSystemDataSnapshotter.normalizeId(Document.parse(ejson).get("_id"));
        } catch (Exception e) {
            throw new BizException("备份数据格式异常，无法解析文档 _id");
        }
    }

    @Override
    public List<ArchiveFileEntry> objects() {
        return List.copyOf(objectEntries);
    }

    @Override
    public InputStream openObject(String objectKey) {
        ZipEntry entry = zipFile.getEntry("objects/" + ArchivePaths.encodeObjectKey(objectKey));
        if (entry == null) {
            throw new BizException("归档中不存在对象：" + objectKey);
        }
        try {
            return zipFile.getInputStream(entry);
        } catch (IOException e) {
            throw new BizException("读取归档对象失败：" + objectKey);
        }
    }

    @Override
    public List<BackupManifest.ManifestPluginScope> pluginScopes() {
        return manifest.pluginScopes();
    }

    @Override
    public List<ArchiveFileEntry> pluginFiles(String pluginCode, String scopeCode) {
        return pluginFilesByScope.getOrDefault(pluginCode + "/" + scopeCode, List.of());
    }

    @Override
    public InputStream openPluginFile(String pluginCode, String scopeCode, String path) {
        ArchivePaths.validateRelative(path);
        ZipEntry entry = zipFile.getEntry("plugins/" + pluginCode + "/" + scopeCode + "/" + path);
        if (entry == null) {
            throw new BizException("归档中不存在插件文件：" + pluginCode + "/" + scopeCode + "/" + path);
        }
        try {
            return zipFile.getInputStream(entry);
        } catch (IOException e) {
            throw new BizException("读取归档插件文件失败：" + path);
        }
    }

    @Override
    public void close() throws IOException {
        zipFile.close();
    }

    private byte[] read(InputStream in) throws IOException {
        try (InputStream stream = in) {
            return stream.readAllBytes();
        }
    }

    private BackupManifest parseManifest(JsonNode root) {
        List<BackupManifest.ManifestScope> scopes = new ArrayList<>();
        root.withArray("scopes").forEach(node -> scopes.add(new BackupManifest.ManifestScope(
                textOrNull(node.get("tag")), textOrNull(node.get("displayName")))));
        List<BackupManifest.ManifestCollection> collections = new ArrayList<>();
        root.withArray("collections").forEach(node -> collections.add(new BackupManifest.ManifestCollection(
                textOrNull(node.get("name")), node.path("count").asLong(0), textOrNull(node.get("sha256")))));
        List<BackupManifest.ManifestPluginScope> pluginScopes = new ArrayList<>();
        root.withArray("pluginScopes").forEach(node -> pluginScopes.add(new BackupManifest.ManifestPluginScope(
                textOrNull(node.get("pluginCode")), textOrNull(node.get("scopeCode")),
                node.path("fileCount").asInt(0), node.path("bytes").asLong(0))));
        List<String> excludedCollections = new ArrayList<>();
        root.withArray("excludedCollections").forEach(node -> {
            if (node.isTextual()) {
                excludedCollections.add(node.asText());
            }
        });
        return new BackupManifest(
                textOrNull(root.get("format")),
                root.path("schemaVersion").asInt(0),
                textOrNull(root.get("createdAt")),
                textOrNull(root.get("hostVersion")),
                textOrNull(root.get("masterKeyFingerprint")),
                List.copyOf(scopes),
                List.copyOf(collections),
                root.path("objectCount").asLong(0),
                root.path("objectBytes").asLong(0),
                List.copyOf(pluginScopes),
                List.copyOf(excludedCollections));
    }

    private void parseObjectIndex(JsonNode root) {
        if (root.isArray()) {
            for (JsonNode node : root) {
                objectEntries.add(new ArchiveFileEntry(
                        textOrNull(node.get("key")), node.path("size").asLong(0), textOrNull(node.get("sha256"))));
            }
        }
    }

    private void parsePluginIndex(JsonNode root) {
        if (!root.isArray()) {
            return;
        }
        for (JsonNode node : root) {
            String pluginCode = textOrNull(node.get("pluginCode"));
            String scopeCode = textOrNull(node.get("scopeCode"));
            String path = textOrNull(node.get("path"));
            ArchiveFileEntry entry = new ArchiveFileEntry(path, node.path("size").asLong(0),
                    textOrNull(node.get("sha256")));
            pluginFilesByScope.computeIfAbsent(pluginCode + "/" + scopeCode, ignored -> new ArrayList<>()).add(entry);
        }
    }

    private static String textOrNull(JsonNode node) {
        return node == null || node.isNull() ? null : node.asText();
    }

    /** 工厂：从归档文件打开读取器。 */
    @Component
    public static class Factory implements BackupArchiveReader.Factory {
        @Override
        public BackupArchiveReader open(Path archiveFile) {
            return new ZipBackupArchiveReader(archiveFile);
        }
    }
}
