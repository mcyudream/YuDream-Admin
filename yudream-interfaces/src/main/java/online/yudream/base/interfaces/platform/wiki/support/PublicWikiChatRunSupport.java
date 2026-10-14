package online.yudream.base.interfaces.platform.wiki.support;

import online.yudream.base.application.platform.wiki.dto.PublicWikiChatRunDTO;
import online.yudream.base.application.platform.ai.dto.AiRunDTO;
import online.yudream.base.application.platform.wiki.service.PublicWikiChatRunAppService;
import online.yudream.base.application.platform.wiki.service.WikiPublicAppService;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.ai.valobj.AiChatMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;

/** 公开提问保留有界并发与频率保护，租约由后台完成/显式取消释放。 */
@Component
@ConditionalOnProperty(prefix = "yudream.platform.capabilities.ai", name = "enabled", havingValue = "true")
public class PublicWikiChatRunSupport {
    private final PublicWikiChatRateLimiter limits;
    private final WikiPublicAppService spaces;
    private final PublicWikiChatRunAppService chat;
    private final Semaphore capacity;

    public PublicWikiChatRunSupport(PublicWikiChatRateLimiter limits, WikiPublicAppService spaces,
            PublicWikiChatRunAppService chat, @Value("${yudream.platform.wiki.chat.public-rate-limit.max-active:8}") int maxActive) {
        if (maxActive < 1) throw new IllegalArgumentException("公开问答并发上限必须大于 0");
        this.limits = limits; this.spaces = spaces; this.chat = chat; capacity = new Semaphore(maxActive);
    }

    public PublicWikiChatRunDTO start(String ip, String slug, String token, String question, List<AiChatMessage> history) {
        Runnable release = acquire(ip, slug);
        try { return chat.start(slug, token, question, history, release); }
        catch (RuntimeException error) { release.run(); throw error; }
    }

    public AiRunDTO startForUser(Long userId, String ip, String slug, String question) {
        Runnable release = acquire(ip, slug);
        try { return chat.startForUser(userId, slug, question, release); }
        catch (RuntimeException error) { release.run(); throw error; }
    }

    private Runnable acquire(String ip, String slug) {
        spaces.ensurePublicSpace(slug);
        var permit = limits.acquire(ip, slug);
        if (!capacity.tryAcquire()) { permit.close(); throw new BizException("同时提问人数过多，请稍后再试"); }
        AtomicBoolean released = new AtomicBoolean();
        return () -> { if (released.compareAndSet(false, true)) { capacity.release(); permit.close(); } };
    }
}
