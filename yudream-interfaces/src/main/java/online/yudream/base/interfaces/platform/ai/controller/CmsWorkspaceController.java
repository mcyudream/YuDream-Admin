package online.yudream.base.interfaces.platform.ai.controller;

import lombok.RequiredArgsConstructor;
import online.yudream.base.application.platform.cms.service.CmsWorkspaceStore;
import online.yudream.base.domain.system.security.anno.PermissionRegister;
import online.yudream.base.interfaces.common.Result;
import online.yudream.base.interfaces.system.security.support.SecurityPrincipalSupport;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * CMS 静态站点工作区：多页面/资产文件树 IO 与发布快照。
 * 文件内容统一 base64 承载（encoding=text|base64），二进制资产（图片/视频）原样存储。
 */
@RestController
@RequestMapping("/api/platform/ai/cms/workspace")
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "yudream.platform.capabilities.ai", name = "enabled", havingValue = "true")
public class CmsWorkspaceController {

    private final CmsWorkspaceStore workspaceStore;

    @GetMapping
    @PermissionRegister(code = "platform:ai:generate", name = "CMS 工作区文件列表", module = "平台能力", desc = "列出 CMS 静态站点工作区文件树")
    public Result<List<CmsWorkspaceStore.WorkspaceEntry>> list() {
        return Result.ok(workspaceStore.list(currentUserId()));
    }

    @GetMapping("/file")
    @PermissionRegister(code = "platform:ai:generate", name = "读取 CMS 工作区文件", module = "平台能力", desc = "读取工作区文本或二进制文件")
    public Result<Map<String, Object>> read(@RequestParam String path) {
        byte[] data = workspaceStore.readFile(currentUserId(), path);
        boolean binary = isBinary(data);
        return Result.ok(Map.of(
                "path", path,
                "encoding", binary ? "base64" : "text",
                "content", binary
                        ? java.util.Base64.getEncoder().encodeToString(data)
                        : new String(data, StandardCharsets.UTF_8)));
    }

    @PostMapping("/file")
    @PermissionRegister(code = "platform:ai:generate", name = "写入 CMS 工作区文件", module = "平台能力", desc = "写入工作区文本或二进制文件")
    public Result<Void> write(@RequestBody Map<String, String> body) {
        String path = body.getOrDefault("path", "");
        String content = body.getOrDefault("content", "");
        byte[] data = "base64".equals(body.get("encoding"))
                ? java.util.Base64.getDecoder().decode(content)
                : content.getBytes(StandardCharsets.UTF_8);
        workspaceStore.writeFile(currentUserId(), path, data);
        return Result.ok(null);
    }

    @PostMapping("/publish")
    @PermissionRegister(code = "platform:ai:generate", name = "发布 CMS 工作区", module = "平台能力", desc = "将工作区整树快照为发布版本")
    public Result<Map<String, Object>> publish() {
        return Result.ok(Map.of("files", workspaceStore.publish(currentUserId())));
    }

    /** 框架运行时库：静态页一行 <script src> 接入 data-yb-* 渲染与后端数据互通。 */
    @GetMapping(value = "/lib.js", produces = "application/javascript;charset=UTF-8")
    public org.springframework.http.ResponseEntity<byte[]> lib() throws java.io.IOException {
        byte[] body = new ClassPathResource("cms/cms-lib.js").getInputStream().readAllBytes();
        return org.springframework.http.ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/javascript;charset=UTF-8"))
                .header("Cache-Control", "public, max-age=300")
                .body(body);
    }

    private static Long currentUserId() {
        return SecurityPrincipalSupport.current().userId();
    }

    private static boolean isBinary(byte[] data) {
        int limit = Math.min(data.length, 8192);
        for (int i = 0; i < limit; i++) {
            if (data[i] == 0) {
                return true;
            }
        }
        return false;
    }
}
