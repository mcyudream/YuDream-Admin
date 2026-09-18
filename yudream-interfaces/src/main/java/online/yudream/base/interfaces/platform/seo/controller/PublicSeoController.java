package online.yudream.base.interfaces.platform.seo.controller;

import online.yudream.base.application.platform.seo.SeoViewAppService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 公开 SEO 端点：页面壳 head 注入与 sitemap。
 * <p>
 * nginx 把页面类请求（无物理文件的 SPA 路由）兜底转发到 /seo/view，
 * 后端按路由注入真实标题/摘要/og/robots 后返回整个 index.html。
 * yudream.seo.enabled=false 时本控制器不注册，请求得到 404，
 * nginx 拦截后回落静态壳，行为与未接入该特性前一致。
 */
@RestController
@RequestMapping
@ConditionalOnProperty(name = "yudream.seo.enabled", havingValue = "true")
public class PublicSeoController {

    private final SeoViewAppService seoViewAppService;

    public PublicSeoController(SeoViewAppService seoViewAppService) {
        this.seoViewAppService = seoViewAppService;
    }

    /**
     * 路径优先取反代注入的 X-SEO-Path 头（nginx $uri$is_args$args，已归一化），
     * 兼容 ?path= 查询参数直连调试。
     */
    @GetMapping(value = "/api/public/seo/view", produces = MediaType.TEXT_HTML_VALUE + ";charset=UTF-8")
    public ResponseEntity<String> view(@RequestHeader(value = "X-SEO-Path", required = false) String headerPath,
                                       @RequestParam(value = "path", required = false) String queryPath) {
        String path = headerPath != null && !headerPath.isBlank() ? headerPath : queryPath;
        String html = seoViewAppService.renderView(path);
        if (html == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        return ResponseEntity.ok(html);
    }

    /**
     * 公开内容站点地图；可直接提交到百度资源平台 / Google Search Console。
     */
    @GetMapping(value = "/api/public/sitemap.xml", produces = MediaType.APPLICATION_XML_VALUE + ";charset=UTF-8")
    public ResponseEntity<String> sitemap() {
        return ResponseEntity.ok(seoViewAppService.sitemapXml());
    }
}
