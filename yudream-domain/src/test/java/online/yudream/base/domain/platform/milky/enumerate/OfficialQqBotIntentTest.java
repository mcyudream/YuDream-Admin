package online.yudream.base.domain.platform.milky.enumerate;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OfficialQqBotIntentTest {

    @Test
    void recommendedMaskKeepsHistoricalDefaultBits() {
        assertEquals((1 << 25) | (1 << 26) | (1 << 30), OfficialQqBotIntent.recommendedMask());
        assertEquals(OfficialQqBotIntent.recommendedMask(), MilkyConnectionProtocol.DEFAULT_OFFICIAL_INTENTS);
    }

    @Test
    void decodeAndMaskRoundTrip() {
        int mask = OfficialQqBotIntent.mask(List.of(
                OfficialQqBotIntent.GROUP_AND_C2C_EVENT,
                OfficialQqBotIntent.INTERACTION,
                OfficialQqBotIntent.GUILDS));
        List<OfficialQqBotIntent> decoded = OfficialQqBotIntent.decode(mask);
        assertEquals(3, decoded.size());
        assertTrue(decoded.contains(OfficialQqBotIntent.GROUP_AND_C2C_EVENT));
        assertTrue(decoded.contains(OfficialQqBotIntent.INTERACTION));
        assertTrue(decoded.contains(OfficialQqBotIntent.GUILDS));
        assertFalse(decoded.contains(OfficialQqBotIntent.PUBLIC_GUILD_MESSAGES));
        assertEquals(mask, OfficialQqBotIntent.mask(decoded));
    }

    @Test
    void allMaskIncludesEveryDocumentedIntent() {
        int mask = OfficialQqBotIntent.allMask();
        for (OfficialQqBotIntent intent : OfficialQqBotIntent.values()) {
            assertTrue(intent.selected(mask), intent.name());
        }
    }
}
