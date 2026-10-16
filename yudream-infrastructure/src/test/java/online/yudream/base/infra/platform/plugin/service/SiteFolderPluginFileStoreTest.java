package online.yudream.base.infra.platform.plugin.service;

import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.plugin.spi.system.storage.PluginSiteFileStore;
import online.yudream.base.plugin.spi.system.storage.PluginStoredFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 站点目录端口语义：目录创建、写隔离、跨插件读授权、符号链接与路径越界拒绝。
 */
class SiteFolderPluginFileStoreTest {

    @TempDir
    Path sitesRoot;

    /** 站点数据根之外的“攻击者目录”：验证符号链接祖先逃逸确实被拒绝。 */
    @TempDir
    Path outsideRoot;

    private SiteFolderPluginFileStore store(boolean crossPluginReadAllowed) {
        return store("default", crossPluginReadAllowed);
    }

    private SiteFolderPluginFileStore store(String siteCode, boolean crossPluginReadAllowed) {
        return new SiteFolderPluginFileStore(environment(siteCode), () -> crossPluginReadAllowed);
    }

    private StandardEnvironment environment(String siteCode) {
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(new MapPropertySource("test", Map.<String, Object>of(
                SiteFolderPluginFileStore.ROOT_PROPERTY, sitesRoot.toString(),
                SiteFolderPluginFileStore.SITE_CODE_PROPERTY, siteCode)));
        return environment;
    }

    private static String readAll(PluginStoredFile file) {
        try (var stream = file.inputStream()) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
        catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static ByteArrayInputStream bytes(String content) {
        return new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void pluginDirectoryCreatesSiteFolderLayout() {
        PluginSiteFileStore alpha = store(false).forPlugin("alpha");

        String directory = alpha.pluginDirectory();

        assertEquals("default/plugins/alpha", directory);
        assertTrue(Files.isDirectory(sitesRoot.resolve("default").resolve("plugins").resolve("alpha")));
        // 幂等：重复调用不报错
        assertEquals(directory, alpha.pluginDirectory());
    }

    @Test
    void writeReadListDeleteStayInsideOwnDirectory() throws Exception {
        PluginSiteFileStore alpha = store(false).forPlugin("alpha");

        String key = alpha.write("notes/a.txt", bytes("hello 站点"), 12, "text/plain");

        assertEquals("notes/a.txt", key);
        PluginStoredFile stored = alpha.read("notes/a.txt");
        assertEquals(12L, stored.contentLength());
        assertEquals("hello 站点", readAll(stored));
        assertEquals(List.of("notes"), alpha.list(""));
        assertEquals(List.of("notes/a.txt"), alpha.list("notes"));
        // 临时文件不得残留
        assertTrue(Files.list(sitesRoot.resolve("default/plugins/alpha/notes")).toList().stream()
                .noneMatch(path -> path.getFileName().toString().contains(".tmp-")));

        alpha.delete("notes/a.txt");
        assertThrows(BizException.class, () -> alpha.read("notes/a.txt"));
        alpha.delete("notes/a.txt");
        assertThrows(BizException.class, () -> alpha.delete("notes"));
    }

    @Test
    void writeRejectsTraversalAbsoluteAndInvalidPaths() {
        PluginSiteFileStore alpha = store(false).forPlugin("alpha");

        assertThrows(BizException.class, () -> alpha.write("../escape.txt", bytes("x"), 1, "text/plain"));
        assertThrows(BizException.class, () -> alpha.write("a/../../escape.txt", bytes("x"), 1, "text/plain"));
        assertThrows(BizException.class, () -> alpha.write("/absolute.txt", bytes("x"), 1, "text/plain"));
        assertThrows(BizException.class, () -> alpha.write("C:/windows.txt", bytes("x"), 1, "text/plain"));
        assertThrows(BizException.class, () -> alpha.write("a//b.txt", bytes("x"), 1, "text/plain"));
        assertThrows(BizException.class, () -> alpha.write("a/./b.txt", bytes("x"), 1, "text/plain"));
        assertThrows(BizException.class, () -> alpha.write("", bytes("x"), 1, "text/plain"));
        assertThrows(BizException.class, () -> alpha.write("notes/a.txt", bytes("x"), -1, "text/plain"));
        assertThrows(BizException.class, () -> alpha.write("notes/a.txt", bytes("x"),
                PluginSiteFileStore.MAX_WRITE_BYTES + 1, "text/plain"));
        assertThrows(BizException.class, () -> alpha.read("../default/plugins/beta/secret.txt"));
        assertFalse(Files.exists(sitesRoot.resolve("escape.txt")));
    }

    /** 声明长度伪小但实际流无限：有界拷贝必须拒绝，不能信任 contentLength。 */
    @Test
    void writeRejectsActualStreamExceedingLimitDespiteLyingLength() {
        // 测试用小上限（1 KiB）验证有界拷贝逻辑，生产上限为 MAX_WRITE_BYTES（64 MiB）
        SiteFolderPluginFileStore smallLimitStore = new SiteFolderPluginFileStore(environment("default"), () -> false) {
            @Override
            long maxWriteBytes() {
                return 1024L;
            }
        };
        PluginSiteFileStore alpha = smallLimitStore.forPlugin("alpha");
        InputStream endless = new InputStream() {
            @Override
            public int read() {
                return 0x41;
            }

            @Override
            public int read(byte[] buffer, int offset, int length) {
                java.util.Arrays.fill(buffer, offset, offset + length, (byte) 0x41);
                return length;
            }
        };

        assertThrows(BizException.class, () -> alpha.write("big.bin", endless, 1, "application/octet-stream"));
        assertFalse(Files.exists(sitesRoot.resolve("default/plugins/alpha/big.bin")));
        assertEquals(List.of(), alpha.list(""));
    }

    /** 声明长度与实际上限一致但内容超长（声明本身合法）：同样被有界拷贝拒绝且不落盘。 */
    @Test
    void writeRejectsStreamLongerThanDeclaredLength() {
        PluginSiteFileStore alpha = store(false).forPlugin("alpha");
        InputStream longer = new ByteArrayInputStream(new byte[64]);

        assertThrows(BizException.class, () -> alpha.write("mismatch.bin", longer, 16, "application/octet-stream"));
        assertFalse(Files.exists(sitesRoot.resolve("default/plugins/alpha/mismatch.bin")));
        assertEquals(List.of(), alpha.list(""));
    }

    @Test
    void listAndReadDoNotSeeOtherProducts() throws Exception {
        PluginSiteFileStore alpha = store(false).forPlugin("alpha");
        PluginSiteFileStore beta = store(false).forPlugin("beta");

        alpha.write("shared/alpha.txt", bytes("alpha"), 5, "text/plain");
        beta.pluginDirectory();

        assertThrows(BizException.class, () -> beta.read("shared/alpha.txt"));
        assertEquals(List.of(), beta.list(""));
        assertTrue(Files.exists(sitesRoot.resolve("default/plugins/beta")));
    }

    @Test
    void readSharedRequiresHostAuthorizationAndStaysInsideOwnerDirectory() {
        PluginSiteFileStore alpha = store(true).forPlugin("alpha");
        PluginSiteFileStore betaDenied = store(false).forPlugin("beta");
        PluginSiteFileStore betaAllowed = store(true).forPlugin("beta");

        alpha.write("shared/alpha.txt", bytes("alpha"), 5, "text/plain");
        alpha.write("private/content.txt", bytes("beta-bait"), 9, "text/plain");
        betaAllowed.write("private/beta.txt", bytes("beta"), 4, "text/plain");

        assertThrows(SecurityException.class, () -> betaDenied.readShared("alpha", "shared/alpha.txt"));
        Optional<PluginStoredFile> shared = betaAllowed.readShared("alpha", "shared/alpha.txt");
        assertTrue(shared.isPresent());
        assertEquals("alpha", readAll(shared.get()));
        assertEquals(Optional.empty(), betaAllowed.readShared("alpha", "shared/missing.txt"));
        assertThrows(BizException.class, () -> betaAllowed.readShared("alpha", "../beta/private/beta.txt"));
        // 自己读自己不受授权约束，且不走 shared 判定
        assertEquals("beta", readAll(betaAllowed.read("private/beta.txt")));
    }

    @Test
    void readSharedRejectsUnsafeOwnerCode() {
        PluginSiteFileStore beta = store(true).forPlugin("beta");
        assertThrows(BizException.class, () -> beta.readShared("../alpha", "a.txt"));
        assertThrows(BizException.class, () -> beta.readShared("alpha/../../etc", "a.txt"));
    }

    @Test
    void invalidSiteCodeFailsClosed() {
        PluginSiteFileStore alpha = store("../evil", false).forPlugin("alpha");
        assertThrows(BizException.class, () -> alpha.pluginDirectory());
        assertThrows(BizException.class, () -> store(false).forPlugin("alpha/../beta"));
        assertThrows(BizException.class, () -> store(false).forPlugin("alpha/beta"));
    }

    @Test
    void symbolicLinkInsidePluginDirectoryIsRejected() throws Exception {
        PluginSiteFileStore alpha = store(false).forPlugin("alpha");
        alpha.pluginDirectory();
        Path target = sitesRoot.resolve("default/plugins/alpha/real.txt");
        Files.writeString(target, "real");
        Path link = sitesRoot.resolve("default/plugins/alpha/link.txt");
        if (!createSymlink(link, target)) {
            return;
        }
        assertThrows(BizException.class, () -> alpha.read("link.txt"));
        assertThrows(BizException.class, () -> alpha.delete("link.txt"));
        assertEquals(List.of("real.txt"), alpha.list(""));
    }

    // ---------- 祖先目录符号链接（逃逸锚点）防护 ----------

    @Test
    void writeRejectsSymlinkedAncestorDirectory() throws Exception {
        PluginSiteFileStore alpha = store(false).forPlugin("alpha");
        alpha.pluginDirectory();
        Files.createDirectories(outsideRoot);
        assertTrue(createSymlink(sitesRoot.resolve("default/plugins/alpha/notes"), outsideRoot));

        assertThrows(BizException.class, () -> alpha.write("notes/a.txt", bytes("escape"), 6, "text/plain"));
        assertFalse(Files.exists(outsideRoot.resolve("a.txt")));
        // 祖先链接即使仍指向站点根内部也拒绝：写必须落在自属目录，不能借链接换锚点
        Path innerReal = sitesRoot.resolve("default/plugins/alpha/inner-real");
        Files.createDirectories(innerReal);
        assertTrue(createSymlink(sitesRoot.resolve("default/plugins/alpha/inner-link"), innerReal));
        assertThrows(BizException.class, () -> alpha.write("inner-link/c.txt", bytes("escape"), 6, "text/plain"));
        assertFalse(Files.exists(innerReal.resolve("c.txt")));
    }

    @Test
    void readListDeleteRejectSymlinkedAncestorDirectory() throws Exception {
        PluginSiteFileStore alpha = store(false).forPlugin("alpha");
        alpha.pluginDirectory();
        Files.createDirectories(outsideRoot.resolve("deep"));
        Files.writeString(outsideRoot.resolve("deep/secret.txt"), "outside-secret");
        assertTrue(createSymlink(sitesRoot.resolve("default/plugins/alpha/notes"), outsideRoot));

        assertThrows(BizException.class, () -> alpha.read("notes/deep/secret.txt"));
        assertThrows(BizException.class, () -> alpha.list("notes"));
        assertThrows(BizException.class, () -> alpha.delete("notes/deep/secret.txt"));
        assertTrue(Files.exists(outsideRoot.resolve("deep/secret.txt")));
    }

    @Test
    void sharedReadAndOwnerDirectoryRejectSymlinkedAncestors() throws Exception {
        PluginSiteFileStore alpha = store(true).forPlugin("alpha");
        PluginSiteFileStore beta = store(true).forPlugin("beta");
        alpha.pluginDirectory();
        beta.pluginDirectory();
        Files.createDirectories(outsideRoot);
        Files.writeString(outsideRoot.resolve("secret.txt"), "outside-secret");
        // 目标插件目录内的祖先链接（如 shared -> 外部目录）：跨插件读同样拒绝，不泄露内容
        assertTrue(createSymlink(sitesRoot.resolve("default/plugins/alpha/shared"), outsideRoot));
        assertThrows(BizException.class, () -> beta.readShared("alpha", "shared/secret.txt"));
        // 插件目录自身被替换成符号链接：请求方自属与跨插件两侧都拒绝
        assertTrue(createSymlink(sitesRoot.resolve("default/plugins/ghost"), outsideRoot));
        assertThrows(BizException.class, () -> store(true).forPlugin("ghost").read("secret.txt"));
        assertThrows(BizException.class, () -> beta.readShared("ghost", "secret.txt"));
    }

    @Test
    void siteChainSymlinkIsRejectedBeforeCreatingOrReading() throws Exception {
        Files.createDirectories(outsideRoot);
        // 站点 plugins 目录被预先替换成指向外部的符号链接：创建与读取都必须拒绝
        Files.createDirectories(sitesRoot.resolve("s1"));
        assertTrue(createSymlink(sitesRoot.resolve("s1/plugins"), outsideRoot));
        Files.createDirectories(outsideRoot.resolve("alpha"));

        assertThrows(BizException.class, () -> store("s1", false).forPlugin("alpha").pluginDirectory());
        assertThrows(BizException.class, () -> store("s1", false).forPlugin("alpha").read("x.txt"));
        assertThrows(BizException.class, () -> store("s1", true).forPlugin("beta")
                .readShared("alpha", "x.txt"));
    }

    /** 创建符号链接；平台不支持或无权限时返回 false（调用方跳过断言）。 */
    private static boolean createSymlink(Path link, Path target) {
        try {
            Files.createSymbolicLink(link, target);
            return true;
        }
        catch (UnsupportedOperationException | java.io.IOException | SecurityException exception) {
            return false;
        }
    }
}
