package online.yudream.base.domain.platform.capability.service;

import online.yudream.base.domain.platform.capability.valobj.CapabilityDescriptor;
import online.yudream.base.domain.platform.capability.valobj.CapabilityHealth;
import online.yudream.base.domain.platform.capability.valobj.CapabilityTestResult;

import java.util.Map;

public interface CapabilityProvider {

    CapabilityDescriptor descriptor();

    CapabilityHealth health();

    void enable(Map<String, String> config);

    void disable();

    CapabilityTestResult test(String message);
}
