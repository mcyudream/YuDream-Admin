package online.yudream.base.domain.system.user.service;

import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.milky.enumerate.MilkyConnectionProtocol;
import online.yudream.base.domain.system.user.aggregate.MessagingIdentity;
import online.yudream.base.domain.system.user.enumerate.MessagingIdentityType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MessagingIdentityClassifierTest {

    @Test
    void milkyQqNumberAcceptsFiveToTwelveDigits() {
        assertTrue(MessagingIdentityClassifier.milkyQqNumber("10001"));
        assertTrue(MessagingIdentityClassifier.milkyQqNumber("3816679582"));
        assertFalse(MessagingIdentityClassifier.milkyQqNumber("member-openid"));
        assertFalse(MessagingIdentityClassifier.milkyQqNumber("1234"));
    }

    @Test
    void storedNumericQqMigratesAsMilky() {
        MessagingIdentityClassifier.Classification classification = MessagingIdentityClassifier.classifyStoredQq("10086");
        assertEquals(MilkyConnectionProtocol.MILKY, classification.protocol());
        assertEquals(MessagingIdentityType.QQ, classification.identityType());
        assertEquals("10086", classification.identity());
        assertNull(classification.appId());
        assertNull(classification.groupOpenid());
    }

    @Test
    void storedOfficialOpenidMigratesAsUserOpenid() {
        MessagingIdentityClassifier.Classification classification =
                MessagingIdentityClassifier.classifyStoredQq("user-openid-abc");
        assertEquals(MilkyConnectionProtocol.OFFICIAL, classification.protocol());
        assertEquals(MessagingIdentityType.USER_OPENID, classification.identityType());
        assertEquals("user-openid-abc", classification.identity());
    }

    @Test
    void officialGroupEventUsesMemberOpenid() {
        MessagingIdentityClassifier.Classification classification = MessagingIdentityClassifier.classifyEvent(
                MilkyConnectionProtocol.OFFICIAL, "group", "member-1", "group-open", "app-9");
        assertEquals(MessagingIdentityType.MEMBER_OPENID, classification.identityType());
        assertEquals("member-1", classification.identity());
        assertEquals("app-9", classification.appId());
        assertEquals("group-open", classification.groupOpenid());
    }

    @Test
    void officialChannelEventUsesMemberOpenid() {
        MessagingIdentityClassifier.Classification classification = MessagingIdentityClassifier.classifyEvent(
                MilkyConnectionProtocol.OFFICIAL, "channel", "member-1", "channel-open", "app-9");
        assertEquals(MessagingIdentityType.MEMBER_OPENID, classification.identityType());
        assertEquals("channel-open", classification.groupOpenid());
    }

    @Test
    void officialPrivateEventUsesUserOpenid() {
        MessagingIdentityClassifier.Classification classification = MessagingIdentityClassifier.classifyEvent(
                MilkyConnectionProtocol.OFFICIAL, "friend", "user-open", null, "app-9");
        assertEquals(MessagingIdentityType.USER_OPENID, classification.identityType());
        assertNull(classification.groupOpenid());
    }

    @Test
    void officialGroupEventRequiresGroupOpenid() {
        assertThrows(BizException.class, () -> MessagingIdentityClassifier.classifyEvent(
                MilkyConnectionProtocol.OFFICIAL, "group", "member-1", null, "app-9"));
    }

    @Test
    void bindRejectsOfficialOpenidAsMilkyQq() {
        assertThrows(BizException.class, () -> MessagingIdentity.bind(1L, MilkyConnectionProtocol.MILKY, null, null,
                MessagingIdentityType.QQ, "user-openid", null));
    }
}
