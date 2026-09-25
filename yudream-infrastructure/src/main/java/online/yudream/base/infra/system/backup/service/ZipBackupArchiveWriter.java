package online.yudream.base.infra.system.backup.service;

import online.yudream.base.domain.system.backup.service.BackupArchiveWriter;
import online.yudream.base.domain.system.backup.valobj.ArchiveFileEntry;
import online.yudream.base.domain.system.backup.valobj.BackupManifest;
import online.yudream.base.domain.system.backup.valobj.BackupManifestHeader;
import online.yudream.base.domain.system.backup.valobj.BackupScopeRef;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * YDBA 归档写入器（ZIP 顺序写）。逐集合统计文档数与 SHA-256，
 * finish 时落盘 objects.index.json、plugins.index.json 与 manifest.json。
 */
public class ZipBackupArchiveWriter implements BackupArchiveWriter {

    private final ZipOutputStream zip;
    private final ObjectMapper mapper = new ObjectMapper();
    private final List<BackupManifest.ManifestCollection> collections = new ArrayList<>();
    private final List<ArchiveFileEntry> objectEntries = new ArrayList<>();
    private final List<PluginIndexEntry> pluginEntries = new ArrayList<>();
    private final List<BackupManifest.ManifestPluginScope> pluginScopes = new ArrayList<>();
    private String currentCollection;
    private long currentCollectionCount;
    private MessageDigest currentCollectionDigest;
    private BackupScopeRef currentScope;
    private int currentScopeFileCount;
    private long currentScopeBytes;
    private long objectCount;
    private long objectBytes;
    private boolean finished;

    public ZipBackupArchiveWriter(OutputStream out) {
        this.zip = new ZipOutputStream(out, StandardCharsets.UTF_8);
    }

    @Override
    public void openCollection(String collection) throws IOException {
        closeCollection();
        ArchivePaths.validateCollection(collection);
        currentCollection = collection;
        currentCollectionCount = 0;
        currentCollectionDigest = sha256();
        zip.putNextEntry(new ZipEntry("mongo/" + collection + ".ndjson"));
    }

    @Override
    public void writeCollectionDocument(String ejson) throws IOException {
        if (currentCollection == null) {
            throw new IllegalStateException("请先 openCollection 再写入文档");
        }
        if (ejson.indexOf('\n') >= 0 || ejson.indexOf('\r') >= 0) {
            throw new IllegalArgumentException("EJSON 文档不允许包含换行");
        }
        byte[] bytes = (ejson + "\n").getBytes(StandardCharsets.UTF_8);
        zip.write(bytes);
        currentCollectionDigest.update(bytes);
        currentCollectionCount++;
    }

    @Override
    public void closeCollection() throws IOException {
        if (currentCollection == null) {
            return;
        }
        zip.closeEntry();
        collections.add(new BackupManifest.ManifestCollection(
                currentCollection, currentCollectionCount, HexFormat.of().formatHex(currentCollectionDigest.digest())));
        currentCollection = null;
        currentCollectionDigest = null;
    }

    @Override
    public void writeObject(String objectKey, InputStream in, long size) throws IOException {
        ArchivePaths.validateObjectKey(objectKey);
        MessageDigest digest = sha256();
        zip.putNextEntry(new ZipEntry("objects/" + ArchivePaths.encodeObjectKey(objectKey)));
        long actual = copy(in, digest);
        zip.closeEntry();
        objectEntries.add(new ArchiveFileEntry(objectKey, actual, HexFormat.of().formatHex(digest.digest())));
        objectCount++;
        objectBytes += actual;
    }

    @Override
    public void openPluginScope(BackupScopeRef scope) throws IOException {
        closePluginScope();
        currentScope = scope;
        currentScopeFileCount = 0;
        currentScopeBytes = 0;
    }

    @Override
    public void writePluginFile(String relativePath, InputStream in, long size) throws IOException {
        if (currentScope == null) {
            throw new IllegalStateException("请先 openPluginScope 再写入插件文件");
        }
        ArchivePaths.validateRelative(relativePath);
        String zipPath = "plugins/" + currentScope.pluginCode() + "/" + currentScope.scopeCode() + "/" + relativePath;
        MessageDigest digest = sha256();
        zip.putNextEntry(new ZipEntry(zipPath));
        long actual = copy(in, digest);
        zip.closeEntry();
        pluginEntries.add(new PluginIndexEntry(currentScope.pluginCode(), currentScope.scopeCode(),
                relativePath, actual, HexFormat.of().formatHex(digest.digest())));
        currentScopeFileCount++;
        currentScopeBytes += actual;
    }

    @Override
    public void closePluginScope() throws IOException {
        if (currentScope == null) {
            return;
        }
        pluginScopes.add(new BackupManifest.ManifestPluginScope(
                currentScope.pluginCode(), currentScope.scopeCode(), currentScopeFileCount, currentScopeBytes));
        currentScope = null;
    }

    @Override
    public BackupManifest finish(BackupManifestHeader header) throws IOException {
        closeCollection();
        closePluginScope();
        writeJson("objects.index.json", objectEntries.stream()
                .map(entry -> entryJson("key", entry.path(), entry.size(), entry.sha256()))
                .toList());
        writeJson("plugins.index.json", pluginEntries.stream()
                .map(entry -> {
                    Map<String, Object> json = entryJson("path", entry.path(), entry.size(), entry.sha256());
                    json.put("pluginCode", entry.pluginCode());
                    json.put("scopeCode", entry.scopeCode());
                    return json;
                })
                .toList());
        Map<String, Object> manifestJson = new LinkedHashMap<>();
        manifestJson.put("format", BackupManifest.FORMAT);
        manifestJson.put("schemaVersion", BackupManifest.SCHEMA_VERSION);
        manifestJson.put("createdAt", Instant.now().toString());
        manifestJson.put("hostVersion", header.hostVersion());
        manifestJson.put("masterKeyFingerprint", header.masterKeyFingerprint());
        manifestJson.put("scopes", header.scopes().stream()
                .map(scope -> Map.of("tag", scope.tag(), "displayName", scope.displayName()))
                .toList());
        manifestJson.put("collections", collections.stream()
                .map(collection -> Map.of("name", collection.name(), "count", collection.count(),
                        "sha256", collection.sha256() == null ? "" : collection.sha256()))
                .toList());
        manifestJson.put("objectCount", objectCount);
        manifestJson.put("objectBytes", objectBytes);
        manifestJson.put("pluginScopes", pluginScopes);
        writeJson("manifest.json", manifestJson);
        finished = true;
        zip.finish();
        zip.close();
        return new BackupManifest(BackupManifest.FORMAT, BackupManifest.SCHEMA_VERSION,
                (String) manifestJson.get("createdAt"), header.hostVersion(), header.masterKeyFingerprint(),
                manifestScopes(header),
                List.copyOf(collections), objectCount, objectBytes, List.copyOf(pluginScopes));
    }

    private List<BackupManifest.ManifestScope> manifestScopes(BackupManifestHeader header) {
        return header.scopes().stream()
                .map(scope -> new BackupManifest.ManifestScope(scope.tag(), scope.displayName()))
                .toList();
    }

    @Override
    public void close() throws IOException {
        if (!finished) {
            zip.close();
        }
    }

    private void writeJson(String name, Object value) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(mapper.writeValueAsBytes(value));
        zip.closeEntry();
    }

    private Map<String, Object> entryJson(String keyField, String key, long size, String sha256) {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put(keyField, key);
        json.put("size", size);
        json.put("sha256", sha256);
        return json;
    }

    private long copy(InputStream in, MessageDigest digest) throws IOException {
        byte[] buffer = new byte[64 * 1024];
        long total = 0;
        int read;
        while ((read = in.read(buffer)) >= 0) {
            zip.write(buffer, 0, read);
            digest.update(buffer, 0, read);
            total += read;
        }
        return total;
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new UncheckedIOException(new IOException("SHA-256 不可用", e));
        }
    }

    private record PluginIndexEntry(String pluginCode, String scopeCode, String path, long size, String sha256) {
    }

    /** 工厂：归档写入器按输出流构建。 */
    @Component
    public static class Factory implements BackupArchiveWriter.Factory {
        @Override
        public BackupArchiveWriter create(OutputStream out) {
            return new ZipBackupArchiveWriter(out);
        }
    }
}
