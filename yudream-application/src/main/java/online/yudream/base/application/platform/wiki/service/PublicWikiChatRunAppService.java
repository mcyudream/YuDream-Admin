package online.yudream.base.application.platform.wiki.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import online.yudream.base.application.platform.ai.dto.AiRunDTO;
import online.yudream.base.application.platform.ai.service.AiRunAppService;
import online.yudream.base.application.platform.wiki.dto.PublicWikiChatRunDTO;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.ai.repo.PublicAiRunGrantRepo;
import online.yudream.base.domain.platform.ai.service.AiUsageExecutionScope;
import online.yudream.base.domain.platform.ai.valobj.AiChatMessage;
import online.yudream.base.domain.platform.ai.valobj.PublicAiRunGrant;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** 公开问答仍使用同一运行日志，随机观察令牌仅授权一个公开知识库会话。 */
@Service
@RequiredArgsConstructor
@lombok.extern.slf4j.Slf4j
@ConditionalOnProperty(prefix = "yudream.platform.capabilities.ai", name = "enabled", havingValue = "true")
public class PublicWikiChatRunAppService {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final TypeReference<Map<String, Object>> ACTIVITY = new TypeReference<>() { };
    private final WikiPublicAppService spaces;
    private final WikiChatAppService chat;
    private final AiRunAppService runs;
    private final PublicAiRunGrantRepo grants;
    private final ObjectMapper json;

    public AiRunDTO startForUser(Long userId, String slug, String question, Runnable release) {
        if (userId == null || userId == 0L) throw new BizException("当前用户未登录");
        spaces.ensurePublicSpace(slug);
        String session = "public-wiki:" + slug;
        try {
            return runs.start(userId, "wiki-public-chat", session, emitter -> {
                emitter.input(question);
                try (AiUsageExecutionScope ignored = AiUsageExecutionScope.open(userId)) {
                    return chat.chatStreamBySlug(slug, userId, session, question, List.of(), emitter::content,
                            emitter::reasoning, emitter::tool,
                            activity -> emitter.progress(activity.phase(), json.convertValue(activity, ACTIVITY)));
                } finally { release.run(); }
            }, release);
        } catch (RuntimeException error) { release.run(); throw error; }
    }

    public PublicWikiChatRunDTO start(String slug, String token, String question, List<AiChatMessage> history, Runnable release) {
        spaces.ensurePublicSpace(slug);
        Instant now = Instant.now();
        String observer = token == null || token.isBlank() ? newToken() : token;
        String hash = hash(observer);
        String session = token == null || token.isBlank() ? "public-" + UUID.randomUUID()
                : grants.conversation(hash, slug, now).orElseThrow(this::unavailable).sessionId();
        String createdRun = null;
        try {
            var run = runs.start(0L, "wiki-public-chat", session, emitter -> {
                emitter.input(question);
                try (AiUsageExecutionScope ignored = AiUsageExecutionScope.open(null)) {
                    return chat.chatStreamBySlug(slug, null, session, question, List.of(), emitter::content,
                            emitter::reasoning, emitter::tool,
                            activity -> emitter.progress(activity.phase(), json.convertValue(activity, ACTIVITY)));
                } finally { release.run(); }
            }, release);
            createdRun = run.id();
            grants.save(new PublicAiRunGrant(run.id(), hash, session, slug, now.plus(Duration.ofDays(1))));
            return new PublicWikiChatRunDTO(run, observer);
        } catch (RuntimeException error) {
            if (createdRun != null) {
                try { runs.cancel(0L, createdRun); }
                catch (RuntimeException cancelError) { log.warn("公开问答观察授权保存失败，后台取消请求失败，runId={}", createdRun); }
            }
            release.run(); throw error;
        }
    }

    public AiRunDTO get(String slug, String id, String token) {
        authorize(slug, id, token);
        return runs.get(0L, id);
    }
    public AiRunDTO cancel(String slug, String id, String token) {
        authorize(slug, id, token);
        return runs.cancel(0L, id);
    }
    private void authorize(String slug, String id, String token) {
        spaces.ensurePublicSpace(slug);
        grants.find(id, hash(token), slug, Instant.now()).orElseThrow(this::unavailable);
        if (!"wiki-public-chat".equals(runs.get(0L, id).module())) throw unavailable();
    }
    private String newToken() {
        byte[] bytes = new byte[32]; RANDOM.nextBytes(bytes); return HexFormat.of().formatHex(bytes);
    }
    private String hash(String token) {
        if (token == null || !token.matches("[a-f0-9]{64}")) throw unavailable();
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException error) { throw new IllegalStateException(error); }
    }
    private BizException unavailable() { return new BizException("问答运行不存在或观察授权已过期"); }
}
