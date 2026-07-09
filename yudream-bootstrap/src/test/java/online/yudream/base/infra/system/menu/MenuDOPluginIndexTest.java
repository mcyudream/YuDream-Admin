package online.yudream.base.infra.system.menu;

import online.yudream.base.infra.system.menu.dataobj.MenuDO;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.index.CompoundIndex;

import static org.assertj.core.api.Assertions.assertThat;

class MenuDOPluginIndexTest {

    @Test
    void pluginRegistrationIdentityHasAPartialUniqueIndex() {
        CompoundIndex index = MenuDO.class.getAnnotation(CompoundIndex.class);

        assertThat(index).isNotNull();
        assertThat(index.unique()).isTrue();
        assertThat(index.def()).contains("source", "pluginCode", "pluginRegistrationKey");
        assertThat(index.partialFilter()).contains("source", "PLUGIN");
    }
}
