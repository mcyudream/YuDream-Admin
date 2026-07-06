package online.yudream.base.plugin.alipay.interfaces.http;

import online.yudream.base.plugin.alipay.application.dto.AlipayNotifyResultDTO;
import online.yudream.base.plugin.alipay.bootstrap.AlipayPlugin;
import online.yudream.base.plugin.alipay.application.service.AlipayAppService;
import online.yudream.base.plugin.alipay.infrastructure.support.FormSupport;
import online.yudream.base.plugin.alipay.infrastructure.support.JsonSupport;
import online.yudream.base.plugin.alipay.interfaces.assembler.AlipayWebAssembler;
import online.yudream.base.plugin.alipay.interfaces.request.AlipayConfigSaveRequest;
import online.yudream.base.plugin.alipay.interfaces.request.AlipayRechargeCreateRequest;
import online.yudream.base.plugin.spi.http.PluginHttpRequest;
import online.yudream.base.plugin.spi.http.PluginHttpResponse;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class AlipayHttpFacade {

    private final AlipayAppService appService;
    private final AlipayWebAssembler assembler = new AlipayWebAssembler();

    public AlipayHttpFacade(AlipayAppService appService) {
        this.appService = appService;
    }

    public PluginHttpResponse config() {
        return PluginHttpResponse.ok(assembler.toRes(appService.config()));
    }

    public PluginHttpResponse saveConfig(PluginHttpRequest request) {
        AlipayConfigSaveRequest body = JsonSupport.read(request.body(), AlipayConfigSaveRequest.class);
        return PluginHttpResponse.ok(assembler.toRes(appService.saveConfig(assembler.toCmd(body))));
    }

    public PluginHttpResponse createRecharge(PluginHttpRequest request) {
        AlipayRechargeCreateRequest body = JsonSupport.read(request.body(), AlipayRechargeCreateRequest.class);
        if (!request.principal().hasPermission(AlipayPlugin.MANAGE_PERMISSION)) {
            Long userId = request.principal().userId();
            if (userId == null) {
                throw new IllegalArgumentException("请先登录");
            }
            if (body.userId() != null && !body.userId().isBlank() && !String.valueOf(userId).equals(body.userId().trim())) {
                throw new IllegalArgumentException("只能为自己的钱包充值");
            }
            body = new AlipayRechargeCreateRequest(
                    String.valueOf(userId),
                    body.assetCode(),
                    body.amount(),
                    body.walletAmount(),
                    body.subject(),
                    body.body(),
                    body.productType()
            );
        }
        return PluginHttpResponse.ok(assembler.toRes(appService.createRecharge(assembler.toCmd(body), request.principal().userId())));
    }

    public PluginHttpResponse orders(PluginHttpRequest request) {
        return PluginHttpResponse.ok(appService.listOrders(page(request), size(request)).stream()
                .map(assembler::toRes)
                .toList());
    }

    public PluginHttpResponse order(PluginHttpRequest request) {
        return appService.findOrder(lastPathSegment(request.path()))
                .filter(order -> canReadOrder(request, order.userId()))
                .map(assembler::toRes)
                .map(PluginHttpResponse::ok)
                .orElseGet(() -> PluginHttpResponse.rawJson(404, Map.of("message", "充值订单不存在")));
    }

    public PluginHttpResponse notify(PluginHttpRequest request) {
        try {
            AlipayNotifyResultDTO result = appService.handleNotify(notifyParams(request));
            String body = result.success() ? "success" : "failure";
            return new PluginHttpResponse(200, Map.of(), "text/plain;charset=UTF-8", body, false);
        } catch (RuntimeException e) {
            return new PluginHttpResponse(200, Map.of(), "text/plain;charset=UTF-8", "failure", false);
        }
    }

    public PluginHttpResponse notifyDebug(PluginHttpRequest request) {
        AlipayNotifyResultDTO result = appService.handleNotify(notifyParams(request));
        return PluginHttpResponse.ok(assembler.toRes(result));
    }

    private Map<String, String> notifyParams(PluginHttpRequest request) {
        Map<String, String> params = new LinkedHashMap<>(FormSupport.flatten(request.query()));
        String body = request.body();
        if (body != null && !body.isBlank()) {
            String contentType = header(request, "content-type");
            if (contentType != null && contentType.toLowerCase().contains("application/json")) {
                JsonSupport.readMap(body).forEach((key, value) -> params.put(key, value == null ? "" : String.valueOf(value)));
            } else {
                params.putAll(FormSupport.parse(body));
            }
        }
        return params;
    }

    private int page(PluginHttpRequest request) {
        return intQuery(request, "page", 1);
    }

    private int size(PluginHttpRequest request) {
        return intQuery(request, "size", 20);
    }

    private int intQuery(PluginHttpRequest request, String key, int defaultValue) {
        List<String> values = request.query().get(key);
        return values == null || values.isEmpty() ? defaultValue : Integer.parseInt(values.get(0));
    }

    private String lastPathSegment(String path) {
        String[] segments = trim(path).split("/");
        return decode(segments[segments.length - 1]);
    }

    private String trim(String path) {
        String value = path == null ? "" : path.trim();
        while (value.startsWith("/")) {
            value = value.substring(1);
        }
        return value;
    }

    private String decode(String value) {
        return value == null ? null : URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    private String header(PluginHttpRequest request, String name) {
        for (Map.Entry<String, List<String>> entry : request.headers().entrySet()) {
            if (entry.getKey().equalsIgnoreCase(name) && !entry.getValue().isEmpty()) {
                return entry.getValue().get(0);
            }
        }
        return null;
    }

    private boolean canReadOrder(PluginHttpRequest request, String ownerId) {
        if (request.principal().hasPermission(AlipayPlugin.MANAGE_PERMISSION)) {
            return true;
        }
        Long userId = request.principal().userId();
        return userId != null && String.valueOf(userId).equals(ownerId);
    }
}
