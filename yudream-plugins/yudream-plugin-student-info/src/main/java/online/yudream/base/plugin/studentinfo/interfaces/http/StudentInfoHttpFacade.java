package online.yudream.base.plugin.studentinfo.interfaces.http;

import online.yudream.base.plugin.spi.http.PluginHttpRequest;
import online.yudream.base.plugin.spi.http.PluginHttpResponse;
import online.yudream.base.plugin.spi.system.FrameworkServices;
import online.yudream.base.plugin.spi.system.user.PluginUserProfile;
import online.yudream.base.plugin.studentinfo.application.query.StudentInfoQuery;
import online.yudream.base.plugin.studentinfo.application.service.StudentInfoAppService;
import online.yudream.base.plugin.studentinfo.infrastructure.support.JsonSupport;
import online.yudream.base.plugin.studentinfo.interfaces.assembler.StudentInfoWebAssembler;
import online.yudream.base.plugin.studentinfo.interfaces.request.StudentInfoSaveRequest;
import online.yudream.base.plugin.studentinfo.interfaces.res.StudentInfoSummaryRes;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

public class StudentInfoHttpFacade {

    private final StudentInfoAppService appService;
    private final FrameworkServices framework;
    private final StudentInfoWebAssembler assembler = new StudentInfoWebAssembler();

    public StudentInfoHttpFacade(StudentInfoAppService appService, FrameworkServices framework) {
        this.appService = appService;
        this.framework = framework;
    }

    public PluginHttpResponse status() {
        return PluginHttpResponse.ok(new StudentInfoSummaryRes(appService.count()));
    }

    public PluginHttpResponse mine(PluginHttpRequest request) {
        String userId = currentUserId(request);
        PluginUserProfile user = userOf(userId);
        return PluginHttpResponse.ok(appService.findByUserId(userId)
                .map(dto -> assembler.toRes(dto, user))
                .orElseGet(() -> assembler.empty(userId, user)));
    }

    public PluginHttpResponse saveMine(PluginHttpRequest request) {
        String userId = currentUserId(request);
        StudentInfoSaveRequest body = JsonSupport.read(request.body(), StudentInfoSaveRequest.class);
        var saved = appService.save(assembler.toCmd(body, userId));
        return PluginHttpResponse.ok(assembler.toRes(saved, userOf(userId)));
    }

    public PluginHttpResponse profiles(PluginHttpRequest request) {
        StudentInfoQuery query = new StudentInfoQuery(
                firstQuery(request, "keyword"),
                firstQuery(request, "college"),
                firstQuery(request, "className"),
                page(request),
                size(request)
        );
        return PluginHttpResponse.ok(appService.list(query).stream()
                .map(dto -> assembler.toRes(dto, userOf(dto.userId())))
                .toList());
    }

    public PluginHttpResponse profile(PluginHttpRequest request) {
        String userId = pathSegment(request.path(), 2);
        return appService.findByUserId(userId)
                .map(dto -> PluginHttpResponse.ok(assembler.toRes(dto, userOf(dto.userId()))))
                .orElseGet(() -> PluginHttpResponse.rawJson(404, Map.of("message", "学生档案不存在")));
    }

    public PluginHttpResponse saveProfile(PluginHttpRequest request) {
        StudentInfoSaveRequest body = JsonSupport.read(request.body(), StudentInfoSaveRequest.class);
        String userId = requireText(body.userId(), "用户不能为空");
        ensureUserExists(userId);
        var saved = appService.save(assembler.toAdminCmd(body));
        return PluginHttpResponse.ok(assembler.toRes(saved, userOf(userId)));
    }

    public PluginHttpResponse deleteProfile(PluginHttpRequest request) {
        appService.delete(pathSegment(request.path(), 2));
        return PluginHttpResponse.ok(Map.of("deleted", true));
    }

    private String currentUserId(PluginHttpRequest request) {
        if (request.principal() == null || request.principal().userId() == null) {
            throw new IllegalArgumentException("请先登录");
        }
        return String.valueOf(request.principal().userId());
    }

    private void ensureUserExists(String userId) {
        try {
            Long id = Long.parseLong(userId);
            framework.users().findById(id)
                    .orElseThrow(() -> new IllegalArgumentException("用户不存在：" + userId));
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("用户 ID 必须是数字：" + userId, ex);
        }
    }

    private PluginUserProfile userOf(String userId) {
        if (framework == null || userId == null || userId.isBlank()) {
            return null;
        }
        try {
            return framework.users().findById(Long.parseLong(userId)).orElse(null);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private int page(PluginHttpRequest request) {
        return intQuery(request, "page", 1);
    }

    private int size(PluginHttpRequest request) {
        return intQuery(request, "size", 20);
    }

    private int intQuery(PluginHttpRequest request, String key, int defaultValue) {
        List<String> values = request.query().get(key);
        return values == null || values.isEmpty() || values.get(0).isBlank() ? defaultValue : Integer.parseInt(values.get(0));
    }

    private String firstQuery(PluginHttpRequest request, String key) {
        List<String> values = request.query().get(key);
        return values == null || values.isEmpty() ? null : values.get(0);
    }

    private String pathSegment(String path, int index) {
        String[] segments = trim(path).split("/");
        return index >= 0 && index < segments.length ? decode(segments[index]) : null;
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

    private String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }
}
