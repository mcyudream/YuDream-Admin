package online.yudream.base.plugin.spi.system.feishu;

import java.util.List;

/**
 * 飞书云文档能力端口（SPI 2.37.0 起）。
 *
 * <p>本端口是宿主「平台能力 &gt; 飞书开放平台」能力（编码 {@value #CAPABILITY_CODE}）面向插件的
 * 稳定门面，覆盖现行唯一在范围的 API 模块——云文档：</p>
 * <ul>
 *   <li><b>只读</b>：Drive 云空间文件列表、Wiki 知识空间列表与知识空间节点列表、docx 文档元信息
 *       与纯文本内容、文档块分页列表；</li>
 *   <li><b>块级编辑原语（条件可用）</b>：向父块追加子块（POST children）、按下标区间删除子块
 *       （DELETE children batch_delete）、更新文本承载块的 elements（PATCH，仅
 *       {@code update_text_elements} 语义）。能否编辑取决于<b>应用对目标文档的编辑权限</b>：
 *       应用自有的飞书文档可直接编辑，他人创建的文档需要文档 owner 授权并把本应用添加为
 *       可访问应用。宿主不做静态可写开关，无权限时按飞书错误（如 403）抛运行时异常；
 *       插件必须按每次调用处理失败并给出可读提示，禁止假设一定可写。</li>
 * </ul>
 *
 * <p>保真与可扩展：块与元素一律以飞书原始 JSON 字符串读写（{@link PluginFeishuDocBlock#rawJson()}、
 * {@code childrenJson}、{@code elementsJson}），宿主不做「块 -&gt; 文本 -&gt; 块」的有损往返，
 * 上游新增字段无需升级 SPI。</p>
 *
 * <p><b>不提供</b>：文档/知识库/云空间的创建、删除、移动、权限与分享，接收整块 PATCH
 * （飞书更新块请求体是操作载荷而非块对象，按块对象提交会造成有损语义）；站点目录文件读写是
 * 另一条独立通道（{@code system.storage.PluginSiteFileStore}，SPI 代理），与飞书云文档互不相干，
 * 禁止混用。</p>
 *
 * <p>双闸门与额度：部署侧由 {@code yudream.platform.capabilities.feishu.enabled} 决定宿主是否
 * 装配该能力，运行侧由管理员在「平台能力 &gt; 飞书开放平台」启用并维护应用凭据；每次调用按
 * <b>当前登录用户</b>原子预占分钟/日额度（额度只在宿主「飞书集成」页展示与配置，不经本端口
 * 下发），超限或无法解析登录主体时 fail-closed 拒绝（运行时异常，插件应按软依赖降级并展示原因）。
 * 分页由插件携带上一页 {@code nextPageToken} 再次调用，每次调用各计 1 次额度。</p>
 *
 * <p>旧宿主/未装配的宿主上的默认实现：{@link #enabled()} 恒 false，其余方法抛
 * {@link UnsupportedOperationException}；插件必须先判 {@link #enabled()}。</p>
 */
public interface PluginFeishuService {

    /** 平台能力模块编码（「平台能力」页与 {@code yudream.platform.capabilities.feishu.*} 配置前缀）。 */
    String CAPABILITY_CODE = "feishu";

    /**
     * 能力是否已启用且应用凭据已配置。false 时任何调用都会被拒绝；
     * 插件应隐藏入口或给出「宿主未启用飞书开放平台能力」的提示。
     */
    default boolean enabled() {
        return false;
    }

    /**
     * Drive 云空间文件列表。
     *
     * @param folderToken 目录 token；空串表示应用根目录
     * @param pageToken   分页游标；空串表示第一页
     * @param pageSize    期望条数，宿主钳制到远端与平台上限
     */
    default PluginFeishuDocPage<PluginFeishuDriveFile> driveFiles(String folderToken, String pageToken, int pageSize) {
        throw unavailable();
    }

    /** Wiki 知识空间列表。{@code pageToken}/{@code pageSize} 语义同 {@link #driveFiles}。 */
    default PluginFeishuDocPage<PluginFeishuWikiSpace> wikiSpaces(String pageToken, int pageSize) {
        throw unavailable();
    }

    /**
     * Wiki 知识空间节点列表（wiki/v2/spaces/{space_id}/nodes）。
     *
     * @param spaceId         知识空间 ID
     * @param parentNodeToken 父节点 token；空串表示根级
     * @param pageToken       分页游标；空串表示第一页
     * @param pageSize        期望条数，宿主钳制到远端与平台上限
     */
    default PluginFeishuDocPage<PluginFeishuWikiNode> wikiNodes(String spaceId, String parentNodeToken,
                                                               String pageToken, int pageSize) {
        throw unavailable();
    }

    /** 文档元信息（标题/版本号）；文档不存在或不可见时抛运行时异常（保留飞书错误文案）。 */
    default PluginFeishuDocument document(String documentId) {
        throw unavailable();
    }

    /**
     * 文档纯文本内容（docx raw_content，原样透传，不经块转换）。
     *
     * @param lang 语言标识（飞书整数枚举）；null 表示宿主默认
     */
    default String documentContent(String documentId, Integer lang) {
        throw unavailable();
    }

    /**
     * 文档块分页列表（docx blocks）。
     *
     * @param pageToken 分页游标；空串表示第一页
     * @param pageSize  期望条数，宿主钳制到远端与平台上限
     */
    default PluginFeishuDocPage<PluginFeishuDocBlock> documentBlocks(String documentId, String pageToken, int pageSize) {
        throw unavailable();
    }

    /**
     * 向指定父块追加/插入子块（POST children）。条件可用：应用无目标文档编辑权限时
     * 按飞书错误抛运行时异常，调用方需处理失败。
     *
     * @param documentId   文档 ID
     * @param parentBlockId 父块 ID；空串表示文档根（page 块）
     * @param childrenJson 子块原始 JSON 原文列表（可由块读取的 {@link PluginFeishuDocBlock#rawJson()} 直接回填）
     * @param index        插入位置下标；null 表示追加到末尾
     * @return 写入结果（新版本号与受影响块 JSON）
     */
    default PluginFeishuBlockWriteResult createBlockChildren(String documentId, String parentBlockId,
                                                             List<String> childrenJson, Integer index) {
        throw unavailable();
    }

    /**
     * 按区间删除指定父块下的子块（DELETE children batch_delete）。条件可用：
     * 应用无目标文档编辑权限时按飞书错误抛运行时异常，调用方需处理失败。
     *
     * @param documentId    文档 ID
     * @param parentBlockId 父块 ID；空串表示文档根（page 块）
     * @param startIndex    起始下标（含）
     * @param endIndex      结束下标（不含）
     */
    default void deleteBlockChildren(String documentId, String parentBlockId, int startIndex, int endIndex) {
        throw unavailable();
    }

    /**
     * 更新文本承载块的 elements（PATCH，{@code update_text_elements} 语义）。条件可用：
     * 应用无目标文档编辑权限时按飞书错误抛运行时异常，调用方需处理失败。
     *
     * @param documentId         文档 ID
     * @param blockId            块 ID
     * @param elementsJson       文本元素原始 JSON 原文列表；语义与飞书 {@code update_text_elements.elements} 一致
     * @param documentRevisionId 文档版本号；null 表示按飞书默认（最新版本）
     * @return 写入结果（新版本号与受影响块 JSON）
     */
    default PluginFeishuBlockWriteResult updateBlockElements(String documentId, String blockId,
                                                             List<String> elementsJson, Long documentRevisionId) {
        throw unavailable();
    }

    /** 未提供该能力的宿主上的统一拒绝口径。 */
    static UnsupportedOperationException unavailable() {
        return new UnsupportedOperationException(
                "当前宿主未提供飞书云文档能力（需要 SPI >= 2.37.0 且已启用 feishu 平台能力）");
    }
}
