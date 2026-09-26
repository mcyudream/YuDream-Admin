package online.yudream.base.infra.system.backup.service;

import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.system.backup.service.BackupArchiveReader;
import online.yudream.base.domain.system.backup.service.BackupArchiveWriter;
import online.yudream.base.domain.system.backup.valobj.ArchiveFileEntry;
import online.yudream.base.domain.system.backup.valobj.BackupManifest;
import online.yudream.base.domain.system.backup.valobj.BackupManifestHeader;
import online.yudream.base.domain.system.backup.valobj.BackupScopeRef;
import online.yudream.base.domain.system.backup.valobj.SnapshotDocument;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ZipBackupArchiveRoundTripTest {

    @TempDir
    Path tempDir;

    @Test
    void writeAndReadRoundTrip() throws Exception {
        Path archive = tempDir.resolve("backup.zip");
        BackupScopeRef pluginScope = BackupScopeRef.plugin("mcpanel", "server-data", "MC 服务器数据");
        BackupManifest manifest;
        try (BackupArchiveWriter writer = new ZipBackupArchiveWriter(Files.newOutputStream(archive))) {
            writer.openCollection("sysSetting");
            writer.writeCollectionDocument("{\"_id\":\"a\",\"value\":\"中文值\",\"n\":{\"$numberLong\":\"123\"}}");
            writer.writeCollectionDocument("{\"_id\":\"b\",\"list\":[1,2,3]}");
            writer.closeCollection();
            writer.openCollection("platformCmsPage");
            writer.writeCollectionDocument("{\"_id\":\"c\",\"html\":\"<p>x</p>\"}");
            writer.closeCollection();
            byte[] objectBytes = "object-content-bytes".getBytes(StandardCharsets.UTF_8);
            writer.writeObject("module/2026/09/26/file name.png",
                    new ByteArrayInputStream(objectBytes), objectBytes.length);
            writer.openPluginScope(pluginScope);
            writer.writePluginFile("world/level.dat", new ByteArrayInputStream("level-data".getBytes(StandardCharsets.UTF_8)), 10);
            writer.writePluginFile("notes.txt", new ByteArrayInputStream("hello".getBytes(StandardCharsets.UTF_8)), 5);
            writer.closePluginScope();
            manifest = writer.finish(new BackupManifestHeader("1.0.0-test", "0123456789abcdef",
                    List.of(BackupScopeRef.system(), pluginScope), List.of("sysApiLog", "system.*")));
        }

        assertEquals(BackupManifest.FORMAT, manifest.format());
        assertEquals(List.of("sysApiLog", "system.*"), manifest.excludedCollections());
        assertEquals(2, manifest.collections().size());
        assertEquals(1, manifest.objectCount());
        assertEquals(20, manifest.objectBytes());
        assertEquals(1, manifest.pluginScopes().size());
        assertEquals(2, manifest.pluginScopes().get(0).fileCount());
        assertEquals(2, manifest.scopes().size());

        try (BackupArchiveReader reader = new ZipBackupArchiveReader(archive)) {
            assertEquals(manifest.format(), reader.manifest().format());
            assertEquals(List.of("sysSetting", "platformCmsPage"), reader.collections());

            List<SnapshotDocument> docs = new ArrayList<>();
            reader.streamCollection("sysSetting", docs::add);
            assertEquals(2, docs.size());
            assertEquals("a", docs.get(0).id());
            assertTrue(docs.get(0).ejson().contains("中文值"));

            List<ArchiveFileEntry> objects = reader.objects();
            assertEquals(1, objects.size());
            assertEquals("module/2026/09/26/file name.png", objects.get(0).path());
            try (InputStream in = reader.openObject("module/2026/09/26/file name.png")) {
                assertArrayEquals("object-content-bytes".getBytes(StandardCharsets.UTF_8), in.readAllBytes());
            }

            assertEquals(1, reader.pluginScopes().size());
            List<ArchiveFileEntry> pluginFiles = reader.pluginFiles("mcpanel", "server-data");
            assertEquals(2, pluginFiles.size());
            try (InputStream in = reader.openPluginFile("mcpanel", "server-data", "world/level.dat")) {
                assertArrayEquals("level-data".getBytes(StandardCharsets.UTF_8), in.readAllBytes());
            }
        }
    }

    @Test
    void rejectsForeignArchive() throws Exception {
        Path archive = tempDir.resolve("foreign.zip");
        try (java.util.zip.ZipOutputStream zip =
                     new java.util.zip.ZipOutputStream(Files.newOutputStream(archive))) {
            zip.putNextEntry(new java.util.zip.ZipEntry("whatever.txt"));
            zip.write("x".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        BizException exception = assertThrows(BizException.class, () -> new ZipBackupArchiveReader(archive));
        assertTrue(exception.getMessage().contains("manifest"));
    }
}
