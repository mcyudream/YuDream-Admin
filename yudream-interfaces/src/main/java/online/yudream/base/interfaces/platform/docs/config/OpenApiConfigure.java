package online.yudream.base.interfaces.platform.docs.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.parameters.RequestBody;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.info.Info;
import online.yudream.base.application.platform.docs.dto.ApiDocSettingsDTO;
import online.yudream.base.application.platform.docs.service.ApiDocAppService;
import online.yudream.base.application.platform.plugin.dto.PluginHttpEndpointDTO;
import online.yudream.base.application.platform.plugin.service.PluginAppService;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Configuration
public class OpenApiConfigure {

    @Bean
    public OpenAPI yudreamOpenApi(ApiDocAppService apiDocAppService) {
        ApiDocSettingsDTO settings = apiDocAppService.settings();
        return new OpenAPI()
                .info(new Info()
                        .title(settings.getTitle())
                        .description(settings.getDescription())
                        .version(settings.getVersion()));
    }

    @Bean
    public OpenApiCustomizer pluginHttpEndpointOpenApiCustomizer(PluginAppService pluginAppService) {
        return openApi -> pluginAppService.httpEndpoints()
                .forEach(endpoint -> addPluginEndpoint(openApi, endpoint));
    }

    private void addPluginEndpoint(OpenAPI openApi, PluginHttpEndpointDTO endpoint) {
        PathItem pathItem = openApi.getPaths().computeIfAbsent(endpoint.getFullPath(), ignored -> new PathItem());
        Operation operation = pluginOperation(endpoint);
        switch (endpoint.getMethod().toUpperCase(Locale.ROOT)) {
            case "GET" -> pathItem.get(operation);
            case "POST" -> pathItem.post(withRequestBody(operation));
            case "PUT" -> pathItem.put(withRequestBody(operation));
            case "PATCH" -> pathItem.patch(withRequestBody(operation));
            case "DELETE" -> pathItem.delete(operation);
            case "HEAD" -> pathItem.head(operation);
            case "OPTIONS" -> pathItem.options(operation);
            case "TRACE" -> pathItem.trace(operation);
            default -> pathItem.get(operation);
        }
    }

    private Operation pluginOperation(PluginHttpEndpointDTO endpoint) {
        Operation operation = new Operation()
                .summary(endpoint.getMethod() + " " + endpoint.getPath())
                .description(description(endpoint))
                .tags(List.of("插件 - " + endpoint.getPluginCode()))
                .responses(responses(endpoint));
        pathParameters(endpoint.getFullPath()).forEach(operation::addParametersItem);
        return operation;
    }

    private String description(PluginHttpEndpointDTO endpoint) {
        List<String> lines = new ArrayList<>();
        lines.add("插件：" + endpoint.getPluginCode());
        if (StringUtils.hasText(endpoint.getPermission())) {
            lines.add("权限：" + endpoint.getPermission());
        }
        lines.add(endpoint.isWrapResult() ? "响应会使用系统 Result 包装。" : "响应由插件直接返回。");
        return String.join("\n\n", lines);
    }

    private ApiResponses responses(PluginHttpEndpointDTO endpoint) {
        MediaType mediaType = new MediaType().schema(new Schema<>().type("object"));
        Content content = new Content();
        content.addMediaType(isSseEndpoint(endpoint) ? "text/event-stream" : "application/json", mediaType);
        return new ApiResponses()
                .addApiResponse("200", new ApiResponse()
                        .description("OK")
                        .content(content));
    }

    private boolean isSseEndpoint(PluginHttpEndpointDTO endpoint) {
        String path = endpoint.getPath() == null ? "" : endpoint.getPath().toLowerCase(Locale.ROOT);
        return path.endsWith("/events") || path.contains("/events/");
    }

    private Operation withRequestBody(Operation operation) {
        return operation.requestBody(new RequestBody()
                .required(false)
                .content(new Content()
                        .addMediaType("application/json", new MediaType()
                                .schema(new Schema<>().type("object")))));
    }

    private List<Parameter> pathParameters(String path) {
        List<Parameter> parameters = new ArrayList<>();
        for (String segment : path.split("/")) {
            if (segment.startsWith("{") && segment.endsWith("}") && segment.length() > 2) {
                String name = segment.substring(1, segment.length() - 1);
                parameters.add(new Parameter()
                        .name(name)
                        .in("path")
                        .required(true)
                        .schema(new Schema<>().type("string")));
            }
        }
        return parameters;
    }
}
