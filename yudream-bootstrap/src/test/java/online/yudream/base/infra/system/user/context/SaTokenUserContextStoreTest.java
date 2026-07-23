package online.yudream.base.infra.system.user.context;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SaTokenUserContextStoreTest {

    private final SaTokenUserContextStore userContextStore = new SaTokenUserContextStore();

    @Test
    void returnsNullForRoleWithoutHttpContext() {
        assertThat(userContextStore.getCurrentRoleId(1L)).isNull();
    }

    @Test
    void returnsNullForDepartmentWithoutHttpContext() {
        assertThat(userContextStore.getCurrentDeptId(1L)).isNull();
    }
}
