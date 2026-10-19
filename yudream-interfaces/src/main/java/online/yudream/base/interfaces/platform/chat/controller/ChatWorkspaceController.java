package online.yudream.base.interfaces.platform.chat.controller;

import lombok.RequiredArgsConstructor;
import online.yudream.base.application.platform.chat.service.ChatWorkspaceStore;
import online.yudream.base.domain.system.security.anno.PermissionRegister;
import online.yudream.base.interfaces.common.Result;
import online.yudream.base.interfaces.system.security.support.SecurityPrincipalSupport;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * 用户工作区：每用户一个默认工作区（随账号存在），AI 助手会话与上传文件都在其中。
 * 文件内容统一以 {path, encoding=text|base64, content} 承载，助手侧同名只读工具读取同一命名空间。
 */
@RestController
@RequestMapping("/api/platform/chat/workspace")
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "yudream.platform.capabilities.ai", name = "enabled", havingValue = "true")
public class ChatWorkspaceController {

    private final ChatWorkspaceStore workspaceStore;

    @GetMapping
    @PermissionRegister(code = "platform:chat:workspace:view", name = "查看我的工作区", module = "AI 助手", desc = "列出当前用户工作区文件")
    public Result<List<ChatWorkspaceStore.Entry>> list() {
        return Result.ok(workspaceStore.list(currentUserId()));
    }

    @GetMapping("/file")
    @PermissionRegister(code = "platform:chat:workspace:view", name = "读取我的工作区文件", module = "AI 助手", desc = "读取当前用户工作区文件内容")
    public Result<Map<String, Object>> read(@RequestParam String path) {
        byte[] data = workspaceStore.readFile(currentUserId(), path);
        boolean binary = isBinary(data);
        return Result.ok(Map.of(
                "path", path,
                "encoding", binary ? "base64" : "text",
                "content", binary
                        ? Base64.getEncoder().encodeToString(data)
                        : new String(data, StandardCharsets.UTF_8)));
    }

    @PostMapping("/upload")
    @PermissionRegister(code = "platform:chat:workspace:edit", name = "上传我的工作区文件", module = "AI 助手", desc = "上传文件到当前用户工作区")
    public Result<Void> upload(@RequestParam String path, @RequestPart("file") MultipartFile file) throws java.io.IOException {
        workspaceStore.writeFile(currentUserId(), path, file.getBytes());
        return Result.ok(null);
    }

    @DeleteMapping("/file")
    @PermissionRegister(code = "platform:chat:workspace:edit", name = "删除我的工作区文件", module = "AI 助手", desc = "删除当前用户工作区文件")
    public Result<Void> delete(@RequestParam String path) {
        workspaceStore.delete(currentUserId(), path);
        return Result.ok(null);
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
