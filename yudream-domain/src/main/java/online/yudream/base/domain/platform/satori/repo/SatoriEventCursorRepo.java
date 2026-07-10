package online.yudream.base.domain.platform.satori.repo;

import online.yudream.base.domain.platform.satori.aggregate.SatoriEventCursor;

import java.util.Optional;

public interface SatoriEventCursorRepo {
    Optional<SatoriEventCursor> findByConnectionId(Long connectionId);
    SatoriEventCursor save(SatoriEventCursor cursor);
}
