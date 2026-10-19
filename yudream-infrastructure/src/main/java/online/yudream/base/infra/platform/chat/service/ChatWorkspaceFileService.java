package online.yudream.base.infra.platform.chat.service;

import io.agentscope.harness.agent.filesystem.remote.store.StoreItem;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import online.yudream.base.application.platform.chat.service.ChatWorkspaceStore;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.infra.platform.ai.runtime.MongoBaseStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 用户工作区文件 IO：与 Harness 远程文件系统共享同一 MongoBaseStore 与命名空间公式
 * （agents/general/users/&lt;uid&gt;/workspace），值为 {content, encoding} 载荷。
 * 单文件大小受 Mongo 文档上限约束，写入前显式拒绝超大文件。
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "yudream.platform.capabilities.ai", name = "enabled", havingValue = "true")
public class ChatWorkspaceFileService implements ChatWorkspaceStore {

    static final String AGENT_ID = "general";
    static final String SEGMENT = "workspace";
    /** 单文件上限：Mongo 文档 16MB，base64 膨胀 4/3，留出元数据余量。 */
    static final int MAX_FILE_BYTES = 8 * 1024 * 1024;
    private static final int MAX_FILES = 2000;

    private final MongoBaseStore store;

    private List<String> ns(Long userId) {
        String uid = userId == null || !StringUtils.hasText(String.valueOf(userId)) ? "_anon" : String.valueOf(userId);
        return List.of("agents", AGENT_ID, "users", uid, SEGMENT);
    }

    @Override
    public List<Entry> list(Long userId) {
        List<Entry> entries = new ArrayList<>();
        int offset = 0;
        while (true) {
            List<StoreItem> page = store.search(ns(userId), 200, offset);
            if (page.isEmpty()) {
                break;
            }
            for (StoreItem item : page) {
                entries.add(new Entry(item.key(), decode(item.value()).length,
                        String.valueOf(item.value().getOrDefault("modified_at", ""))));
            }
            offset += page.size();
            if (offset > MAX_FILES) {
                log.warn("用户工作区文件数超过 {}，列表截断：user={}", MAX_FILES, userId);
                break;
            }
        }
        return entries;
    }

    @Override
    public byte[] readFile(Long userId, String path) {
        StoreItem item = store.get(ns(userId), normalize(path));
        if (item == null) {
            throw new BizException("工作区文件不存在：" + path);
        }
        return decode(item.value());
    }

    @Override
    public void writeFile(Long userId, String path, byte[] data) {
        String normalized = normalize(path);
        if (!StringUtils.hasText(normalized) || normalized.contains("../")) {
            throw new BizException("工作区路径无效：" + path);
        }
        if (data != null && data.length > MAX_FILE_BYTES) {
            throw new BizException("工作区单文件不能超过 8 MB：" + path);
        }
        store.put(ns(userId), normalized, payload(data));
    }

    @Override
    public void delete(Long userId, String path) {
        store.delete(ns(userId), normalize(path));
    }

    private static Map<String, Object> payload(byte[] data) {
        byte[] safe = data == null ? new byte[0] : data;
        boolean binary = isBinary(safe);
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("content", binary
                ? Base64.getEncoder().encodeToString(safe)
                : new String(safe, StandardCharsets.UTF_8));
        value.put("encoding", binary ? "base64" : "text");
        String now = Instant.now().toString();
        value.put("created_at", now);
        value.put("modified_at", now);
        return value;
    }

    private static byte[] decode(Map<String, Object> value) {
        Object content = value == null ? null : value.get("content");
        if (content == null) {
            return new byte[0];
        }
        if ("base64".equals(value.get("encoding"))) {
            return Base64.getDecoder().decode(String.valueOf(content));
        }
        return String.valueOf(content).getBytes(StandardCharsets.UTF_8);
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

    private static String normalize(String path) {
        String value = path == null ? "" : path.replace('\\', '/').trim();
        while (value.startsWith("/")) {
            value = value.substring(1);
        }
        return value;
    }
}
