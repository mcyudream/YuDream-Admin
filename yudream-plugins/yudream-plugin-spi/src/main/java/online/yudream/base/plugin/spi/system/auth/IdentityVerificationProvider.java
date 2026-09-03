package online.yudream.base.plugin.spi.system.auth;

/**
 * 身份核验提供器：插件以一种核验方式（学信网、教育邮箱、CARSI、人工审核等）
 * 参与注册门禁。通过 PluginContext.registerExtension(IdentityVerificationProvider.class, provider) 注册。
 *
 * <p>核验交互（发起、回调、审核队列）由插件自身 HTTP 端点与前端页面承载；
 * 宿主只在注册时调用 check(...) 询问该主体是否已完成核验，并通过
 * GET /api/user/register/verification-methods 向前端暴露当前可用的核验方式清单。</p>
 *
 * <p>站点设置 system.auth.registration.required-verifications（逗号分隔的方式编码）
 * 声明哪些方式为注册必需；必需的提供器缺失或核验未通过时注册被拒绝（fail-closed）。</p>
 */
public interface IdentityVerificationProvider {

    IdentityVerificationMethod method();

    IdentityVerificationResult check(VerificationSubject subject);
}
