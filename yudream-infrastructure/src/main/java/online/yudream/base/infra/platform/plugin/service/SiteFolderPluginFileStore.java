package online.yudream.base.infra.platform.plugin.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.plugin.spi.system.storage.PluginSiteFileStore;
import online.yudream.base.plugin.spi.system.storage.PluginStoredFile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.Channels;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.DirectoryStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * 站点目录文件端口实现（SPI 2.37.0）：锚定 {@code sites/{siteCode}/plugins/{pluginCode}/}。
 * <p>
 * 物理根取 {@code yudream.platform.site.root}（默认 {@code sites}，相对进程工作目录），
 * 站点编码取 {@code yudream.platform.site.code}（默认 {@code default}，多站点演进时改由宿主解析）；
 * 站点编码与插件编码都必须是安全路径段，插件不可指定站点。
 * <p>
 * 安全语义：写/删/列/读一律限制在调用插件自属目录内（绝对路径、{@code ..} 与越界解析拒绝）；
 * 路径逐级以 {@code NOFOLLOW_LINKS} 校验祖先链——任一层为符号链接或非目录即拒绝，目录创建后
 * 再次复验，并以「真实路径必须仍在站点数据根内」做边界兜底，可发现本机并发把父级替换成
 * 指向外部符号链接的情形；写入落点复验失败会清理产物并拒绝，读取最终文件用 {@code NOFOLLOW}
 * 打开，避免最终段被换成符号链接后读出锚点外内容。跨插件读取只经 {@link #forPlugin(String)}
 * 返回对象上的 {@code readShared}，由 {@link SiteFileReadAuthorizer} 裁决
 * （无认证主体/无权限 fail-closed）。本端口只处理站点目录文件，与飞书云文档无关。
 * <p>
 * <b>本机部署约束</b>：Java NIO 没有 {@code openat}/{@code dirfd} 语义，无法把「校验目录」与
 * 「打开/创建文件」原子绑定；上列逐级校验、创建后复验与真实路径边界只能<b>检测</b>本机并发
 * 替换并安全失败，不能在所有平台 100% 消除 TOCTOU 窗口。站点数据根因此必须只对宿主进程账号
 * 可写（同站点/同插件目录不应授予其他本机用户写权限），把并发替换面收敛为进程内可控写入。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SiteFolderPluginFileStore {

    /** 站点数据根属性：默认 {@code sites}（相对进程工作目录）。 */
    public static final String ROOT_PROPERTY = "yudream.platform.site.root";
    /** 当前站点编码属性：单站点部署默认 {@code default}。 */
    public static final String SITE_CODE_PROPERTY = "yudream.platform.site.code";
    public static final String DEFAULT_ROOT = "sites";
    public static final String DEFAULT_SITE_CODE = "default";

    private static final Pattern CODE_PATTERN = Pattern.compile("[A-Za-z0-9][A-Za-z0-9_-]{0,63}");
    private static final int MAX_RELATIVE_LENGTH = 512;
    private static final int MAX_SEGMENT_LENGTH = 128;
    private static final String PLUGINS_SEGMENT = "plugins";

    private final Environment environment;
    private final SiteFileReadAuthorizer readAuthorizer;

    /** 为指定插件创建站点目录端口视图。 */
    public PluginSiteFileStore forPlugin(String pluginCode) {
        return new PluginScopedSiteFileStore(requireCode(pluginCode, "插件编码"));
    }

    /**
     * 幂等确保插件在当前站点下的目录存在（逐级创建并逐级校验符号链接），
     * 返回站点数据根下的相对逻辑路径 {@code {siteCode}/plugins/{pluginCode}}；
     * 供插件启用等宿主时机主动创建。
     */
    public String ensurePluginDirectory(String pluginCode) {
        String code = requireCode(pluginCode, "插件编码");
        ensuredPluginDirectoryPath(siteCode(), code);
        return logicalDirectory(siteCode(), code);
    }

    private String siteCode() {
        String raw = environment.getProperty(SITE_CODE_PROPERTY);
        String value = StringUtils.hasText(raw) ? raw.trim() : DEFAULT_SITE_CODE;
        if (!CODE_PATTERN.matcher(value).matches()) {
            throw new BizException("站点编码不合法（" + SITE_CODE_PROPERTY + "）：" + raw);
        }
        return value;
    }

    private Path siteRoot() {
        String raw = environment.getProperty(ROOT_PROPERTY);
        String value = StringUtils.hasText(raw) ? raw.trim() : DEFAULT_ROOT;
        return Path.of(value).toAbsolutePath().normalize();
    }

    private Path pluginDirectoryPath(String siteCode, String pluginCode) {
        return siteRoot().resolve(siteCode).resolve(PLUGINS_SEGMENT).resolve(pluginCode);
    }

    private static String logicalDirectory(String siteCode, String pluginCode) {
        return siteCode + "/" + PLUGINS_SEGMENT + "/" + pluginCode;
    }

    /**
     * 逐级创建并校验插件目录链：站点数据根允许由部署方软链（边界按解析后的真实路径计算），
     * 其后每一级（站点编码、{@code plugins}、插件编码）都必须不存在或为非符号链接目录。
     */
    private Path ensuredPluginDirectoryPath(String siteCode, String pluginCode) {
        Path root = ensureRootDirectory(siteRoot());
        Path current = ensureChildDirectory(root, siteCode, "站点编码目录");
        current = ensureChildDirectory(current, PLUGINS_SEGMENT, "站点插件目录");
        current = ensureChildDirectory(current, pluginCode, "插件站点目录");
        verifyRealPathWithin(root, current, logicalDirectory(siteCode, pluginCode));
        return current;
    }

    private static Path ensureRootDirectory(Path root) {
        if (!Files.exists(root, LinkOption.NOFOLLOW_LINKS)) {
            try {
                Files.createDirectories(root);
            }
            catch (IOException exception) {
                throw ioFailure("创建站点数据根失败", exception);
            }
        }
        if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) {
            throw new BizException("站点数据根不是目录：" + root);
        }
        return root;
    }

    private static Path ensureChildDirectory(Path parent, String name, String label) {
        Path child = parent.resolve(name);
        if (!Files.exists(child, LinkOption.NOFOLLOW_LINKS)) {
            try {
                Files.createDirectory(child);
            }
            catch (FileAlreadyExistsException ignored) {
                // 并发创建：落到下面的校验
            }
            catch (IOException exception) {
                throw ioFailure("创建" + label + "失败", exception);
            }
        }
        if (Files.isSymbolicLink(child)) {
            throw new BizException(label + "是符号链接，已拒绝：" + name);
        }
        if (!Files.isDirectory(child, LinkOption.NOFOLLOW_LINKS)) {
            throw new BizException(label + "不是目录：" + name);
        }
        return child;
    }

    /** 规范化插件提供的相对路径：拒绝绝对路径、盘符、空段、点段、超长段与控制字符。 */
    static String normalizeRelative(String raw) {
        if (!StringUtils.hasText(raw)) {
            throw new BizException("站点目录文件路径不能为空");
        }
        String value = raw.trim().replace('\\', '/');
        if (value.startsWith("/") || value.matches("^[A-Za-z]:.*")) {
            throw new BizException("站点目录文件路径不合法（禁止绝对路径）：" + raw);
        }
        if (value.length() > MAX_RELATIVE_LENGTH) {
            throw new BizException("站点目录文件路径过长：" + raw);
        }
        String[] segments = value.split("/", -1);
        for (String segment : segments) {
            if (segment.isEmpty() || ".".equals(segment) || "..".equals(segment)) {
                throw new BizException("站点目录文件路径不合法：" + raw);
            }
            if (segment.length() > MAX_SEGMENT_LENGTH || containsControlChar(segment)) {
                throw new BizException("站点目录文件路径不合法：" + raw);
            }
        }
        return String.join("/", segments);
    }

    private static boolean containsControlChar(String segment) {
        for (int i = 0; i < segment.length(); i++) {
            if (Character.isISOControl(segment.charAt(i))) {
                return true;
            }
        }
        return false;
    }

    private static String requireCode(String raw, String label) {
        if (!StringUtils.hasText(raw) || !CODE_PATTERN.matcher(raw.trim()).matches()) {
            throw new BizException(label + "不合法：" + raw);
        }
        return raw.trim();
    }

    /**
     * 逐级解析相对路径并做 {@code NOFOLLOW_LINKS} 校验：任一已存在的祖先为符号链接或
     * （非末级）不是目录即拒绝；{@code createMissing} 为 true 时逐级创建目录（创建后同样校验）。
     * 返回最深条目路径；创建关闭时遇到缺失层级提前返回该缺失路径（交由调用方按不存在处理）。
     */
    private static Path resolveChain(Path anchor, String relative, boolean createMissing) {
        Path current = anchor;
        String[] segments = relative.split("/");
        for (int i = 0; i < segments.length; i++) {
            Path next = current.resolve(segments[i]);
            boolean last = i == segments.length - 1;
            if (!Files.exists(next, LinkOption.NOFOLLOW_LINKS)) {
                // 末级或（读路径下）缺失的父级：不创建，原样返回交由调用方处理
                if (last || !createMissing) {
                    return next;
                }
                try {
                    Files.createDirectory(next);
                }
                catch (FileAlreadyExistsException ignored) {
                    // 并发创建：落到下面的校验
                }
                catch (IOException exception) {
                    throw ioFailure("创建站点目录失败", exception);
                }
            }
            if (Files.isSymbolicLink(next)) {
                throw new BizException("站点目录路径包含符号链接，已拒绝：" + relative);
            }
            if (!last && !Files.isDirectory(next, LinkOption.NOFOLLOW_LINKS)) {
                throw new BizException("站点目录路径存在非目录层级，已拒绝：" + relative);
            }
            current = next;
        }
        return current;
    }

    /**
     * 真实路径边界兜底：取候选路径最深的已存在祖先（含候选自身）解析真实路径，
     * 必须仍位于容器（站点数据根）真实路径内；可发现本机并发把父级替换成指向外部的符号链接。
     */
    private static void verifyRealPathWithin(Path container, Path candidate, String context) {
        Path existing = candidate;
        while (existing != null && !Files.exists(existing, LinkOption.NOFOLLOW_LINKS)) {
            existing = existing.getParent();
        }
        if (existing == null || !Files.exists(container, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        try {
            Path containerReal = container.toRealPath();
            Path existingReal = existing.toRealPath();
            if (!existingReal.startsWith(containerReal)) {
                throw new BizException("站点目录真实路径越界，已拒绝：" + context);
            }
        }
        catch (IOException exception) {
            throw ioFailure("校验站点目录真实路径失败", exception);
        }
    }

    private static Optional<String> contentTypeOf(Path file) {
        try {
            return Optional.ofNullable(Files.probeContentType(file));
        }
        catch (IOException exception) {
            return Optional.empty();
        }
    }

    /** 以 {@code NOFOLLOW_LINKS} 打开常规文件，避免最终段在打开瞬间被替换为符号链接。 */
    private static PluginStoredFile storedFile(String relative, Path file) {
        try {
            SeekableByteChannel channel = Files.newByteChannel(file,
                    Set.of(StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS));
            return new PluginStoredFile(relative, contentTypeOf(file).orElse(""),
                    Files.size(file), Channels.newInputStream(channel));
        }
        catch (IOException exception) {
            throw ioFailure("读取站点目录文件失败", exception);
        }
    }

    private static boolean isRegularFileNoLinks(Path file) {
        return !Files.isSymbolicLink(file) && Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS);
    }

    /** 单文件写入上限（字节）；可覆写以便测试用小上限验证有界拷贝。 */
    long maxWriteBytes() {
        return PluginSiteFileStore.MAX_WRITE_BYTES;
    }

    /**
     * 有界拷贝：不信任调用方声明的长度，边读边累计，实际字节数一旦超过上限立即拒绝
     * （超限分片不写入，磁盘占用不超过上限），避免「声明长度伪小、实际无限写」。
     */
    private static long copyBounded(InputStream source, OutputStream target, long maxBytes) throws IOException {
        byte[] buffer = new byte[8192];
        long total = 0;
        int read;
        while ((read = source.read(buffer)) != -1) {
            if (read == 0) {
                continue;
            }
            if (total + read > maxBytes) {
                throw new BizException("站点目录文件超过单文件上限（" + maxBytes + " 字节），已拒绝");
            }
            target.write(buffer, 0, read);
            total += read;
        }
        return total;
    }

    /**
     * 单个插件视角的站点目录端口：写/删/列/读严格限制在自属目录；
     * {@code readShared} 才触达其他插件目录且必须先过宿主授权。
     */
    private final class PluginScopedSiteFileStore implements PluginSiteFileStore {

        private final String pluginCode;

        private PluginScopedSiteFileStore(String pluginCode) {
            this.pluginCode = pluginCode;
        }

        @Override
        public String pluginDirectory() {
            return ensurePluginDirectory(pluginCode);
        }

        @Override
        public String write(String relativePath, InputStream content, long contentLength, String contentType) {
            String relative = normalizeRelative(relativePath);
            if (content == null) {
                throw new BizException("站点目录文件内容不能为空");
            }
            long limit = maxWriteBytes();
            if (contentLength < 0 || contentLength > limit) {
                throw new BizException("站点目录文件大小不合法（0 ~ " + limit + " 字节）：" + contentLength);
            }
            Path root = siteRoot();
            Path directory = ensuredPluginDirectoryPath(siteCode(), pluginCode);
            Path target = resolveChain(directory, relative, true);
            if (Files.isDirectory(target, LinkOption.NOFOLLOW_LINKS)) {
                throw new BizException("站点目录写入目标已存在同名目录，已拒绝：" + relative);
            }
            verifyRealPathWithin(root, target, relative);
            Path parent = target.getParent();
            Path temp = null;
            try {
                temp = parent.resolve("." + target.getFileName() + ".tmp-" + UUID.randomUUID());
                try (OutputStream output = Files.newOutputStream(temp,
                        StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
                    // 有界拷贝：不信任声明的 contentLength，实际字节数超限立即拒绝
                    long copied = copyBounded(content, output, limit);
                    if (copied != contentLength) {
                        throw new BizException("站点目录文件写入长度不一致：期望 " + contentLength + " 字节，实际 "
                                + copied + " 字节");
                    }
                }
                // 创建后复验：并发替换父目录会被真实路径边界发现，此时清理临时文件并拒绝
                if (Files.isSymbolicLink(temp)) {
                    throw new BizException("站点目录临时文件被替换为符号链接，已拒绝：" + relative);
                }
                verifyRealPathWithin(root, temp, relative);
                try {
                    Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                }
                catch (AtomicMoveNotSupportedException exception) {
                    Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
                }
                temp = null;
                // 落点复验：不得是符号链接、必须是常规文件、真实路径仍在站点数据根内
                if (!isRegularFileNoLinks(target)) {
                    deleteQuietly(target);
                    throw new BizException("站点目录写入落点校验失败，已拒绝：" + relative);
                }
                verifyRealPathWithin(root, target, relative);
                return relative;
            }
            catch (IOException exception) {
                throw ioFailure("站点目录文件写入失败", exception);
            }
            finally {
                deleteQuietly(temp);
            }
        }

        @Override
        public List<String> list(String directory) {
            Path root = siteRoot();
            Path base = existingPluginDirectory();
            if (base == null) {
                return List.of();
            }
            String relative = StringUtils.hasText(directory) ? normalizeRelative(directory) : "";
            Path target = relative.isEmpty() ? base : resolveChain(base, relative, false);
            if (!Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
                return List.of();
            }
            verifyRealPathWithin(root, target, relative);
            if (Files.isSymbolicLink(target) || !Files.isDirectory(target, LinkOption.NOFOLLOW_LINKS)) {
                throw new BizException("站点目录列举目标不是常规目录，已拒绝：" + relative);
            }
            try {
                Path before = target.toRealPath();
                List<String> entries = new ArrayList<>();
                try (DirectoryStream<Path> stream = Files.newDirectoryStream(target)) {
                    for (Path entry : stream) {
                        if (Files.isSymbolicLink(entry)) {
                            continue;
                        }
                        entries.add(relative.isEmpty()
                                ? entry.getFileName().toString()
                                : relative + "/" + entry.getFileName());
                    }
                }
                Path after = target.toRealPath();
                if (!before.equals(after)) {
                    throw new BizException("站点目录在列举期间被替换，已拒绝返回结果：" + relative);
                }
                entries.sort(String::compareTo);
                return List.copyOf(entries);
            }
            catch (IOException exception) {
                throw ioFailure("列出站点目录失败", exception);
            }
        }

        @Override
        public PluginStoredFile read(String relativePath) {
            String relative = normalizeRelative(relativePath);
            Path root = siteRoot();
            Path base = existingPluginDirectory();
            if (base == null) {
                throw new BizException("站点目录文件不存在或不是常规文件：" + relative);
            }
            Path target = resolveChain(base, relative, false);
            if (!Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
                throw new BizException("站点目录文件不存在或不是常规文件：" + relative);
            }
            verifyRealPathWithin(root, target, relative);
            if (!isRegularFileNoLinks(target)) {
                throw new BizException("站点目录文件不存在或不是常规文件：" + relative);
            }
            return storedFile(relative, target);
        }

        @Override
        public void delete(String relativePath) {
            String relative = normalizeRelative(relativePath);
            Path root = siteRoot();
            Path base = existingPluginDirectory();
            if (base == null) {
                return;
            }
            Path target = resolveChain(base, relative, false);
            if (!Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
                return;
            }
            verifyRealPathWithin(root, target, relative);
            if (Files.isSymbolicLink(target)) {
                throw new BizException("站点目录不允许符号链接：" + relative);
            }
            if (Files.isDirectory(target, LinkOption.NOFOLLOW_LINKS)) {
                throw new BizException("站点目录端口只支持删除文件：" + relative);
            }
            if (!Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS)) {
                return;
            }
            try {
                // 删除前再确认一次真实路径边界：父级被并发替换成符号链接时不执行删除
                verifyRealPathWithin(root, target.getParent(), relative);
                Files.delete(target);
            }
            catch (IOException exception) {
                throw ioFailure("删除站点目录文件失败", exception);
            }
        }

        @Override
        public Optional<PluginStoredFile> readShared(String ownerPluginCode, String relativePath) {
            String owner = requireCode(ownerPluginCode, "插件编码");
            if (owner.equals(pluginCode)) {
                return Optional.of(read(relativePath));
            }
            if (!readAuthorizer.canReadOtherPluginFiles()) {
                throw new SecurityException("无跨插件读取站点目录的权限：需要当前用户持有宿主插件管理查看权限");
            }
            String relative = normalizeRelative(relativePath);
            Path root = siteRoot();
            Path ownerDirectory = existingPluginDirectory(pluginDirectoryPath(siteCode(), owner), owner);
            if (ownerDirectory == null) {
                return Optional.empty();
            }
            Path target = resolveChain(ownerDirectory, relative, false);
            if (!Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
                return Optional.empty();
            }
            verifyRealPathWithin(root, target, relative);
            if (!isRegularFileNoLinks(target)) {
                return Optional.empty();
            }
            return Optional.of(storedFile(relative, target));
        }

        /** 读路径的自属目录：不存在返回 null，是符号链接或真实路径越界直接拒绝。 */
        private Path existingPluginDirectory() {
            return existingPluginDirectory(pluginDirectoryPath(siteCode(), pluginCode), pluginCode);
        }

        /** 读路径的任意插件目录（含跨插件）：不存在返回 null，是符号链接或真实路径越界直接拒绝。 */
        private Path existingPluginDirectory(Path directory, String label) {
            if (Files.isSymbolicLink(directory)) {
                throw new BizException("插件站点目录是符号链接，已拒绝：" + label);
            }
            if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) {
                return null;
            }
            verifyRealPathWithin(siteRoot(), directory, label);
            return directory;
        }
    }

    private static void deleteQuietly(Path path) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        }
        catch (IOException exception) {
            log.debug("清理站点目录临时文件失败：{}", path);
        }
    }

    private static BizException ioFailure(String message, IOException cause) {
        BizException exception = new BizException(message + "：" + cause.getMessage());
        exception.initCause(cause);
        return exception;
    }
}
