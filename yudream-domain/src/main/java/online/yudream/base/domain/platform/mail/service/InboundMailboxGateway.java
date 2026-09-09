package online.yudream.base.domain.platform.mail.service;

import online.yudream.base.domain.common.PageResult;
import online.yudream.base.domain.platform.mail.valobj.InboundMailDetail;
import online.yudream.base.domain.platform.mail.valobj.InboundMailSummary;

import java.util.Optional;

/**
 * 入站邮箱只读网关。连接与 MIME 解析在基础设施完成。
 */
public interface InboundMailboxGateway {

    PageResult<InboundMailSummary> page(int page, int size, String keyword);

    Optional<InboundMailDetail> findByUid(long uid);
}
