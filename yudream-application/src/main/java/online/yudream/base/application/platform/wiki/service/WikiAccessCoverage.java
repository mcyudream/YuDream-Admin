package online.yudream.base.application.platform.wiki.service;

import online.yudream.base.domain.common.exception.BizException;

import java.util.List;

/**
 * 受限（RESTRICTED）空间 ACL 的暴露开关与覆盖清单。
 * <p>
 * 实例 ACL 只有覆盖「全部空间入口」才是安全的：只要还存在未接入 ACL 的空间入口，
 * 持有全局 {@code platform:wiki:*} 权限码的非成员就能从该入口读写受限空间，形成
 * 「看起来受限、实际可绕过」的假安全。因此：
 * <ul>
 *   <li>平台侧空间入口必须经 {@code WikiSpaceAclAppService}（应用层双闸门）或
 *       {@code @WikiSpaceAccess}（声明式守卫）执行实例 ACL，覆盖由
 *       {@code WikiSpaceAclCoverageTest} 反射校验；</li>
 *   <li>{@link #DOCUMENTED_EXCEPTIONS} 是显式登记且经确认不构成旁路的例外；</li>
 *   <li>新增空间入口必须先接入 ACL 或登记例外，否则覆盖校验失败；</li>
 *   <li>覆盖不成立时把 {@link #RESTRICTED_EXPOSURE_READY} 置回 false：新建空间保持
 *       LEGACY（仍写入创建者 MANAGE 成员），启用受限访问的用例 fail closed。</li>
 * </ul>
 */
public final class WikiAccessCoverage {

    /** 受限 ACL 是否已具备暴露条件；跨 Wiki AI/chat 调用链尚未完成实例级授权，当前保持关闭。 */
    public static final boolean RESTRICTED_EXPOSURE_READY = false;

    /** 经确认不构成受限空间旁路的例外（保持全局权限码控制，不做实例 ACL）。 */
    public static final List<String> DOCUMENTED_EXCEPTIONS = List.of(
            // 全局归档导入：总是按唯一 slug 新建空间，既不读取也不覆盖既有受限空间
            "WikiMigrationController#importArchive（全局导入，仅新建空间）",
            // 公开阅读轴：由空间 publicReadEnabled + 已发布版本过滤控制，属显式公开配置而非实例 ACL
            "公开入口 /api/public/wiki/**（含公开检索与公开对话 SSE，需空间显式公开）",
            // 公开工具只返回显式公开的已发布页面；管理工具与通用聊天/Agent 仍需实例 ACL，不能算例外
            "WikiPublicSearchAiTool（只读显式公开的已发布页面）");

    private WikiAccessCoverage() {
    }

    /** 受限访问就绪校验；未就绪时 fail closed 并说明原因。 */
    public static void ensureRestrictedExposureReady() {
        if (RESTRICTED_EXPOSURE_READY) {
            return;
        }
        throw new BizException("受限访问尚未开放：空间入口的实例 ACL 覆盖不完整，请先接入 ACL 并更新覆盖清单");
    }
}
