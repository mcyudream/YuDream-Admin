package online.yudream.base.infra.platform.document.service;

import online.yudream.base.domain.platform.capability.enumerate.CapabilityType;
import online.yudream.base.domain.platform.capability.service.CapabilityProvider;
import online.yudream.base.domain.platform.capability.valobj.CapabilityDescriptor;
import online.yudream.base.domain.platform.capability.valobj.CapabilityHealth;
import online.yudream.base.domain.platform.capability.valobj.CapabilityTestResult;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class WordDocumentCapabilityProvider implements CapabilityProvider {

    public static final String CODE = "document-template";

    private final AtomicBoolean enabled = new AtomicBoolean(false);

    @Override
    public CapabilityDescriptor descriptor() {
        return new CapabilityDescriptor(
                CODE,
                "Word 模板",
                CapabilityType.DOCUMENT,
                "提供 DOCX 模板上传、占位符配置和报告/证明文件生成能力",
                "i-ri:file-word-2-line",
                60,
                Map.of("placeholderStyle", "${变量名}")
        );
    }

    @Override
    public CapabilityHealth health() {
        return enabled.get()
                ? CapabilityHealth.enabled("Word 模板能力已启用", Map.of("renderer", "poi-ooxml"))
                : CapabilityHealth.disabled("Word 模板能力未启用");
    }

    @Override
    public void enable(Map<String, String> config) {
        enabled.set(true);
    }

    @Override
    public void disable() {
        enabled.set(false);
    }

    @Override
    public CapabilityTestResult test(String message) {
        return enabled.get()
                ? CapabilityTestResult.success("Word 模板能力可用")
                : CapabilityTestResult.failure("Word 模板能力未启用");
    }
}
