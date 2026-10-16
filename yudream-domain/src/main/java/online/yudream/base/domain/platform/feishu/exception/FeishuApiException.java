package online.yudream.base.domain.platform.feishu.exception;

import online.yudream.base.domain.common.exception.BizException;

/**
 * 飞书开放平台返回的业务错误：保留飞书错误码与 HTTP 状态，文案面向使用者。
 * <p>
 * {@link #refundable()} 用于额度核销约定：飞书在真正执行前拒绝的调用（限频、鉴权/权限不足）
 * 退还预占；网络超时等未知结果保守计数，不退还。
 */
public class FeishuApiException extends BizException {

    private final Integer apiCode;
    private final int httpStatus;

    public FeishuApiException(String message, Integer apiCode, int httpStatus) {
        super(message);
        this.apiCode = apiCode;
        this.httpStatus = httpStatus;
    }

    public Integer apiCode() {
        return apiCode;
    }

    public int httpStatus() {
        return httpStatus;
    }

    /** 飞书限频：单应用每秒上限或单篇文档并发编辑上限。 */
    public boolean throttled() {
        return httpStatus == 429 || (apiCode != null && apiCode == 99991400);
    }

    public boolean refundable() {
        return throttled() || httpStatus == 401 || httpStatus == 403;
    }
}
