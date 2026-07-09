package online.yudream.base.plugin.projectprogress.interfaces.http;

import online.yudream.base.plugin.projectprogress.application.service.ProjectProgressAppService;
import online.yudream.base.plugin.projectprogress.infrastructure.support.JsonSupport;
import online.yudream.base.plugin.projectprogress.interfaces.assembler.ProjectProgressWebAssembler;
import online.yudream.base.plugin.projectprogress.interfaces.request.ProjectProgressAcceptanceRequest;
import online.yudream.base.plugin.projectprogress.interfaces.request.ProjectProgressCheckInRequest;
import online.yudream.base.plugin.projectprogress.interfaces.request.ProjectProgressDetailSaveRequest;
import online.yudream.base.plugin.projectprogress.interfaces.request.ProjectProgressProjectSaveRequest;
import online.yudream.base.plugin.spi.http.PluginHttpRequest;
import online.yudream.base.plugin.spi.http.PluginHttpResponse;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

public class ProjectProgressHttpFacade {

    private final ProjectProgressAppService appService;
    private final ProjectProgressWebAssembler assembler = new ProjectProgressWebAssembler();

    public ProjectProgressHttpFacade(ProjectProgressAppService appService) {
        this.appService = appService;
    }

    public PluginHttpResponse status() {
        return PluginHttpResponse.ok(assembler.toRes(appService.status()));
    }

    public PluginHttpResponse projects(PluginHttpRequest request) {
        return PluginHttpResponse.ok(appService.projects(page(request), size(request)).stream().map(assembler::toRes).toList());
    }

    public PluginHttpResponse createProject(PluginHttpRequest request) {
        ProjectProgressProjectSaveRequest body = JsonSupport.read(request.body(), ProjectProgressProjectSaveRequest.class);
        return PluginHttpResponse.ok(assembler.toRes(appService.createProject(assembler.toCmd(body), currentUserId(request))));
    }

    public PluginHttpResponse project(PluginHttpRequest request) {
        return PluginHttpResponse.ok(assembler.toRes(appService.project(pathSegment(request.path(), 1))));
    }

    public PluginHttpResponse updateProject(PluginHttpRequest request) {
        ProjectProgressProjectSaveRequest body = JsonSupport.read(request.body(), ProjectProgressProjectSaveRequest.class);
        return PluginHttpResponse.ok(assembler.toRes(appService.updateProject(pathSegment(request.path(), 1), assembler.toCmd(body), currentUserId(request))));
    }

    public PluginHttpResponse deleteProject(PluginHttpRequest request) {
        appService.deleteProject(pathSegment(request.path(), 1));
        return PluginHttpResponse.ok(Map.of("deleted", true));
    }

    public PluginHttpResponse details(PluginHttpRequest request) {
        return PluginHttpResponse.ok(appService.details(pathSegment(request.path(), 1), page(request), size(request)).stream().map(assembler::toRes).toList());
    }

    public PluginHttpResponse createDetail(PluginHttpRequest request) {
        ProjectProgressDetailSaveRequest body = JsonSupport.read(request.body(), ProjectProgressDetailSaveRequest.class);
        return PluginHttpResponse.ok(assembler.toRes(appService.createDetail(pathSegment(request.path(), 1), assembler.toCmd(body), currentUserId(request))));
    }

    public PluginHttpResponse updateDetail(PluginHttpRequest request) {
        ProjectProgressDetailSaveRequest body = JsonSupport.read(request.body(), ProjectProgressDetailSaveRequest.class);
        return PluginHttpResponse.ok(assembler.toRes(appService.updateDetail(pathSegment(request.path(), 1), assembler.toCmd(body), currentUserId(request))));
    }

    public PluginHttpResponse deleteDetail(PluginHttpRequest request) {
        appService.deleteDetail(pathSegment(request.path(), 1));
        return PluginHttpResponse.ok(Map.of("deleted", true));
    }

    public PluginHttpResponse publishDetail(PluginHttpRequest request) {
        return PluginHttpResponse.ok(assembler.toRes(appService.publishDetail(pathSegment(request.path(), 1), currentUserId(request))));
    }

    public PluginHttpResponse randomAssign(PluginHttpRequest request) {
        return PluginHttpResponse.ok(assembler.toRes(appService.randomAssign(pathSegment(request.path(), 1), currentUserId(request))));
    }

    public PluginHttpResponse claim(PluginHttpRequest request) {
        return PluginHttpResponse.ok(assembler.toRes(appService.claim(pathSegment(request.path(), 1), currentUserId(request))));
    }

    public PluginHttpResponse myTasks(PluginHttpRequest request) {
        return PluginHttpResponse.ok(appService.myTasks(currentUserId(request), page(request), size(request)).stream().map(assembler::toRes).toList());
    }

    public PluginHttpResponse claimableTasks(PluginHttpRequest request) {
        return PluginHttpResponse.ok(appService.claimableTasks(currentUserId(request), page(request), size(request)).stream().map(assembler::toRes).toList());
    }

    public PluginHttpResponse pendingAcceptance(PluginHttpRequest request) {
        return PluginHttpResponse.ok(appService.pendingAcceptance(currentUserId(request), page(request), size(request)).stream().map(assembler::toRes).toList());
    }

    public PluginHttpResponse checkIns(PluginHttpRequest request) {
        return PluginHttpResponse.ok(appService.checkIns(pathSegment(request.path(), 1), page(request), size(request)).stream().map(assembler::toRes).toList());
    }

    public PluginHttpResponse createCheckIn(PluginHttpRequest request) {
        ProjectProgressCheckInRequest body = JsonSupport.read(request.body(), ProjectProgressCheckInRequest.class);
        return PluginHttpResponse.ok(assembler.toRes(appService.checkIn(pathSegment(request.path(), 1), assembler.toCmd(body), currentUserId(request))));
    }

    public PluginHttpResponse minecraftCheckIn(PluginHttpRequest request) {
        return PluginHttpResponse.ok(assembler.toRes(appService.minecraftCheckIn(pathSegment(request.path(), 1), currentUserId(request))));
    }

    public PluginHttpResponse autoMinecraftCheckIns(PluginHttpRequest request) {
        return PluginHttpResponse.ok(appService.autoMinecraftCheckIns(pathSegment(request.path(), 1)).stream().map(assembler::toRes).toList());
    }

    public PluginHttpResponse accept(PluginHttpRequest request) {
        ProjectProgressAcceptanceRequest body = JsonSupport.read(request.body(), ProjectProgressAcceptanceRequest.class);
        return PluginHttpResponse.ok(assembler.toRes(appService.accept(pathSegment(request.path(), 1), assembler.toCmd(body), currentUserId(request))));
    }

    public PluginHttpResponse reject(PluginHttpRequest request) {
        ProjectProgressAcceptanceRequest body = JsonSupport.read(request.body(), ProjectProgressAcceptanceRequest.class);
        return PluginHttpResponse.ok(assembler.toRes(appService.reject(pathSegment(request.path(), 1), assembler.toCmd(body), currentUserId(request))));
    }

    public PluginHttpResponse acceptanceRecords(PluginHttpRequest request) {
        return PluginHttpResponse.ok(appService.acceptanceRecords(pathSegment(request.path(), 1), page(request), size(request)).stream().map(assembler::toRes).toList());
    }

    public PluginHttpResponse events(PluginHttpRequest request) {
        return PluginHttpResponse.ok(appService.events(pathSegment(request.path(), 1), longQuery(request, "since"), page(request), size(request)).stream().map(assembler::toRes).toList());
    }

    public PluginHttpResponse eventStream() {
        return new PluginHttpResponse(200, Map.of("Cache-Control", "no-cache"), "text/event-stream", appService.eventStream(), false);
    }

    private String currentUserId(PluginHttpRequest request) {
        if (request.principal() == null || request.principal().userId() == null) {
            throw new IllegalArgumentException("请先登录");
        }
        return String.valueOf(request.principal().userId());
    }

    private int page(PluginHttpRequest request) {
        return intQuery(request, "page", 1);
    }

    private int size(PluginHttpRequest request) {
        return intQuery(request, "size", 20);
    }

    private int intQuery(PluginHttpRequest request, String key, int defaultValue) {
        java.util.List<String> values = request.query().get(key);
        return values == null || values.isEmpty() || values.get(0).isBlank() ? defaultValue : Integer.parseInt(values.get(0));
    }

    private Long longQuery(PluginHttpRequest request, String key) {
        java.util.List<String> values = request.query().get(key);
        return values == null || values.isEmpty() || values.get(0).isBlank() ? null : Long.parseLong(values.get(0));
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
}
