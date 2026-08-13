package online.yudream.base.infra.system.log.service;

import online.yudream.base.domain.system.log.model.SystemLogLevel;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DockerContainerLogSourceTest {

    @Test
    void infersErrorLevelFromKeywords() {
        assertEquals(SystemLogLevel.ERROR, DockerContainerLogSource.inferLevel("request failed with exception"));
        assertEquals(SystemLogLevel.ERROR, DockerContainerLogSource.inferLevel("panic: fatal error"));
    }

    @Test
    void infersWarnLevel() {
        assertEquals(SystemLogLevel.WARN, DockerContainerLogSource.inferLevel("WARN: low memory"));
    }

    @Test
    void infersInfoLevelByDefault() {
        assertEquals(SystemLogLevel.INFO, DockerContainerLogSource.inferLevel("started listening on :8080"));
    }

    @Test
    void parsesCommaSeparatedContainers() {
        assertEquals(List.of("a", "b", "c"), DockerContainerLogSource.parseContainers(" a, b ,c"));
    }

    @Test
    void parsesEmptyContainers() {
        assertTrue(DockerContainerLogSource.parseContainers("  ").isEmpty());
        assertTrue(DockerContainerLogSource.parseContainers(null).isEmpty());
    }
}
