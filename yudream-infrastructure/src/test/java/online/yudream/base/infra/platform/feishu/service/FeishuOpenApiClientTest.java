package online.yudream.base.infra.platform.feishu.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import online.yudream.base.application.common.net.OutboundNetworkPolicy;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.feishu.exception.FeishuApiException;
import online.yudream.base.domain.platform.feishu.valobj.FeishuBlockWriteResult;
import online.yudream.base.domain.platform.feishu.valobj.FeishuCloudDoc;
import online.yudream.base.domain.platform.feishu.valobj.FeishuDocPage;
import online.yudream.base.domain.platform.feishu.valobj.FeishuDocumentBlock;
import online.yudream.base.domain.platform.feishu.valobj.FeishuDocumentMeta;
import online.yudream.base.domain.platform.feishu.valobj.FeishuWikiNode;
import online.yudream.base.domain.platform.feishu.valobj.FeishuWikiSpace;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 飞书客户端：tenant_access_token 缓存与单飞、错误映射（限频可退）、块 JSON 保真、路径与请求体形态。
 * <p>
 * 用本机 stub HTTP 服务端到端跑真实 HttpClient 与 JSON 解析，不打桩 HTTP 层。
 */
class FeishuOpenApiClientTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private HttpServer server;
    private final List<RecordedRequest> requests = new ArrayList<>();
    private FeishuOpenApiClient client;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", this::handle);
        server.setExecutor(java.util.concurrent.Executors.newFixedThreadPool(4));
        server.start();
        client = new FeishuOpenApiClient(MAPPER, new OutboundNetworkPolicy(true));
        client.reconfigure(Map.of(
                FeishuOpenApiClient.BASE_URL_CONFIG_KEY, "http://127.0.0.1:" + server.getAddress().getPort(),
                FeishuOpenApiClient.APP_ID_CONFIG_KEY, "cli_test",
                FeishuOpenApiClient.APP_SECRET_CONFIG_KEY, "secret_test"));
    }

    @AfterEach
    void stopServer() {
        client.close();
        server.stop(0);
    }

    @Test
    void reusesTenantTokenAcrossCallsAndSendsBearerHeader() {
        client.listDriveFiles(null, null, 20);
        client.listWikiSpaces(null, 20);

        assertEquals(1, requests.stream().filter(request -> request.path().endsWith("/tenant_access_token/internal")).count());
        RecordedRequest drive = requests.stream().filter(request -> request.path().endsWith("/drive/v1/files")).findFirst().orElseThrow();
        assertEquals("Bearer token-1", drive.authorization());
        JsonNode body = readJson(requests.get(0).body());
        assertEquals("cli_test", body.path("app_id").asText());
        assertEquals("secret_test", body.path("app_secret").asText());
    }

    @Test
    void mapsDriveFilesWikiSpacesAndDocumentMetaWithoutLosingIds() {
        FeishuDocPage<FeishuCloudDoc> files = client.listDriveFiles("fldr1", "np0", 9999);
        assertEquals(1, files.items().size());
        FeishuCloudDoc file = files.items().get(0);
        assertEquals("fldc1234567890123456789012", file.token());
        assertEquals("设计文档", file.name());
        assertEquals("docx", file.type());
        assertEquals("fldr0", file.parentToken());
        assertEquals("1728000000", file.createdTime());
        assertEquals("1728000001", file.modifiedTime());
        assertEquals("ou_owner", file.ownerId());
        assertEquals("np1", files.nextPageToken());
        assertTrue(files.hasMore());
        // 驱动列表分页上限 200
        assertEquals("200", requests.stream().filter(request -> request.path().endsWith("/drive/v1/files")).findFirst()
                .orElseThrow().query().get("page_size"));

        FeishuDocPage<FeishuWikiSpace> spaces = client.listWikiSpaces(null, 9999);
        assertEquals("spc1", spaces.items().get(0).spaceId());
        assertEquals("知识库", spaces.items().get(0).name());
        assertEquals("open", spaces.items().get(0).visibility());
        assertFalse(spaces.hasMore());
        // 知识库分页上限 50
        assertEquals("50", requests.stream().filter(request -> request.path().endsWith("/wiki/v2/spaces")).findFirst()
                .orElseThrow().query().get("page_size"));

        FeishuDocumentMeta meta = client.documentMeta("doxcnA");
        assertEquals("doxcnA", meta.documentId());
        assertEquals("42", meta.revisionId());
        assertEquals("测试文档", meta.title());
    }

    @Test
    void mapsWikiNodesKeepingIdsAndRequestShape() {
        FeishuDocPage<FeishuWikiNode> page = client.listWikiNodes("spc1", "wikcnRoot", "np0", 9999);

        assertEquals(1, page.items().size());
        FeishuWikiNode node = page.items().get(0);
        // 长 ID 与全部标识保持飞书字符串原样，不转数值
        assertEquals("wikcnNode1234567890123456789012", node.nodeToken());
        assertEquals("doxcnObj1234567890123456789012", node.objToken());
        assertEquals("docx", node.objType());
        assertEquals("设计文档", node.title());
        assertTrue(node.hasChild());
        assertEquals("wikcnRoot", node.parentNodeToken());
        assertEquals("origin", node.nodeType());
        assertEquals("wikcnOrigin", node.originNodeToken());
        assertEquals("spc0", node.originSpaceId());
        assertEquals("spc1", node.spaceId());
        assertEquals("npt1", page.nextPageToken());
        assertTrue(page.hasMore());

        RecordedRequest request = requests.stream()
                .filter(item -> item.path().endsWith("/wiki/v2/spaces/spc1/nodes")).findFirst().orElseThrow();
        // 官方分页上限 50，page_size 必须钳制；父节点与游标原样透传
        assertEquals("50", request.query().get("page_size"));
        assertEquals("wikcnRoot", request.query().get("parent_node_token"));
        assertEquals("np0", request.query().get("page_token"));
        assertEquals("Bearer token-1", request.authorization());
    }

    @Test
    void requestsRootWikiNodesWithoutParentTokenWhenBlank() {
        client.listWikiNodes("spc1", "", null, 20);

        RecordedRequest request = requests.stream()
                .filter(item -> item.path().endsWith("/wiki/v2/spaces/spc1/nodes")).findFirst().orElseThrow();
        assertFalse(request.query().containsKey("parent_node_token"));
        assertFalse(request.query().containsKey("page_token"));
        assertEquals("20", request.query().get("page_size"));
    }

    @Test
    void keepsRawBlockJsonLossless() {
        FeishuDocPage<FeishuDocumentBlock> page = client.documentBlocks("doxcnA", null, 100);
        assertEquals(1, page.items().size());
        FeishuDocumentBlock block = page.items().get(0);
        assertEquals("doxcnB", block.blockId());
        assertEquals("doxcnA", block.parentId());
        assertEquals(2, block.blockType());
        assertEquals(List.of("doxcnC"), block.children());
        JsonNode raw = readJson(block.raw());
        assertEquals("doxcnB", raw.path("block_id").asText());
        assertEquals("doxcnC", raw.path("children").get(0).asText());
        assertEquals("原始文本", raw.path("text").path("elements").get(0).path("text_run").path("content").asText());
        assertEquals(3, raw.path("text").path("elements").size());
        assertEquals("bp1", page.nextPageToken());
    }

    @Test
    void appendsChildrenAsJsonNodesNotEscapedStrings() {
        String childJson = "{\"block_type\":2,\"text\":{\"elements\":[{\"text_run\":{\"content\":\"新块\"}}]}}";
        FeishuBlockWriteResult result = client.appendDocumentBlocks("doxcnA", "doxcnB", List.of(childJson), 3);

        RecordedRequest request = requests.stream()
                .filter(item -> item.path().endsWith("/blocks/doxcnB/children")).findFirst().orElseThrow();
        assertEquals("POST", request.method());
        assertEquals("Bearer token-1", request.authorization());
        JsonNode body = readJson(request.body());
        assertTrue(body.path("children").isArray());
        assertEquals("新块", body.path("children").get(0).path("text").path("elements").get(0)
                .path("text_run").path("content").asText());
        assertEquals(3, body.path("index").asInt());
        assertEquals(List.of("doxcnD"), result.blockIds());
        assertEquals("43", result.revisionId());
        assertEquals(1, result.blocksRaw().size());
    }

    @Test
    void rejectsNonObjectChildJsonBeforeCallingFeishu() {
        assertThrows(BizException.class, () -> client.appendDocumentBlocks("doxcnA", "doxcnB", List.of("[1,2]"), null));
        assertThrows(BizException.class, () -> client.appendDocumentBlocks("doxcnA", "doxcnB", List.of("{oops"), null));
        assertTrue(requests.stream().noneMatch(item -> item.path().endsWith("/children")));
    }

    @Test
    void deletesChildrenWithHalfOpenRangePassedThroughUnchanged() {
        // 官方语义：start_index/end_index 为左闭右开区间，原样透传，不做下标换算（避免多删一个块）
        client.deleteDocumentBlocks("doxcnA", "doxcnB", 1, 3);
        RecordedRequest request = requests.stream()
                .filter(item -> item.path().endsWith("/children/batch_delete")).findFirst().orElseThrow();
        assertEquals("DELETE", request.method());
        JsonNode body = readJson(request.body());
        assertEquals(1, body.path("start_index").asInt());
        assertEquals(3, body.path("end_index").asInt());
        assertFalse(body.has("start_index_inclusive"));
        // 单块删除 [i, i+1)
        client.deleteDocumentBlocks("doxcnA", "doxcnB", 2, 3);
        JsonNode single = readJson(requests.stream().filter(item -> item.path().endsWith("/children/batch_delete"))
                .reduce((first, second) -> second).orElseThrow().body());
        assertEquals(2, single.path("start_index").asInt());
        assertEquals(3, single.path("end_index").asInt());
    }

    @Test
    void rejectsEmptyOrInvertedDeleteRangeBeforeCallingFeishu() {
        assertThrows(BizException.class, () -> client.deleteDocumentBlocks("doxcnA", "doxcnB", 3, 1));
        assertThrows(BizException.class, () -> client.deleteDocumentBlocks("doxcnA", "doxcnB", 2, 2));
        assertThrows(BizException.class, () -> client.deleteDocumentBlocks("doxcnA", "doxcnB", -1, 1));
        assertTrue(requests.stream().noneMatch(item -> item.path().endsWith("/batch_delete")));
    }

    @Test
    void updatesTextElementsThroughOfficialOperationPayload() {
        String element = "{\"text_run\":{\"content\":\"改后文本\"}}";
        FeishuBlockWriteResult result = client.updateBlockElements("doxcnA", "doxcnB", List.of(element), 42L);

        RecordedRequest request = requests.stream().filter(item -> "PATCH".equals(item.method())).findFirst().orElseThrow();
        assertEquals("/open-apis/docx/v1/documents/doxcnA/blocks/doxcnB", request.path());
        assertEquals("42", request.query().get("document_revision_id"));
        assertNotNull(request.query().get("client_token"));
        JsonNode body = readJson(request.body());
        assertEquals("改后文本", body.path("update_text_elements").path("elements").get(0)
                .path("text_run").path("content").asText());
        assertFalse(body.has("block_id"));
        assertEquals("45", result.revisionId());
        assertEquals(List.of("doxcnB"), result.blockIds());
    }

    @Test
    void mapsRateLimitToRefundableThrottle() {
        FeishuApiException exception = assertThrows(FeishuApiException.class,
                () -> client.documentBlocks("throttled", null, 100));
        assertEquals(99991400, exception.apiCode());
        assertTrue(exception.throttled());
        assertTrue(exception.refundable());
        assertTrue(exception.getMessage().contains("限频"));
    }

    @Test
    void mapsHttp429AndForbiddenToRefundableErrors() {
        FeishuApiException tooMany = assertThrows(FeishuApiException.class,
                () -> client.documentBlocks("busy", null, 100));
        assertEquals(429, tooMany.httpStatus());
        assertTrue(tooMany.refundable());

        FeishuApiException forbidden = assertThrows(FeishuApiException.class,
                () -> client.documentBlocks("forbidden", null, 100));
        assertEquals(403, forbidden.httpStatus());
        assertTrue(forbidden.refundable());
    }

    @Test
    void keepsBusinessErrorsCountedButReadable() {
        FeishuApiException exception = assertThrows(FeishuApiException.class,
                () -> client.documentBlocks("broken", null, 100));
        assertEquals(1770001, exception.apiCode());
        assertFalse(exception.refundable());
        assertTrue(exception.getMessage().contains("1770001"));
        assertTrue(exception.getMessage().contains("block not found"));
    }

    @Test
    void rejectsUnsafeDocumentIdWithoutAnyHttpCall() {
        assertThrows(BizException.class, () -> client.documentMeta("../etc/passwd"));
        assertThrows(BizException.class, () -> client.documentBlocks("doc id", null, 10));
        assertThrows(BizException.class, () -> client.listWikiNodes("../etc", null, null, 20));
        assertTrue(requests.isEmpty());
    }

    @Test
    void refusesCallsWhenCredentialMissing() {
        client.reconfigure(Map.of(FeishuOpenApiClient.BASE_URL_CONFIG_KEY, "http://127.0.0.1:" + server.getAddress().getPort()));
        assertFalse(client.configured());
        assertThrows(BizException.class, () -> client.documentMeta("doxcnA"));
        assertTrue(requests.isEmpty());
    }

    @Test
    void rejectsNonAllowlistedHostWithoutSendingCredentials() {
        assertThrows(BizException.class, () -> client.reconfigure(Map.of(
                FeishuOpenApiClient.BASE_URL_CONFIG_KEY, "https://evil.example.com",
                FeishuOpenApiClient.APP_ID_CONFIG_KEY, "cli_test",
                FeishuOpenApiClient.APP_SECRET_CONFIG_KEY, "secret_test")));
        assertThrows(BizException.class, () -> client.reconfigure(Map.of(
                FeishuOpenApiClient.BASE_URL_CONFIG_KEY, "https://open.feishu.cn.evil.example.com")));
        assertThrows(BizException.class, () -> client.reconfigure(Map.of(
                FeishuOpenApiClient.BASE_URL_CONFIG_KEY, "https://feishu.cn")));
        assertTrue(requests.isEmpty());
        // 上一次成功配置仍然生效，未被非法配置覆盖（1 次取令牌 + 1 次列文件，且只打到已配置的本机服务）
        client.listDriveFiles(null, null, 20);
        assertEquals(2, requests.size());
        assertTrue(requests.stream().allMatch(request -> request.path().startsWith("/open-apis/")));
    }

    @Test
    void requiresHttpsForNonLoopbackHosts() {
        assertThrows(BizException.class, () -> client.reconfigure(Map.of(
                FeishuOpenApiClient.BASE_URL_CONFIG_KEY, "http://open.feishu.cn")));
        assertTrue(requests.isEmpty());
    }

    @Test
    void rejectsLoopbackHttpWhenPrivateNetworkNotAllowed() {
        FeishuOpenApiClient strict = new FeishuOpenApiClient(MAPPER, new OutboundNetworkPolicy(false));
        assertThrows(BizException.class, () -> strict.reconfigure(Map.of(
                FeishuOpenApiClient.BASE_URL_CONFIG_KEY, "http://127.0.0.1:" + server.getAddress().getPort())));
        strict.close();
    }

    @Test
    void acceptsOfficialHostsAndOperatorConfiguredHosts() {
        FeishuOpenApiClient withGateway = new FeishuOpenApiClient(MAPPER, new OutboundNetworkPolicy(true),
                " gw.example.com , gw2.example.com ");
        withGateway.reconfigure(Map.of(FeishuOpenApiClient.BASE_URL_CONFIG_KEY, "https://gw.example.com/feishu-proxy"));
        withGateway.reconfigure(Map.of(FeishuOpenApiClient.BASE_URL_CONFIG_KEY, "https://open.feishu.cn"));
        withGateway.reconfigure(Map.of(FeishuOpenApiClient.BASE_URL_CONFIG_KEY, "https://open.larksuite.com"));
        withGateway.close();
        assertThrows(BizException.class, () -> withGateway.reconfigure(Map.of(
                FeishuOpenApiClient.BASE_URL_CONFIG_KEY, "https://gw3.example.com")));
    }

    @Test
    void rejectsBaseUrlWithCredentialsOrQuery() {
        assertThrows(BizException.class, () -> client.reconfigure(Map.of(
                FeishuOpenApiClient.BASE_URL_CONFIG_KEY, "https://user:pass@open.feishu.cn")));
        assertThrows(BizException.class, () -> client.reconfigure(Map.of(
                FeishuOpenApiClient.BASE_URL_CONFIG_KEY, "https://open.feishu.cn?redirect=evil.example.com")));
        assertThrows(BizException.class, () -> client.reconfigure(Map.of(
                FeishuOpenApiClient.BASE_URL_CONFIG_KEY, "https://open.feishu.cn#fragment")));
        assertTrue(requests.isEmpty());
    }

    @Test
    void redactsSecretAndTokenFromUpstreamErrorMessages() {
        FeishuApiException exception = assertThrows(FeishuApiException.class,
                () -> client.documentBlocks("leaky", null, 100));

        String message = exception.getMessage();
        assertFalse(message.contains("secret_test"), message);
        assertFalse(message.contains("token-1"), message);
        assertTrue(message.contains("***"));
        assertTrue(message.contains("1770001"));
    }

    private void handle(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        requests.add(new RecordedRequest(exchange.getRequestMethod(), path, queryOf(exchange),
                exchange.getRequestHeaders().getFirst("Authorization"), body));
        if (path.endsWith("/tenant_access_token/internal")) {
            respond(exchange, 200, "{\"code\":0,\"msg\":\"ok\",\"tenant_access_token\":\"token-1\",\"expire\":7200}");
            return;
        }
        if (path.endsWith("/drive/v1/files")) {
            respond(exchange, 200, "{\"code\":0,\"msg\":\"ok\",\"data\":{\"files\":[{"
                    + "\"token\":\"fldc1234567890123456789012\",\"name\":\"设计文档\",\"type\":\"docx\","
                    + "\"parent_token\":\"fldr0\",\"url\":\"https://x.feishu.cn/docx/x\","
                    + "\"shortcut_info\":{\"target_type\":\"docx\",\"target_token\":\"doxcnTarget\"},"
                    + "\"created_time\":\"1728000000\",\"modified_time\":\"1728000001\",\"owner_id\":\"ou_owner\"}],"
                    + "\"next_page_token\":\"np1\",\"has_more\":true}}");
            return;
        }
        if (path.endsWith("/wiki/v2/spaces")) {
            respond(exchange, 200, "{\"code\":0,\"msg\":\"ok\",\"data\":{\"items\":[{"
                    + "\"space_id\":\"spc1\",\"name\":\"知识库\",\"description\":\"描述\",\"space_type\":\"team\","
                    + "\"visibility\":\"open\",\"open_sharing\":\"open\"}],\"page_token\":\"p2\",\"has_more\":false}}");
            return;
        }
        if (path.endsWith("/wiki/v2/spaces/spc1/nodes")) {
            respond(exchange, 200, "{\"code\":0,\"msg\":\"ok\",\"data\":{\"has_more\":true,\"page_token\":\"npt1\","
                    + "\"items\":[{\"space_id\":\"spc1\",\"node_token\":\"wikcnNode1234567890123456789012\","
                    + "\"obj_token\":\"doxcnObj1234567890123456789012\",\"obj_type\":\"docx\","
                    + "\"parent_node_token\":\"wikcnRoot\",\"node_type\":\"origin\","
                    + "\"origin_node_token\":\"wikcnOrigin\",\"origin_space_id\":\"spc0\","
                    + "\"has_child\":true,\"title\":\"设计文档\"}]}}");
            return;
        }
        if (path.endsWith("/raw_content")) {
            respond(exchange, 200, "{\"code\":0,\"msg\":\"ok\",\"data\":{\"content\":\"第一行\\n第二行\"}}");
            return;
        }
        if (path.endsWith("/blocks/doxcnB/children/batch_delete")) {
            respond(exchange, 200, "{\"code\":0,\"msg\":\"ok\",\"data\":{\"document_revision_id\":44}}");
            return;
        }
        if (path.endsWith("/blocks/doxcnB/children")) {
            respond(exchange, 200, "{\"code\":0,\"msg\":\"ok\",\"data\":{\"children\":[{\"block_id\":\"doxcnD\","
                    + "\"block_type\":2,\"text\":{\"elements\":[{\"text_run\":{\"content\":\"新块\"}}]}}],"
                    + "\"document_revision_id\":43}}");
            return;
        }
        if (path.endsWith("/blocks/doxcnB")) {
            respond(exchange, 200, "{\"code\":0,\"msg\":\"ok\",\"data\":{\"block\":{\"block_id\":\"doxcnB\","
                    + "\"parent_id\":\"doxcnA\",\"block_type\":2,\"text\":{\"elements\":[{\"text_run\":{\"content\":\"改后文本\"}}]}},"
                    + "\"document_revision_id\":45}}");
            return;
        }
        if (path.endsWith("/documents/throttled/blocks")) {
            respond(exchange, 400, "{\"code\":99991400,\"msg\":\"request trigger frequency limit\"}");
            return;
        }
        if (path.endsWith("/documents/busy/blocks")) {
            respond(exchange, 429, "{\"code\":99991400,\"msg\":\"too many requests\"}");
            return;
        }
        if (path.endsWith("/documents/forbidden/blocks")) {
            respond(exchange, 403, "{\"code\":1770001,\"msg\":\"no permission\"}");
            return;
        }
        if (path.endsWith("/documents/broken/blocks")) {
            respond(exchange, 200, "{\"code\":1770001,\"msg\":\"block not found\"}");
            return;
        }
        if (path.endsWith("/documents/leaky/blocks")) {
            // 模拟上游回显凭据与令牌：宿主文案必须脱敏
            respond(exchange, 200, "{\"code\":1770001,\"msg\":\"secret=secret_test token=token-1\"}");
            return;
        }
        if (path.endsWith("/documents/doxcnA/blocks")) {
            respond(exchange, 200, "{\"code\":0,\"msg\":\"ok\",\"data\":{\"items\":[{\"block_id\":\"doxcnB\","
                    + "\"parent_id\":\"doxcnA\",\"children\":[\"doxcnC\"],\"block_type\":2,"
                    + "\"text\":{\"elements\":[{\"text_run\":{\"content\":\"原始文本\"}},{\"text_run\":{\"content\":\"B\"}},"
                    + "{\"text_run\":{\"content\":\"C\"}}]}}],\"page_token\":\"bp1\",\"has_more\":true}}");
            return;
        }
        if (path.endsWith("/documents/doxcnA")) {
            respond(exchange, 200, "{\"code\":0,\"msg\":\"ok\",\"data\":{\"document\":{"
                    + "\"document_id\":\"doxcnA\",\"revision_id\":42,\"title\":\"测试文档\"}}}");
            return;
        }
        respond(exchange, 404, "{\"code\":1,\"msg\":\"not found\"}");
    }

    private void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] payload = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(status, payload.length);
        exchange.getResponseBody().write(payload);
        exchange.close();
    }

    private static Map<String, String> queryOf(HttpExchange exchange) {
        Map<String, String> query = new java.util.LinkedHashMap<>();
        String raw = exchange.getRequestURI().getRawQuery();
        if (raw == null || raw.isBlank()) {
            return query;
        }
        for (String pair : raw.split("&")) {
            int index = pair.indexOf('=');
            if (index <= 0) {
                continue;
            }
            query.put(java.net.URLDecoder.decode(pair.substring(0, index), StandardCharsets.UTF_8),
                    java.net.URLDecoder.decode(pair.substring(index + 1), StandardCharsets.UTF_8));
        }
        return query;
    }

    private static JsonNode readJson(String body) {
        try {
            return MAPPER.readTree(body);
        }
        catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private record RecordedRequest(String method, String path, Map<String, String> query, String authorization, String body) {
    }
}
