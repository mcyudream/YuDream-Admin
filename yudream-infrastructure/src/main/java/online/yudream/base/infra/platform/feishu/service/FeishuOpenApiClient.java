package online.yudream.base.infra.platform.feishu.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import online.yudream.base.application.common.net.OutboundNetworkPolicy;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.feishu.exception.FeishuApiException;
import online.yudream.base.domain.platform.feishu.service.FeishuCloudDocGateway;
import online.yudream.base.domain.platform.feishu.valobj.FeishuBlockWriteResult;
import online.yudream.base.domain.platform.feishu.valobj.FeishuCloudDoc;
import online.yudream.base.domain.platform.feishu.valobj.FeishuDocPage;
import online.yudream.base.domain.platform.feishu.valobj.FeishuDocumentBlock;
import online.yudream.base.domain.platform.feishu.valobj.FeishuDocumentMeta;
import online.yudream.base.domain.platform.feishu.valobj.FeishuWikiNode;
import online.yudream.base.domain.platform.feishu.valobj.FeishuWikiSpace;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * 飞书开放平台通用认证与云文档客户端（本工作项唯一在范围的 API 模块）。
 * <p>
 * 认证：{@code POST /open-apis/auth/v3/tenant_access_token/internal}（body {@code app_id}/{@code app_secret}），
 * 令牌按 {@code expire} 秒数缓存并在到期前 2 分钟刷新，同一实例内单飞获取。
 * <p>
 * 连接生命周期：{@link #reconfigure} 只保存配置、重建 HttpClient 并清空令牌缓存，不发起任何网络请求；
 * 真实连接只在能力两道闸门通过且业务调用或显式测试动作触发时建立，{@link #close} 清空配置与令牌。
 * <p>
 * 凭据外发边界：baseUrl 只允许官方飞书主机或部署配置显式声明的主机（{@code yudream.platform.feishu.allowed-hosts}），
 * 且强制 https（仅本机回环地址在部署放开内网出站时可用 http）。能力配置管理员因此无法把 app_id/app_secret
 * 指向任意地址；错误文案统一对 App Secret 与 tenant_access_token 做脱敏。
 * <p>
 * 保真：块与元素 JSON 一律按飞书原文透传（{@code raw} 为原始 JSON 文本，写回时按 JSON 节点提交），
 * 不做「块 -> 文本 -> 块」的有损往返。
 */
@Service
public class FeishuOpenApiClient implements FeishuCloudDocGateway {

    public static final String BASE_URL_CONFIG_KEY = "baseUrl";
    public static final String APP_ID_CONFIG_KEY = "appId";
    public static final String APP_SECRET_CONFIG_KEY = "appSecret";
    public static final String CONNECT_TIMEOUT_CONFIG_KEY = "connectTimeoutMillis";
    public static final String READ_TIMEOUT_CONFIG_KEY = "readTimeoutMillis";

    public static final String DEFAULT_BASE_URL = "https://open.feishu.cn";

    /** 部署级受信主机清单（逗号分隔）；不放在能力配置里，避免同一权限同时改地址与放行主机。 */
    public static final String ALLOWED_HOSTS_PROPERTY = "yudream.platform.feishu.allowed-hosts";

    /** 官方开放平台主机：飞书（中国）与 Lark（国际）。 */
    private static final Set<String> OFFICIAL_HOSTS = Set.of("open.feishu.cn", "open.larksuite.com");

    /** 仅部署放开内网出站时可用的本机回环主机（本地联调/网关）。 */
    private static final Set<String> LOOPBACK_HOSTS = Set.of("127.0.0.1", "localhost", "[::1]", "::1");

    private static final int DEFAULT_CONNECT_TIMEOUT_MILLIS = 5_000;
    private static final int DEFAULT_READ_TIMEOUT_MILLIS = 15_000;
    private static final Duration TOKEN_REFRESH_MARGIN = Duration.ofMinutes(2);

    private static final int MAX_DRIVE_PAGE_SIZE = 200;
    private static final int MAX_WIKI_PAGE_SIZE = 50;
    private static final int MAX_WIKI_NODE_PAGE_SIZE = 50;
    private static final int MAX_BLOCK_PAGE_SIZE = 500;

    private static final String AUTH_PATH = "/open-apis/auth/v3/tenant_access_token/internal";
    private static final String DRIVE_FILES_PATH = "/open-apis/drive/v1/files";
    private static final String WIKI_SPACES_PATH = "/open-apis/wiki/v2/spaces";
    private static final String WIKI_NODES_SUFFIX = "/nodes";
    private static final String DOCX_DOCUMENT_PATH = "/open-apis/docx/v1/documents/";
    private static final String DOCX_BLOCK_PATH = "/blocks";
    private static final String DOCX_CHILDREN_PATH = "/children";
    private static final String DOCX_CHILDREN_BATCH_DELETE_PATH = "/children/batch_delete";

    /** 文档/块标识只允许 URL 安全字符，避免路径拼接注入。 */
    private static final Pattern SAFE_ID = Pattern.compile("[A-Za-z0-9_-]{1,128}");

    private static final String REDACTED = "***";

    private final ObjectMapper objectMapper;
    private final OutboundNetworkPolicy outboundNetworkPolicy;
    private final Set<String> allowedHosts;

    public FeishuOpenApiClient(ObjectMapper objectMapper, OutboundNetworkPolicy outboundNetworkPolicy) {
        this(objectMapper, outboundNetworkPolicy, "");
    }

    @Autowired
    public FeishuOpenApiClient(ObjectMapper objectMapper, OutboundNetworkPolicy outboundNetworkPolicy,
                               @Value("${" + ALLOWED_HOSTS_PROPERTY + ":}") String configuredHosts) {
        this.objectMapper = objectMapper;
        this.outboundNetworkPolicy = outboundNetworkPolicy;
        this.allowedHosts = allowedHosts(configuredHosts);
    }

    private volatile Runtime runtime;

    /** 保存能力配置；只做本地校验（主机/协议白名单），不校验远端连通性、不获取令牌。 */
    public void reconfigure(Map<String, String> config) {
        Map<String, String> values = config == null ? Map.of() : config;
        String baseUrl = StringUtils.hasText(values.get(BASE_URL_CONFIG_KEY))
                ? values.get(BASE_URL_CONFIG_KEY).trim()
                : DEFAULT_BASE_URL;
        while (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }
        if (baseUrl.isBlank()) {
            throw new BizException("飞书开放平台地址不能为空");
        }
        URI baseUri;
        try {
            baseUri = URI.create(baseUrl);
        }
        catch (IllegalArgumentException exception) {
            throw new BizException("飞书开放平台地址格式不正确");
        }
        assertAllowedBaseUrl(baseUri);
        int connectTimeout = positiveInt(values.get(CONNECT_TIMEOUT_CONFIG_KEY), DEFAULT_CONNECT_TIMEOUT_MILLIS);
        int readTimeout = positiveInt(values.get(READ_TIMEOUT_CONFIG_KEY), DEFAULT_READ_TIMEOUT_MILLIS);
        HttpClient http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(connectTimeout))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        this.runtime = new Runtime(baseUri,
                text(values.get(APP_ID_CONFIG_KEY)),
                text(values.get(APP_SECRET_CONFIG_KEY)),
                Duration.ofMillis(readTimeout),
                http);
    }

    /**
     * 凭据外发白名单校验：协议必须 https（仅回环地址在放开内网出站时可 http），
     * 主机必须是官方主机或部署配置声明的主机，且不接受用户凭据、查询串与片段。
     */
    private void assertAllowedBaseUrl(URI baseUri) {
        String scheme = baseUri.getScheme() == null ? "" : baseUri.getScheme().toLowerCase(Locale.ROOT);
        String host = baseUri.getHost() == null ? "" : baseUri.getHost().toLowerCase(Locale.ROOT);
        if (host.isBlank()) {
            throw new BizException("飞书开放平台地址缺少主机名");
        }
        if (baseUri.getUserInfo() != null) {
            throw new BizException("飞书开放平台地址不允许携带用户凭据信息");
        }
        if (baseUri.getRawQuery() != null || baseUri.getRawFragment() != null) {
            throw new BizException("飞书开放平台地址不允许携带查询串或片段");
        }
        boolean loopback = LOOPBACK_HOSTS.contains(host) && outboundNetworkPolicy.allowPrivateNetwork();
        if (!"https".equals(scheme) && !("http".equals(scheme) && loopback)) {
            throw new BizException("飞书开放平台地址必须使用 https（仅本机回环地址在放开内网出站时可用 http）");
        }
        if (!allowedHosts.contains(host) && !loopback) {
            throw new BizException("飞书开放平台地址主机不在受信清单：" + host
                    + "；仅允许 " + String.join("、", OFFICIAL_HOSTS.stream().sorted().toList())
                    + "，自建网关需由部署配置 " + ALLOWED_HOSTS_PROPERTY + " 显式声明");
        }
    }

    private static Set<String> allowedHosts(String configuredHosts) {
        Set<String> hosts = new java.util.TreeSet<>(OFFICIAL_HOSTS);
        if (StringUtils.hasText(configuredHosts)) {
            for (String host : configuredHosts.split(",")) {
                String normalized = host.trim().toLowerCase(Locale.ROOT);
                if (!normalized.isEmpty()) {
                    hosts.add(normalized);
                }
            }
        }
        return Set.copyOf(hosts);
    }


    public void close() {
        this.runtime = null;
    }

    public boolean configured() {
        Runtime current = this.runtime;
        return current != null && StringUtils.hasText(current.appId()) && StringUtils.hasText(current.appSecret());
    }

    @Override
    public String probe() {
        Runtime current = requireRuntime();
        String token = tenantToken(current, true);
        return "飞书 tenant_access_token 获取成功（长度 " + token.length() + "，已缓存并在到期前自动刷新）";
    }

    @Override
    public FeishuDocPage<FeishuCloudDoc> listDriveFiles(String folderToken, String pageToken, int pageSize) {
        Runtime current = requireRuntime();
        Map<String, String> query = new LinkedHashMap<>();
        if (StringUtils.hasText(folderToken)) {
            query.put("folder_token", folderToken.trim());
        }
        query.put("page_size", String.valueOf(clamp(pageSize, MAX_DRIVE_PAGE_SIZE)));
        if (StringUtils.hasText(pageToken)) {
            query.put("page_token", pageToken.trim());
        }
        JsonNode data = dataOf(exchange(current, "GET", DRIVE_FILES_PATH, query, null, "列出云空间文件"));
        List<FeishuCloudDoc> files = new ArrayList<>();
        for (JsonNode node : data.path("files")) {
            JsonNode shortcut = node.path("shortcut_info");
            files.add(new FeishuCloudDoc(
                    textOf(node, "token"),
                    textOf(node, "name"),
                    textOf(node, "type"),
                    textOf(node, "parent_token"),
                    textOf(node, "url"),
                    textOf(shortcut, "target_type"),
                    textOf(shortcut, "target_token"),
                    textOf(node, "created_time"),
                    textOf(node, "modified_time"),
                    textOf(node, "owner_id")));
        }
        return new FeishuDocPage<>(files, textOf(data, "next_page_token"), data.path("has_more").asBoolean(false));
    }

    @Override
    public FeishuDocPage<FeishuWikiSpace> listWikiSpaces(String pageToken, int pageSize) {
        Runtime current = requireRuntime();
        Map<String, String> query = new LinkedHashMap<>();
        query.put("page_size", String.valueOf(clamp(pageSize, MAX_WIKI_PAGE_SIZE)));
        if (StringUtils.hasText(pageToken)) {
            query.put("page_token", pageToken.trim());
        }
        JsonNode data = dataOf(exchange(current, "GET", WIKI_SPACES_PATH, query, null, "列出知识库空间"));
        List<FeishuWikiSpace> spaces = new ArrayList<>();
        for (JsonNode node : data.path("items")) {
            spaces.add(new FeishuWikiSpace(
                    textOf(node, "space_id"),
                    textOf(node, "name"),
                    textOf(node, "description"),
                    textOf(node, "space_type"),
                    textOf(node, "visibility"),
                    textOf(node, "open_sharing")));
        }
        return new FeishuDocPage<>(spaces, textOf(data, "page_token"), data.path("has_more").asBoolean(false));
    }

    @Override
    public FeishuDocPage<FeishuWikiNode> listWikiNodes(String spaceId, String parentNodeToken, String pageToken, int pageSize) {
        Runtime current = requireRuntime();
        Map<String, String> query = new LinkedHashMap<>();
        if (StringUtils.hasText(parentNodeToken)) {
            query.put("parent_node_token", parentNodeToken.trim());
        }
        query.put("page_size", String.valueOf(clamp(pageSize, MAX_WIKI_NODE_PAGE_SIZE)));
        if (StringUtils.hasText(pageToken)) {
            query.put("page_token", pageToken.trim());
        }
        JsonNode data = dataOf(exchange(current, "GET", wikiNodesPath(spaceId), query, null, "列出知识库节点"));
        List<FeishuWikiNode> nodes = new ArrayList<>();
        for (JsonNode node : data.path("items")) {
            nodes.add(new FeishuWikiNode(
                    textOf(node, "node_token"),
                    textOf(node, "obj_token"),
                    textOf(node, "obj_type"),
                    textOf(node, "title"),
                    node.path("has_child").asBoolean(false),
                    textOf(node, "parent_node_token"),
                    textOf(node, "node_type"),
                    textOf(node, "origin_node_token"),
                    textOf(node, "origin_space_id"),
                    textOf(node, "space_id")));
        }
        return new FeishuDocPage<>(nodes, textOf(data, "page_token"), data.path("has_more").asBoolean(false));
    }

    @Override
    public FeishuDocumentMeta documentMeta(String documentId) {
        Runtime current = requireRuntime();
        JsonNode data = dataOf(exchange(current, "GET", documentPath(documentId), Map.of(), null, "获取文档元信息"));
        JsonNode document = data.path("document");
        return new FeishuDocumentMeta(
                textOf(document, "document_id"),
                textOf(document, "revision_id"),
                textOf(document, "title"));
    }

    @Override
    public String documentRawContent(String documentId, Integer lang) {
        Runtime current = requireRuntime();
        Map<String, String> query = new LinkedHashMap<>();
        if (lang != null) {
            query.put("lang", String.valueOf(lang));
        }
        JsonNode data = dataOf(exchange(current, "GET", documentPath(documentId) + "/raw_content", query, null, "获取文档纯文本"));
        return data.path("content").asText("");
    }

    @Override
    public FeishuDocPage<FeishuDocumentBlock> documentBlocks(String documentId, String pageToken, int pageSize) {
        Runtime current = requireRuntime();
        Map<String, String> query = new LinkedHashMap<>();
        query.put("page_size", String.valueOf(clamp(pageSize, MAX_BLOCK_PAGE_SIZE)));
        if (StringUtils.hasText(pageToken)) {
            query.put("page_token", pageToken.trim());
        }
        JsonNode data = dataOf(exchange(current, "GET", documentPath(documentId) + DOCX_BLOCK_PATH, query, null, "获取文档块"));
        List<FeishuDocumentBlock> blocks = new ArrayList<>();
        for (JsonNode node : data.path("items")) {
            blocks.add(blockOf(node));
        }
        return new FeishuDocPage<>(blocks, textOf(data, "page_token"), data.path("has_more").asBoolean(false));
    }

    @Override
    public FeishuBlockWriteResult appendDocumentBlocks(String documentId, String parentBlockId, List<String> childrenJson, Integer index) {
        Runtime current = requireRuntime();
        if (childrenJson == null || childrenJson.isEmpty()) {
            throw new BizException("追加子块必须提供至少一个块内容");
        }
        ObjectNode body = objectMapper.createObjectNode();
        ArrayNode children = body.putArray("children");
        for (String json : childrenJson) {
            children.add(parseBlockJson(json));
        }
        if (index != null) {
            if (index < 0) {
                throw new BizException("子块插入位置不能为负数");
            }
            body.put("index", index);
        }
        JsonNode data = dataOf(exchange(current, "POST",
                blockChildrenPath(documentId, parentBlockId) + DOCX_CHILDREN_PATH,
                Map.of(), body, "追加文档子块"));
        List<String> blockIds = new ArrayList<>();
        List<String> blocksRaw = new ArrayList<>();
        for (JsonNode node : data.path("children")) {
            blockIds.add(textOf(node, "block_id"));
            blocksRaw.add(node.toString());
        }
        return new FeishuBlockWriteResult(documentId, textOf(data, "document_revision_id"), blockIds, blocksRaw);
    }

    @Override
    public void deleteDocumentBlocks(String documentId, String parentBlockId, int startIndex, int endIndex) {
        Runtime current = requireRuntime();
        // 飞书 batch_delete 的 start_index/end_index 为左闭右开区间，且要求 start_index < end_index，
        // 因此宿主边界契约与之保持一致，原样透传，不做下标换算（避免多删一个块）。
        if (startIndex < 0 || endIndex <= startIndex) {
            throw new BizException("删除子块区间必须满足 startIndex >= 0 且 startIndex < endIndex（飞书为左闭右开区间）");
        }
        ObjectNode body = objectMapper.createObjectNode();
        body.put("start_index", startIndex);
        body.put("end_index", endIndex);
        exchange(current, "DELETE",
                blockChildrenPath(documentId, parentBlockId) + DOCX_CHILDREN_BATCH_DELETE_PATH,
                Map.of(), body, "删除文档子块");
    }

    @Override
    public FeishuBlockWriteResult updateBlockElements(String documentId, String blockId, List<String> elementsJson, Long documentRevisionId) {
        Runtime current = requireRuntime();
        if (elementsJson == null || elementsJson.isEmpty()) {
            throw new BizException("更新文本块必须提供至少一个文本元素");
        }
        ObjectNode body = objectMapper.createObjectNode();
        ArrayNode elements = body.putObject("update_text_elements").putArray("elements");
        for (String json : elementsJson) {
            elements.add(parseElementJson(json));
        }
        Map<String, String> query = new LinkedHashMap<>();
        if (documentRevisionId != null) {
            if (documentRevisionId < -1) {
                throw new BizException("文档版本号不合法");
            }
            query.put("document_revision_id", String.valueOf(documentRevisionId));
        }
        query.put("client_token", UUID.randomUUID().toString());
        JsonNode data = dataOf(exchange(current, "PATCH",
                documentPath(documentId) + DOCX_BLOCK_PATH + "/" + safeId(blockId, "块"),
                query, body, "更新文本块"));
        JsonNode block = data.path("block");
        return new FeishuBlockWriteResult(documentId, textOf(data, "document_revision_id"),
                List.of(textOf(block, "block_id")), List.of(block.toString()));
    }

    private JsonNode parseBlockJson(String json) {
        if (!StringUtils.hasText(json)) {
            throw new BizException("子块内容不能为空");
        }
        try {
            JsonNode node = objectMapper.readTree(json);
            if (!node.isObject()) {
                throw new BizException("子块内容必须是 JSON 对象");
            }
            return node;
        }
        catch (BizException exception) {
            throw exception;
        }
        catch (IOException exception) {
            throw new BizException("子块内容不是合法 JSON");
        }
    }

    private JsonNode parseElementJson(String json) {
        if (!StringUtils.hasText(json)) {
            throw new BizException("文本元素不能为空");
        }
        try {
            JsonNode node = objectMapper.readTree(json);
            if (!node.isObject()) {
                throw new BizException("文本元素必须是 JSON 对象");
            }
            return node;
        }
        catch (BizException exception) {
            throw exception;
        }
        catch (IOException exception) {
            throw new BizException("文本元素不是合法 JSON");
        }
    }

    private FeishuDocumentBlock blockOf(JsonNode node) {
        List<String> children = new ArrayList<>();
        for (JsonNode child : node.path("children")) {
            children.add(child.asText());
        }
        Integer blockType = node.hasNonNull("block_type") ? node.get("block_type").asInt() : null;
        return new FeishuDocumentBlock(textOf(node, "block_id"), textOf(node, "parent_id"), blockType, children, node.toString());
    }

    private JsonNode exchange(Runtime current, String method, String path, Map<String, String> query,
                             JsonNode body, String purpose) {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(current.uri(path, query, outboundNetworkPolicy))
                .timeout(current.readTimeout());
        if (body == null) {
            builder.method(method, HttpRequest.BodyPublishers.noBody());
        } else {
            builder.header("Content-Type", "application/json; charset=utf-8")
                    .method(method, HttpRequest.BodyPublishers.ofString(serialize(body), StandardCharsets.UTF_8));
        }
        if (!AUTH_PATH.equals(path)) {
            builder.header("Authorization", "Bearer " + tenantToken(current, false));
        }
        HttpResponse<String> response;
        try {
            response = current.http().send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        }
        catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new BizException("飞书接口调用被中断");
        }
        catch (IOException exception) {
            throw new BizException("飞书接口调用失败：" + redact(current, readable(exception)));
        }
        return assertSuccess(current, response, purpose);
    }

    /**
     * 飞书错误映射：HTTP 429 与错误码 99991400 是限频（飞书明确未执行，额度可退）；
     * 401/403 是鉴权/权限拒绝（同样未执行，额度可退）；其余错误码与网络异常保守计数。
     * 所有文案经脱敏，不泄漏 App Secret 与 tenant_access_token。
     */
    private JsonNode assertSuccess(Runtime current, HttpResponse<String> response, String purpose) {
        int status = response.statusCode();
        JsonNode root = parseBody(response.body());
        Integer code = root != null && root.hasNonNull("code") ? root.get("code").asInt() : null;
        if (status >= 200 && status < 300 && (code == null || code == 0)) {
            return root == null ? objectMapper.createObjectNode() : root;
        }
        String detail = redact(current, detailOf(code, root));
        if (status == 429 || (code != null && code == 99991400)) {
            throw new FeishuApiException("飞书接口限频（" + purpose + "），请稍后重试" + detail, code, status == 429 ? 429 : status);
        }
        throw new FeishuApiException("飞书接口调用失败（" + purpose + "）：HTTP " + status + detail, code, status);
    }

    /** 上游文案脱敏：凭据与令牌即使被上游回显也不进入异常/日志。 */
    private String redact(Runtime runtime, String message) {
        if (runtime == null || !StringUtils.hasText(message)) {
            return message;
        }
        String redacted = message;
        String secret = runtime.appSecret();
        if (StringUtils.hasText(secret)) {
            redacted = redacted.replace(secret, REDACTED);
        }
        String token = runtime.rawToken();
        if (StringUtils.hasText(token)) {
            redacted = redacted.replace(token, REDACTED);
        }
        return redacted;
    }

    private JsonNode parseBody(String body) {
        if (!StringUtils.hasText(body)) {
            return null;
        }
        try {
            return objectMapper.readTree(body);
        }
        catch (IOException ignored) {
            return null;
        }
    }

    private String detailOf(Integer code, JsonNode root) {
        String msg = root == null ? "" : root.path("msg").asText("");
        StringBuilder detail = new StringBuilder();
        if (code != null) {
            detail.append("（code=").append(code);
        }
        if (StringUtils.hasText(msg)) {
            detail.append(detail.isEmpty() ? "（" : "，").append(msg);
        }
        if (!detail.isEmpty()) {
            detail.append("）");
        }
        return detail.toString();
    }

    private JsonNode dataOf(JsonNode root) {
        JsonNode data = root.path("data");
        return data.isObject() ? data : objectMapper.createObjectNode();
    }

    private String tenantToken(Runtime current, boolean forceRefresh) {
        String cached = current.cachedToken();
        if (cached != null && !forceRefresh) {
            return cached;
        }
        synchronized (current.tokenLock()) {
            cached = current.cachedToken();
            if (cached != null && !forceRefresh) {
                return cached;
            }
            ObjectNode body = objectMapper.createObjectNode()
                    .put("app_id", current.appId())
                    .put("app_secret", current.appSecret());
            JsonNode root = exchange(current, "POST", AUTH_PATH, Map.of(), body, "获取 tenant_access_token");
            String token = textOf(root, "tenant_access_token");
            if (!StringUtils.hasText(token)) {
                throw new FeishuApiException("飞书未返回 tenant_access_token，请检查应用凭证与权限配置", null, 200);
            }
            long expireSeconds = root.path("expire").asLong(0);
            current.cacheToken(token, expireSeconds > 0 ? expireSeconds : 7_200);
            return token;
        }
    }

    private Runtime requireRuntime() {
        Runtime current = this.runtime;
        if (current == null) {
            throw new BizException("飞书能力未启用或未配置");
        }
        if (!StringUtils.hasText(current.appId()) || !StringUtils.hasText(current.appSecret())) {
            throw new BizException("飞书应用凭证未配置，请先在平台能力中填写 App ID 与 App Secret");
        }
        return current;
    }

    private String documentPath(String documentId) {
        return DOCX_DOCUMENT_PATH + safeId(documentId, "文档");
    }

    private String wikiNodesPath(String spaceId) {
        return WIKI_SPACES_PATH + "/" + safeId(spaceId, "知识空间") + WIKI_NODES_SUFFIX;
    }

    private String blockChildrenPath(String documentId, String parentBlockId) {
        return documentPath(documentId) + DOCX_BLOCK_PATH + "/" + safeId(parentBlockId, "父块");
    }

    private String safeId(String value, String label) {
        if (!StringUtils.hasText(value) || !SAFE_ID.matcher(value.trim()).matches()) {
            throw new BizException("飞书" + label + "标识不合法");
        }
        return value.trim();
    }

    private static int clamp(int pageSize, int max) {
        if (pageSize <= 0) {
            return Math.min(20, max);
        }
        return Math.min(pageSize, max);
    }

    private static int positiveInt(String raw, int fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            int parsed = Integer.parseInt(raw.trim());
            return parsed > 0 ? parsed : fallback;
        }
        catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static String text(String value) {
        return value == null ? "" : value.trim();
    }

    private static String textOf(JsonNode node, String field) {
        if (node == null) {
            return null;
        }
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? null : value.asText();
    }

    private String serialize(JsonNode body) {
        try {
            return objectMapper.writeValueAsString(body);
        }
        catch (IOException exception) {
            throw new BizException("飞书请求体序列化失败");
        }
    }

    private static String readable(IOException exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank() ? exception.getClass().getSimpleName() : message;
    }

    /**
     * 单次配置的运行期状态：基础地址、凭证、超时、HttpClient 与令牌缓存。
     * 每次 {@code reconfigure} 生成新实例，令牌缓存随之失效，配置切换不会复用旧凭证的令牌。
     */
    private static final class Runtime {

        private final URI baseUri;
        private final String appId;
        private final String appSecret;
        private final Duration readTimeout;
        private final HttpClient http;
        private final Object tokenLock = new Object();

        private volatile String token;
        private volatile Instant tokenExpiresAt;

        private Runtime(URI baseUri, String appId, String appSecret, Duration readTimeout, HttpClient http) {
            this.baseUri = baseUri;
            this.appId = appId;
            this.appSecret = appSecret;
            this.readTimeout = readTimeout;
            this.http = http;
        }

        private String appId() {
            return appId;
        }

        private String appSecret() {
            return appSecret;
        }

        private Duration readTimeout() {
            return readTimeout;
        }

        private HttpClient http() {
            return http;
        }

        private Object tokenLock() {
            return tokenLock;
        }

        private String cachedToken() {
            String current = token;
            Instant expiresAt = tokenExpiresAt;
            if (current == null || expiresAt == null) {
                return null;
            }
            return Instant.now().isBefore(expiresAt.minus(TOKEN_REFRESH_MARGIN)) ? current : null;
        }

        /** 原始令牌（忽略有效期），仅用于错误文案脱敏。 */
        private String rawToken() {
            return token;
        }

        private void cacheToken(String value, long expireSeconds) {
            this.token = value;
            this.tokenExpiresAt = Instant.now().plusSeconds(expireSeconds);
        }

        /** 目标地址按部署级出站策略校验（协议 + 主机解析），每次拼接路径前执行。 */
        private URI uri(String path, Map<String, String> query, OutboundNetworkPolicy policy) {
            StringBuilder url = new StringBuilder(policy.validate(baseUri.toString(), "飞书开放平台").toString())
                    .append(path);
            if (query != null && !query.isEmpty()) {
                boolean first = true;
                for (Map.Entry<String, String> entry : query.entrySet()) {
                    if (entry.getValue() == null || entry.getValue().isBlank()) {
                        continue;
                    }
                    url.append(first ? '?' : '&');
                    first = false;
                    url.append(URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8))
                            .append('=')
                            .append(URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8));
                }
            }
            return URI.create(url.toString());
        }
    }
}
