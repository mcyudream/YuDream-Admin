package online.yudream.base.interfaces.platform.interop.controller;

import io.swagger.v3.oas.annotations.Hidden;
import lombok.RequiredArgsConstructor;
import online.yudream.base.application.platform.interop.service.McInteropWellKnownAppService;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * mc-interop v1 统一出口（协议 §2）：根级 well-known 路径由宿主聚合服务，
 * 内容 = mc-interop 插件本站目录 + peers 友站互采合并。
 */
@RestController
@RequiredArgsConstructor
@Hidden
public class McInteropWellKnownController {

    private final McInteropWellKnownAppService appService;

    @GetMapping(path = "/.well-known/mc-interop-v1.json", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Object> site(jakarta.servlet.http.HttpServletRequest request) {
        McInteropWellKnownAppService.WellKnownResponse response =
                appService.site(request.getHeader("If-None-Match"));
        if (response.status() == 304) {
            return ResponseEntity.status(304)
                    .eTag(response.etag())
                    .build();
        }
        return ResponseEntity.status(response.status())
                .contentType(MediaType.APPLICATION_JSON)
                .headers(headers -> {
                    headers.setETag(response.etag());
                    headers.setCacheControl(CacheControl.maxAge(java.time.Duration.ofMinutes(5))
                            .getHeaderValue());
                })
                .body(response.body());
    }
}
