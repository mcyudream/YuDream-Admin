package online.yudream.base.infra.platform.satori.service;

import online.yudream.base.domain.common.exception.BizException;
import org.springframework.http.HttpStatusCode;

/** Converts remote HTTP failures into stable domain-facing errors without exposing credentials. */
final class SatoriHttpErrorMapper {
    private SatoriHttpErrorMapper() {
    }

    static BizException toException(HttpStatusCode status, String body) {
        int code = status.value();
        String detail = body == null || body.isBlank() ? "" : ": " + body.substring(0, Math.min(body.length(), 512));
        return switch (code) {
            case 400 -> new BizException(code, "Satori 请求参数错误" + detail);
            case 401 -> new BizException(code, "Satori 鉴权失败");
            case 403 -> new BizException(code, "Satori 拒绝访问");
            case 404 -> new BizException(code, "Satori 接口或资源不存在");
            case 405 -> new BizException(code, "Satori 不支持该请求方法");
            case 501 -> new BizException(code, "Satori 适配器未实现该能力");
            default -> new BizException(code, "Satori 服务请求失败（HTTP " + code + ")" + detail);
        };
    }
}
