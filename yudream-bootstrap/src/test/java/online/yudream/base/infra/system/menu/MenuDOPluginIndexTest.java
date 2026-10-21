package online.yudream.base.infra.system.menu;

import online.yudream.base.infra.system.menu.dataobj.MenuDO;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;

import java.lang.reflect.Field;

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

    @Test
    void menuCodeHasDeclaredUniqueIndexNamedUkMenuCode() throws NoSuchFieldException {
        Field code = MenuDO.class.getDeclaredField("code");
        Indexed index = code.getAnnotation(Indexed.class);

        assertThat(index).isNotNull();
        assertThat(index.unique()).isTrue();
        assertThat(index.name()).isEqualTo("uk_menu_code");
    }
}
