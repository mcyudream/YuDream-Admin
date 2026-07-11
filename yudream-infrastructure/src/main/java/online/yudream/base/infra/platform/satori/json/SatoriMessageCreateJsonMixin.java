package online.yudream.base.infra.platform.satori.json;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Map;

/** Some adapters treat an empty referrer object as an invalid reply reference. */
abstract class SatoriMessageCreateJsonMixin {

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    abstract Map<String, Object> referrer();
}
