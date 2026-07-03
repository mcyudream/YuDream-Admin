package online.yudream.base.interfaces.platform.docs.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import online.yudream.base.application.platform.docs.dto.ApiDocSettingsDTO;
import online.yudream.base.application.platform.docs.service.ApiDocAppService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

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
}
