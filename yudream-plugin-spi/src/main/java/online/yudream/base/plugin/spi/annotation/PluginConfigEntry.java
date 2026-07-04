package online.yudream.base.plugin.spi.annotation;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Retention(RetentionPolicy.RUNTIME)
public @interface PluginConfigEntry {

    String key();

    String value();
}
