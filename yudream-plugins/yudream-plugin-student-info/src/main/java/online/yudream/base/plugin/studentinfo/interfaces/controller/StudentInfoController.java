package online.yudream.base.plugin.studentinfo.interfaces.controller;

import online.yudream.base.plugin.spi.annotation.PluginHttpEndpoint;
import online.yudream.base.plugin.spi.http.PluginHttpRequest;
import online.yudream.base.plugin.spi.http.PluginHttpResponse;
import online.yudream.base.plugin.studentinfo.bootstrap.StudentInfoPlugin;
import online.yudream.base.plugin.studentinfo.interfaces.http.StudentInfoHttpFacade;

public class StudentInfoController {

    private final StudentInfoHttpFacade http;

    public StudentInfoController(StudentInfoHttpFacade http) {
        this.http = http;
    }

    @PluginHttpEndpoint(method = "GET", path = "/status", permission = StudentInfoPlugin.VIEW_PERMISSION)
    public PluginHttpResponse status() {
        return http.status();
    }

    @PluginHttpEndpoint(method = "GET", path = "/me", permission = StudentInfoPlugin.USER_PERMISSION)
    public PluginHttpResponse mine(PluginHttpRequest request) {
        return http.mine(request);
    }

    @PluginHttpEndpoint(method = "PUT", path = "/me", permission = StudentInfoPlugin.USER_PERMISSION)
    public PluginHttpResponse saveMine(PluginHttpRequest request) {
        return http.saveMine(request);
    }

    @PluginHttpEndpoint(method = "GET", path = "/admin/profiles", permission = StudentInfoPlugin.MANAGE_PERMISSION)
    public PluginHttpResponse profiles(PluginHttpRequest request) {
        return http.profiles(request);
    }

    @PluginHttpEndpoint(method = "GET", path = "/admin/profiles/{userId}", permission = StudentInfoPlugin.MANAGE_PERMISSION)
    public PluginHttpResponse profile(PluginHttpRequest request) {
        return http.profile(request);
    }

    @PluginHttpEndpoint(method = "PUT", path = "/admin/profiles", permission = StudentInfoPlugin.MANAGE_PERMISSION)
    public PluginHttpResponse saveProfile(PluginHttpRequest request) {
        return http.saveProfile(request);
    }

    @PluginHttpEndpoint(method = "DELETE", path = "/admin/profiles/{userId}", permission = StudentInfoPlugin.MANAGE_PERMISSION)
    public PluginHttpResponse deleteProfile(PluginHttpRequest request) {
        return http.deleteProfile(request);
    }
}
