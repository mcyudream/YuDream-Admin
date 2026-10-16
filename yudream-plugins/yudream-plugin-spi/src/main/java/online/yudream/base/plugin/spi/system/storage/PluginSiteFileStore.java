package online.yudream.base.plugin.spi.system.storage;

import java.io.InputStream;
import java.util.List;
import java.util.Optional;

/**
 * 站点数据目录文件端口（SPI 2.37.0 起）：插件数据落盘到当前站点的自属目录。
 *
 * <p>目录锚定 {@code sites/{siteCode}/plugins/{pluginCode}/}：{@code siteCode} 由宿主按当前
 * 站点解析（单站点部署为稳定默认值），<b>插件不可指定站点</b>；插件永远只能触达自己在
 * 当前站点下的目录。本端口是宿主对站点目录的 SPI 代理，只处理站点目录文件，
 * <b>与飞书云文档（{@code system.feishu}，只读）无关</b>，两者禁止互相替代或混用。</p>
 *
 * <p>安全语义（宿主实现必须强制）：</p>
 * <ul>
 *   <li><b>写隔离</b>：{@link #write} / {@link #delete} 只允许落在插件自属目录内；
 *       绝对路径、{@code ..} 段、反斜杠越界与空/超长段一律拒绝。</li>
 *   <li><b>祖先链校验</b>：相对路径逐级以 {@code NOFOLLOW_LINKS} 校验——任一层（含
 *       {@code sites/{siteCode}/plugins} 链与插件目录自身）是符号链接或非目录即拒绝，
 *       防止借预置/并发替换的父级链接（如 {@code notes -> /outside}）走出锚点；
 *       目录创建后再次复验，并以「真实路径仍在站点数据根内」做边界兜底。</li>
 *   <li><b>读授权</b>：{@link #read} / {@link #list} 只读自属目录；跨插件读取必须走
 *       {@link #readShared(String, String)}，由宿主按当前认证主体（用户授权）与站点上下文
 *       （站点授权）裁决，未授权 fail-closed（{@link SecurityException}）。</li>
 *   <li><b>原子落盘</b>：写入先落同目录临时文件再原子替换（临时文件不参与目录列举），
 *       落点复验失败会清理并拒绝；读取最终文件以 {@code NOFOLLOW} 打开。</li>
 * </ul>
 *
 * <p><b>部署约束</b>：Java NIO 没有 {@code openat}/{@code dirfd} 语义，无法把「校验目录」与
 * 「打开/创建文件」原子绑定；宿主已逐级校验、创建后复验并做真实路径边界兜底，能检测本机
 * 并发替换并安全失败，但不能在所有平台 100% 消除 TOCTOU 窗口。站点数据根必须只对宿主
 * 进程账号可写，同站点/同插件目录不得授予其他本机用户写权限。</p>
 *
 * <p>旧宿主（未经 SPI 2.37.0 实现）上 {@code FrameworkServices.siteFiles(pluginCode)} 默认
 * 抛 {@link UnsupportedOperationException}，插件应按软依赖降级。</p>
 */
public interface PluginSiteFileStore {

    /** 单文件写入上限（字节，64 MiB），超出即拒绝。 */
    long MAX_WRITE_BYTES = 64L * 1024L * 1024L;

    /**
     * 确保并返回插件在<b>当前站点</b>下的自属目录（幂等）。
     * 返回站点数据根下的相对逻辑路径，形如 {@code {siteCode}/plugins/{pluginCode}}；
     * 物理根由宿主配置，插件不感知、也不可改写。
     */
    String pluginDirectory();

    /**
     * 写入插件自属目录内的文件（相对 {@link #pluginDirectory()} 的路径）。
     *
     * <p>{@code contentLength} 只是声明值：宿主用有界拷贝实际计数，流真实字节数超过
     * {@link #MAX_WRITE_BYTES} 或与声明值不一致都会拒绝并清理临时文件，调用方不得依赖
     * 「声明得小就能多写」。</p>
     *
     * @param contentLength 内容字节数，必须 {@code >= 0} 且不超过 {@link #MAX_WRITE_BYTES}
     * @return 规范化后的相对路径（正斜杠）
     */
    String write(String relativePath, InputStream content, long contentLength, String contentType);

    /** 列出插件自属目录内某子目录的直属文件与子目录（非递归，路径排序稳定）。 */
    List<String> list(String directory);

    /** 读取插件自属目录内的文件；路径非法或文件不存在时抛运行时异常。 */
    PluginStoredFile read(String relativePath);

    /** 删除插件自属目录内的文件；文件不存在时静默返回。 */
    void delete(String relativePath);

    /**
     * 跨插件读取同一站点下其他插件的文件（站点授权 + 用户授权双约束）。
     *
     * <p>宿主裁决规则：由宿主授权服务判定，当前内置规则为「当前认证主体持有宿主插件管理查看
     * 权限（{@code platform:plugin:view}）」；无认证主体、无权限或目标越过其自属目录时抛
     * {@link SecurityException}，不回落到隐式放行。插件不得假设存在按插件实例粒度的权限码
     * （宿主未提供 {@code plugin:{owner}:view} 之类的授权语义）。文件不存在返回空。</p>
     */
    Optional<PluginStoredFile> readShared(String ownerPluginCode, String relativePath);
}
