package online.yudream.base.interfaces.platform.wiki.request;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class WikiChatRequestValidationTest {

    private static Validator validator;

    @BeforeAll
    static void setUp() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    @Test
    void questionWithinLimitIsValid() {
        WikiChatRequest request = new WikiChatRequest();
        request.setQuestion("a".repeat(4000));

        assertThat(validator.validate(request)).isEmpty();
    }

    @Test
    void questionOverLimitIsRejected() {
        WikiChatRequest request = new WikiChatRequest();
        request.setQuestion("a".repeat(4001));

        assertThat(validator.validate(request)).isNotEmpty();
    }

    @Test
    void historyWithinLimitIsValid() {
        WikiChatRequest request = new WikiChatRequest();
        request.setQuestion("问题");
        request.setHistory(Collections.nCopies(10, turn("user", "回答")));

        assertThat(validator.validate(request)).isEmpty();
    }

    @Test
    void historyOverLimitIsRejected() {
        WikiChatRequest request = new WikiChatRequest();
        request.setQuestion("问题");
        request.setHistory(Collections.nCopies(11, turn("user", "回答")));

        assertThat(validator.validate(request)).isNotEmpty();
    }

    @Test
    void nestedTurnRoleOverLimitIsRejected() {
        WikiChatRequest request = new WikiChatRequest();
        request.setQuestion("问题");
        request.setHistory(List.of(turn("r".repeat(21), "回答")));

        assertThat(validator.validate(request)).isNotEmpty();
    }

    @Test
    void nestedTurnContentOverLimitIsRejected() {
        WikiChatRequest request = new WikiChatRequest();
        request.setQuestion("问题");
        request.setHistory(List.of(turn("user", "c".repeat(8001))));

        assertThat(validator.validate(request)).isNotEmpty();
    }

    private WikiChatRequest.ChatTurn turn(String role, String content) {
        WikiChatRequest.ChatTurn turn = new WikiChatRequest.ChatTurn();
        turn.setRole(role);
        turn.setContent(content);
        return turn;
    }
}
