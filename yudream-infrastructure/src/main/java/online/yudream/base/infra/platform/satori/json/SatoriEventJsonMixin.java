package online.yudream.base.infra.platform.satori.json;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Satori 事件扩展字段的传输层字段映射。 */
abstract class SatoriEventJsonMixin {

    @JsonProperty("_type")
    abstract String extensionType();

    @JsonProperty("_data")
    abstract Object extensionData();
}
