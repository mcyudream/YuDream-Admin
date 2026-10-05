package online.yudream.base.application.platform.cms.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import online.yudream.base.application.platform.ai.service.CmsCanvasValidateAiTool;
import online.yudream.base.domain.common.exception.BizException;

import online.yudream.base.domain.platform.cms.enumerate.PageTemplate;
import online.yudream.base.domain.platform.ai.valobj.AiAgentToolCall;
import online.yudream.base.domain.platform.ai.valobj.AiAgentToolResult;
import online.yudream.base.domain.platform.cms.aggregate.CmsPage;
import online.yudream.base.domain.platform.cms.enumerate.PageStatus;
import online.yudream.base.domain.platform.cms.repo.CmsPageRepo;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * CMS 静态站点工作区发布闭环：先跑画布完整性校验闸门，再整树快照到发布命名空间，
 * 并把 pages/*.html 同步为 CmsPage（slug = 文件路径，保留 SEO 元数据；index.html 为
 * 站点首页内容，不参与页面同步——首页仍由 HomePageLayout 承载）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CmsWorkspacePublishService {

    private final CmsWorkspaceStore workspaceStore;
    private final CmsCanvasValidateAiTool validateTool;
    private final CmsPageRepo cmsPageRepo;

    public Map<String, Object> publish(Long userId, String themeCode, boolean published) {
        byte[] html = safeRead(workspaceStore, userId, "pages/index.html");
        byte[] css = safeRead(workspaceStore, userId, "assets/css/site.css");
        byte[] js = safeRead(workspaceStore, userId, "assets/js/site.js");
        validateGate(html, css, js);

        int files = workspaceStore.publish(userId);
        int synced = syncPages(userId, themeCode, css, js, published);
        log.info("CMS 工作区发布完成：user={} files={} pages={}", userId, files, synced);
        return Map.of("files", files, "pages", synced);
    }

    private void validateGate(byte[] html, byte[] css, byte[] js) {
        AiAgentToolResult result = validateTool.execute(new AiAgentToolCall("cms.canvas.validate", Map.of(
                "htmlContent", new String(html, StandardCharsets.UTF_8),
                "cssContent", new String(css, StandardCharsets.UTF_8),
                "jsContent", new String(js, StandardCharsets.UTF_8),
                // 静态站点工作区：页面允许自带 script（第三方/内联），数据互通走平台 lib.js
                "allowScripts", true)));
        Object valid = result.payload() == null ? null : result.payload().get("valid");
        if (!Boolean.TRUE.equals(valid)) {
            Object errors = result.payload() == null ? List.of() : result.payload().get("errors");
            throw new BizException("站点完整性校验未通过：" + errors);
        }
    }

    /** pages/*.html → CmsPage（slug = 相对路径去 .html；index.html 为首页内容，跳过）。 */
    private int syncPages(Long userId, String themeCode, byte[] css, byte[] js, boolean published) {
        int synced = 0;
        for (CmsWorkspaceStore.WorkspaceEntry entry : workspaceStore.list(userId)) {
            String path = entry.path();
            if (!path.startsWith("pages/") || !path.endsWith(".html") || path.equals("pages/index.html")) {
                continue;
            }
            String slug = path.substring("pages/".length(), path.length() - ".html".length());
            byte[] html = workspaceStore.readFile(userId, path);
            String title = StringUtils.hasText(slug) ? slug : "index";
            CmsPage page = cmsPageRepo.findBySlug(themeCode, slug).orElseGet(() -> CmsPage.create(title, slug, themeCode));
            page.update(title, slug, null, null, null,
                    List.of(), List.of(), null,
                    new String(html, StandardCharsets.UTF_8),
                    new String(css, StandardCharsets.UTF_8),
                    new String(js, StandardCharsets.UTF_8),
                    null, null, null,
                    PageTemplate.DEFAULT, published ? PageStatus.PUBLISHED : PageStatus.DRAFT);
            cmsPageRepo.save(page);
            synced++;
        }
        return synced;
    }

    private static byte[] safeRead(CmsWorkspaceStore store, Long userId, String path) {
        try {
            return store.readFile(userId, path);
        } catch (Exception e) {
            return new byte[0];
        }
    }
}
