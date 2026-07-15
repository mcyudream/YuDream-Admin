package online.yudream.base.infra.platform.milky.service;

/** Internal signal used to close all Milky sessions after the capability is disabled. */
public record MilkyRuntimeShutdownRequested() {
}
