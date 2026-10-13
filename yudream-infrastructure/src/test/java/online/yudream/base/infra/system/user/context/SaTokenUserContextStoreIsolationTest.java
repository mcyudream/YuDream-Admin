package online.yudream.base.infra.system.user.context;

import cn.dev33.satoken.session.SaSession;
import cn.dev33.satoken.stp.StpLogic;
import cn.dev33.satoken.stp.StpUtil;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SaTokenUserContextStoreIsolationTest {

    @Test
    void doesNotApplyTheOperatorsTokenContextToAnotherUser() {
        StpLogic original = StpUtil.getStpLogic();
        StubLogic logic = new StubLogic();
        StpUtil.setStpLogic(logic);
        try {
            SaTokenUserContextStore store = new SaTokenUserContextStore();
            assertNull(store.getCurrentDeptId(2L));
            assertNull(store.getCurrentRoleId(2L));
            assertEquals(0, logic.sessionReads);
            assertEquals(10L, store.getCurrentDeptId(1L));
            assertEquals(20L, store.getCurrentRoleId(1L));
            assertEquals(2, logic.sessionReads);
        } finally {
            StpUtil.setStpLogic(original);
        }
    }

    @Test
    void anonymousContextFallsBackWithoutCreatingATokenSession() {
        StpLogic original = StpUtil.getStpLogic();
        StubLogic logic = new StubLogic();
        logic.loggedIn = false;
        StpUtil.setStpLogic(logic);
        try {
            SaTokenUserContextStore store = new SaTokenUserContextStore();
            assertNull(store.getCurrentDeptId(1L));
            assertNull(store.getCurrentRoleId(1L));
            assertEquals(0, logic.sessionReads);
        } finally {
            StpUtil.setStpLogic(original);
        }
    }

    private static final class StubLogic extends StpLogic {
        int sessionReads;
        boolean loggedIn = true;
        final SaSession session = new SaSession("permission-overview-test");

        StubLogic() {
            super("login");
            session.set("currentDeptId", 10L);
            session.set("currentRoleId", 20L);
        }

        @Override
        public boolean isLogin() {
            return loggedIn;
        }

        @Override
        public long getLoginIdAsLong() {
            return 1L;
        }

        @Override
        public SaSession getTokenSession() {
            sessionReads++;
            return session;
        }
    }
}
