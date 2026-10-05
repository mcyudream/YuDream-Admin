package online.yudream.base.infra.platform.ai.runtime;

import io.agentscope.harness.agent.filesystem.remote.store.StoreItem;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import online.yudream.base.domain.common.exception.BizException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * CMS 静态站点工作区文件 IO：与 Harness 远程文件系统共享同一 MongoBaseStore 与
 * 命名空间公式（agents/cms-builder/users/<uid> + 路由段 cms），值为 {content, encoding}
 * 载荷；发布 = 工作区到发布命名空间的整树快照，公开端只读发布区、草稿不外泄。
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "yudream.platform.capabilities.ai", name = "enabled", havingValue = "true")
public class CmsWorkspaceService implements online.yudream.base.application.platform.cms.service.CmsWorkspaceStore {

    static final String AGENT_ID = "cms-builder";
    static final String PREFIX_SEGMENT = "cms";
    static final String PUBLISHED_SEGMENT = "cms-published";
    static final List<String> SITE_PUBLISHED_NS = List.of("cms", "published", "site");

    private final MongoBaseStore store;

    private List<String> ns(Long userId, String segment) {
        return List.of("agents", AGENT_ID, "users",
                StringUtils.hasText(String.valueOf(userId)) && userId != null ? String.valueOf(userId) : "_anon",
                segment);
    }

    public byte[] readFile(Long userId, String path) {
        StoreItem item = store.get(ns(userId, PREFIX_SEGMENT), normalize(path));
        if (item == null) {
            throw new BizException("工作区文件不存在：" + path);
        }
        return decode(item.value());
    }

    public void writeFile(Long userId, String path, byte[] data) {
        String normalized = normalize(path);
        if (!StringUtils.hasText(normalized) || normalized.contains("../")) {
            throw new BizException("工作区路径无效：" + path);
        }
        boolean binary = isBinary(data);
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("content", binary
                ? Base64.getEncoder().encodeToString(data)
                : new String(data, java.nio.charset.StandardCharsets.UTF_8));
        value.put("encoding", binary ? "base64" : "text");
        String now = Instant.now().toString();
        value.put("created_at", now);
        value.put("modified_at", now);
        store.put(ns(userId, PREFIX_SEGMENT), normalized, value);
    }

    public List<WorkspaceEntry> list(Long userId) {
        List<WorkspaceEntry> entries = new ArrayList<>();
        int offset = 0;
        while (true) {
            List<StoreItem> page = store.search(ns(userId, PREFIX_SEGMENT), offset, 200);
            if (page.isEmpty()) {
                break;
            }
            for (StoreItem item : page) {
                byte[] data = decode(item.value());
                entries.add(new WorkspaceEntry(item.key(), data.length,
                        String.valueOf(item.value().getOrDefault("modified_at", ""))));
            }
            offset += page.size();
            if (offset > 5000) {
                log.warn("CMS 工作区文件数超过 5000，列表截断：user={}", userId);
                break;
            }
        }
        return entries;
    }

    /** 发布快照：工作区整树复制到发布命名空间与站点级命名空间（公开端只读站点区）。 */
    public int publish(Long userId) {
        List<WorkspaceEntry> files = list(userId);
        if (files.isEmpty()) {
            throw new BizException("工作区为空，无可发布内容");
        }
        int count = 0;
        for (WorkspaceEntry entry : files) {
            byte[] data = readFile(userId, entry.path());
            writeSnapshot(ns(userId, PUBLISHED_SEGMENT), entry.path(), data);
            writeSnapshot(SITE_PUBLISHED_NS, entry.path(), data);
            count++;
        }
        log.info("CMS 工作区已发布快照：user={} files={}", userId, count);
        return count;
    }

    private void writeSnapshot(List<String> namespace, String path, byte[] data) {
        Map<String, Object> value = new LinkedHashMap<>();
        boolean binary = isBinary(data);
        value.put("content", binary
                ? Base64.getEncoder().encodeToString(data)
                : new String(data, java.nio.charset.StandardCharsets.UTF_8));
        value.put("encoding", binary ? "base64" : "text");
        value.put("created_at", Instant.now().toString());
        value.put("modified_at", Instant.now().toString());
        store.put(namespace, path, value);
    }

    @Override
    public byte[] readPublished(Long userId, String path) {
        StoreItem item = store.get(ns(userId, PUBLISHED_SEGMENT), normalize(path));
        return item == null ? null : decode(item.value());
    }

    /** 公开端读取站点发布区文件（匿名，最后发布者胜）。 */
    @Override
    public byte[] readPublishedSite(String path) {
        StoreItem item = store.get(SITE_PUBLISHED_NS, normalize(path));
        return item == null ? null : decode(item.value());
    }

    private static byte[] decode(Map<String, Object> value) {
        Object content = value == null ? null : value.get("content");
        if (content == null) {
            return new byte[0];
        }
        if ("base64".equals(value.get("encoding"))) {
            return Base64.getDecoder().decode(String.valueOf(content));
        }
        return String.valueOf(content).getBytes(java.nio.charset.StandardCharsets.UTF_8);
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
