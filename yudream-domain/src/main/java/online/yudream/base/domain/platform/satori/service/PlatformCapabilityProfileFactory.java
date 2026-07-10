package online.yudream.base.domain.platform.satori.service;

import online.yudream.base.domain.platform.satori.aggregate.SatoriLogin;

import java.util.Locale;
import java.util.Set;

/**
 * Supplies conservative adapter defaults only when a login did not explicitly
 * advertise a capability. Runtime Login.features always wins over this table.
 */
public final class PlatformCapabilityProfileFactory {
    private static final Set<String> RICH_ADAPTERS = Set.of("discord", "kook", "qqguild", "telegram", "wecom", "slack");
    private static final Set<String> MEDIA_ADAPTERS = Set.of("discord", "kook", "onebot", "qqguild", "telegram", "wecom", "slack");

    private PlatformCapabilityProfileFactory() {
    }

    public static Profile from(SatoriLogin login) {
        Set<String> features = login == null || login.getFeatures() == null
                ? Set.of() : login.getFeatures().stream().filter(value -> value != null && !value.isBlank())
                .map(value -> value.trim().toLowerCase(Locale.ROOT)).collect(java.util.stream.Collectors.toUnmodifiableSet());
        String adapter = login == null || login.getAdapter() == null ? "" : login.getAdapter().trim().toLowerCase(Locale.ROOT);
        return new Profile(features, adapter);
    }

    public record Profile(Set<String> features, String adapter) {
        public Profile {
            features = features == null ? Set.of() : Set.copyOf(features);
            adapter = adapter == null ? "" : adapter;
        }

        public boolean supportsRichText() {
            return featureOrAdapter(RICH_ADAPTERS, "html", "markdown", "message.rich");
        }

        public boolean supportsMedia() {
            return featureOrAdapter(MEDIA_ADAPTERS, "image", "audio", "video", "file", "message.media");
        }

        private boolean featureOrAdapter(Set<String> supportedAdapters, String... featureNames) {
            for (String featureName : featureNames) {
                if (features.contains(featureName)) {
                    return true;
                }
            }
            // A feature list that explicitly talks about this surface is authoritative.
            boolean declared = features.stream().anyMatch(value -> value.startsWith("message.")
                    || value.equals("html") || value.equals("markdown") || value.equals("image")
                    || value.equals("audio") || value.equals("video") || value.equals("file"));
            return !declared && supportedAdapters.contains(adapter);
        }
    }
}
