package online.yudream.base.infra.common.cache.spel;

import online.yudream.base.infra.common.cache.prop.CacheProperties;
import online.yudream.base.infra.common.redis.exception.RedisCacheException;
import org.springframework.context.expression.MethodBasedEvaluationContext;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.lang.reflect.Method;

/**
 * 缓存 Key SpEL 解析器。
 * <p>
 * 支持：
 * <ul>
 *   <li>方法参数引用，如 {@code #userId}</li>
 *   <li>返回值引用，如 {@code #result.id}</li>
 *   <li>root 对象辅助变量：{@code #root.methodName}、{@code #root.className}、{@code #root.targetClass}</li>
 * </ul>
 */
@Component
public class CacheKeySpelParser {

    private final SpelExpressionParser parser = new SpelExpressionParser();
    private final ParameterNameDiscoverer parameterNameDiscoverer = new DefaultParameterNameDiscoverer();
    private final CacheProperties cacheProperties;

    public CacheKeySpelParser(CacheProperties cacheProperties) {
        this.cacheProperties = cacheProperties;
    }

    /**
     * 解析缓存 key。
     *
     * @param expression SpEL 表达式
     * @param method     目标方法
     * @param args       方法参数
     * @param result     方法返回值（可为 null）
     * @return 拼接了全局前缀的最终 key
     */
    public String parseKey(String expression, Method method, Object[] args, Object result) {
        if (!StringUtils.hasText(expression)) {
            throw new RedisCacheException("cache key expression must not be empty");
        }
        EvaluationContext context = createContext(method, args, result);
        String parsed = parser.parseExpression(expression).getValue(context, String.class);
        if (parsed == null) {
            throw new RedisCacheException("cache key resolved to null, expression: " + expression);
        }
        return buildFinalKey(parsed);
    }

    /**
     * 解析条件表达式。
     *
     * @param expression SpEL 表达式
     * @param method     目标方法
     * @param args       方法参数
     * @param result     方法返回值（可为 null）
     * @return 计算结果，表达式为空时返回 true
     */
    public boolean parseCondition(String expression, Method method, Object[] args, Object result) {
        if (!StringUtils.hasText(expression)) {
            return true;
        }
        EvaluationContext context = createContext(method, args, result);
        Boolean value = parser.parseExpression(expression).getValue(context, Boolean.class);
        return Boolean.TRUE.equals(value);
    }

    private EvaluationContext createContext(Method method, Object[] args, Object result) {
        StandardEvaluationContext context = new MethodBasedEvaluationContext(
                new RootObject(method),
                method,
                args == null ? new Object[0] : args,
                parameterNameDiscoverer
        );
        if (result != null) {
            context.setVariable("result", result);
        }
        return context;
    }

    private String buildFinalKey(String parsed) {
        String prefix = cacheProperties.getKeyPrefix();
        if (!StringUtils.hasText(prefix)) {
            return parsed;
        }
        return prefix + ":" + parsed;
    }

    /**
     * 判断 key 是否包含通配符（* 或 ?）。
     */
    public boolean isPattern(String key) {
        return key != null && (key.contains("*") || key.contains("?"));
    }

    public record RootObject(Method method) {
        public String getMethodName() {
            return method.getName();
        }

        public String getClassName() {
            return method.getDeclaringClass().getName();
        }

        public Class<?> getTargetClass() {
            return method.getDeclaringClass();
        }
    }
}
