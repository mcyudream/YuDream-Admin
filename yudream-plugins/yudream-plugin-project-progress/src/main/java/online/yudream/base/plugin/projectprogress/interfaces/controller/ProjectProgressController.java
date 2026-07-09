package online.yudream.base.plugin.projectprogress.interfaces.controller;

import online.yudream.base.plugin.projectprogress.bootstrap.ProjectProgressPlugin;
import online.yudream.base.plugin.projectprogress.interfaces.http.ProjectProgressHttpFacade;
import online.yudream.base.plugin.spi.annotation.PluginHttpEndpoint;
import online.yudream.base.plugin.spi.http.PluginHttpRequest;
import online.yudream.base.plugin.spi.http.PluginHttpResponse;

public class ProjectProgressController {

    private final ProjectProgressHttpFacade http;

    public ProjectProgressController(ProjectProgressHttpFacade http) {
        this.http = http;
    }

    @PluginHttpEndpoint(method = "GET", path = "/status", permission = ProjectProgressPlugin.VIEW_PERMISSION)
    public PluginHttpResponse status(PluginHttpRequest request) {
        return http.status();
    }

    @PluginHttpEndpoint(method = "GET", path = "/projects", permission = ProjectProgressPlugin.VIEW_PERMISSION)
    public PluginHttpResponse projects(PluginHttpRequest request) {
        return http.projects(request);
    }

    @PluginHttpEndpoint(method = "GET", path = "/users", permission = ProjectProgressPlugin.MANAGE_PERMISSION)
    public PluginHttpResponse users(PluginHttpRequest request) {
        return http.users(request);
    }

    @PluginHttpEndpoint(method = "GET", path = "/users/resolve", permission = ProjectProgressPlugin.MANAGE_PERMISSION)
    public PluginHttpResponse resolveUsers(PluginHttpRequest request) {
        return http.resolveUsers(request);
    }

    @PluginHttpEndpoint(method = "GET", path = "/departments", permission = ProjectProgressPlugin.MANAGE_PERMISSION)
    public PluginHttpResponse departments(PluginHttpRequest request) {
        return http.departments(request);
    }

    @PluginHttpEndpoint(method = "GET", path = "/minecraft/servers", permission = ProjectProgressPlugin.MANAGE_PERMISSION)
    public PluginHttpResponse minecraftServers(PluginHttpRequest request) {
        return http.minecraftServers(request);
    }

    @PluginHttpEndpoint(method = "POST", path = "/projects", permission = ProjectProgressPlugin.MANAGE_PERMISSION)
    public PluginHttpResponse createProject(PluginHttpRequest request) {
        return http.createProject(request);
    }

    @PluginHttpEndpoint(method = "GET", path = "/projects/{projectId}", permission = ProjectProgressPlugin.VIEW_PERMISSION)
    public PluginHttpResponse project(PluginHttpRequest request) {
        return http.project(request);
    }

    @PluginHttpEndpoint(method = "PUT", path = "/projects/{projectId}", permission = ProjectProgressPlugin.MANAGE_PERMISSION)
    public PluginHttpResponse updateProject(PluginHttpRequest request) {
        return http.updateProject(request);
    }

    @PluginHttpEndpoint(method = "DELETE", path = "/projects/{projectId}", permission = ProjectProgressPlugin.MANAGE_PERMISSION)
    public PluginHttpResponse deleteProject(PluginHttpRequest request) {
        return http.deleteProject(request);
    }

    @PluginHttpEndpoint(method = "GET", path = "/projects/{projectId}/details", permission = ProjectProgressPlugin.VIEW_PERMISSION)
    public PluginHttpResponse details(PluginHttpRequest request) {
        return http.details(request);
    }

    @PluginHttpEndpoint(method = "POST", path = "/projects/{projectId}/details", permission = ProjectProgressPlugin.MANAGE_PERMISSION)
    public PluginHttpResponse createDetail(PluginHttpRequest request) {
        return http.createDetail(request);
    }

    @PluginHttpEndpoint(method = "PUT", path = "/details/{detailId}", permission = ProjectProgressPlugin.MANAGE_PERMISSION)
    public PluginHttpResponse updateDetail(PluginHttpRequest request) {
        return http.updateDetail(request);
    }

    @PluginHttpEndpoint(method = "DELETE", path = "/details/{detailId}", permission = ProjectProgressPlugin.MANAGE_PERMISSION)
    public PluginHttpResponse deleteDetail(PluginHttpRequest request) {
        return http.deleteDetail(request);
    }

    @PluginHttpEndpoint(method = "POST", path = "/details/{detailId}/publish", permission = ProjectProgressPlugin.ASSIGN_PERMISSION)
    public PluginHttpResponse publishDetail(PluginHttpRequest request) {
        return http.publishDetail(request);
    }

    @PluginHttpEndpoint(method = "POST", path = "/details/{detailId}/random-assign", permission = ProjectProgressPlugin.ASSIGN_PERMISSION)
    public PluginHttpResponse randomAssign(PluginHttpRequest request) {
        return http.randomAssign(request);
    }

    @PluginHttpEndpoint(method = "POST", path = "/details/{detailId}/claim", permission = ProjectProgressPlugin.CHECK_IN_PERMISSION)
    public PluginHttpResponse claim(PluginHttpRequest request) {
        return http.claim(request);
    }

    @PluginHttpEndpoint(method = "GET", path = "/my-tasks", permission = ProjectProgressPlugin.CHECK_IN_PERMISSION)
    public PluginHttpResponse myTasks(PluginHttpRequest request) {
        return http.myTasks(request);
    }

    @PluginHttpEndpoint(method = "GET", path = "/tasks/claimable", permission = ProjectProgressPlugin.CHECK_IN_PERMISSION)
    public PluginHttpResponse claimableTasks(PluginHttpRequest request) {
        return http.claimableTasks(request);
    }

    @PluginHttpEndpoint(method = "GET", path = "/acceptance/pending", permission = ProjectProgressPlugin.ACCEPT_PERMISSION)
    public PluginHttpResponse pendingAcceptance(PluginHttpRequest request) {
        return http.pendingAcceptance(request);
    }

    @PluginHttpEndpoint(method = "POST", path = "/details/{detailId}/submit-acceptance", permission = ProjectProgressPlugin.CHECK_IN_PERMISSION)
    public PluginHttpResponse submitAcceptance(PluginHttpRequest request) {
        return http.submitAcceptance(request);
    }

    @PluginHttpEndpoint(method = "GET", path = "/projects/{projectId}/check-ins", permission = ProjectProgressPlugin.VIEW_PERMISSION)
    public PluginHttpResponse projectCheckIns(PluginHttpRequest request) {
        return http.projectCheckIns(request);
    }

    @PluginHttpEndpoint(method = "POST", path = "/projects/{projectId}/check-ins", permission = ProjectProgressPlugin.CHECK_IN_PERMISSION)
    public PluginHttpResponse createProjectCheckIn(PluginHttpRequest request) {
        return http.createProjectCheckIn(request);
    }

    @PluginHttpEndpoint(method = "POST", path = "/projects/{projectId}/check-ins/minecraft", permission = ProjectProgressPlugin.CHECK_IN_PERMISSION)
    public PluginHttpResponse projectMinecraftCheckIn(PluginHttpRequest request) {
        return http.projectMinecraftCheckIn(request);
    }

    @PluginHttpEndpoint(method = "GET", path = "/details/{detailId}/check-ins", permission = ProjectProgressPlugin.VIEW_PERMISSION)
    public PluginHttpResponse checkIns(PluginHttpRequest request) {
        return http.checkIns(request);
    }

    @PluginHttpEndpoint(method = "POST", path = "/details/{detailId}/check-ins", permission = ProjectProgressPlugin.CHECK_IN_PERMISSION)
    public PluginHttpResponse createCheckIn(PluginHttpRequest request) {
        return http.createCheckIn(request);
    }

    @PluginHttpEndpoint(method = "POST", path = "/details/{detailId}/check-ins/minecraft", permission = ProjectProgressPlugin.CHECK_IN_PERMISSION)
    public PluginHttpResponse minecraftCheckIn(PluginHttpRequest request) {
        return http.minecraftCheckIn(request);
    }

    @PluginHttpEndpoint(method = "POST", path = "/projects/{projectId}/minecraft/auto-check-ins", permission = ProjectProgressPlugin.MANAGE_PERMISSION)
    public PluginHttpResponse autoMinecraftCheckIns(PluginHttpRequest request) {
        return http.autoMinecraftCheckIns(request);
    }

    @PluginHttpEndpoint(method = "POST", path = "/details/{detailId}/accept", permission = ProjectProgressPlugin.ACCEPT_PERMISSION)
    public PluginHttpResponse accept(PluginHttpRequest request) {
        return http.accept(request);
    }

    @PluginHttpEndpoint(method = "POST", path = "/details/{detailId}/reject", permission = ProjectProgressPlugin.ACCEPT_PERMISSION)
    public PluginHttpResponse reject(PluginHttpRequest request) {
        return http.reject(request);
    }

    @PluginHttpEndpoint(method = "GET", path = "/details/{detailId}/acceptance-records", permission = ProjectProgressPlugin.VIEW_PERMISSION)
    public PluginHttpResponse acceptanceRecords(PluginHttpRequest request) {
        return http.acceptanceRecords(request);
    }

    @PluginHttpEndpoint(method = "GET", path = "/projects/{projectId}/events", permission = ProjectProgressPlugin.VIEW_PERMISSION)
    public PluginHttpResponse events(PluginHttpRequest request) {
        return http.events(request);
    }

    @PluginHttpEndpoint(method = "GET", path = "/projects/{projectId}/events/stream", permission = ProjectProgressPlugin.VIEW_PERMISSION)
    public PluginHttpResponse eventStream(PluginHttpRequest request) {
        return http.eventStream();
    }
}
