package online.yudream.base.domain.shared;

public class SnowflakeIdGenerator implements IdGenerator {

    // 起始时间戳 (2024-01-01)
    private static final long START_TIMESTAMP = 1704067200000L;

    // 各部位位数
    private static final long DATA_CENTER_BITS = 5L;
    private static final long MACHINE_BITS = 5L;
    private static final long SEQUENCE_BITS = 12L;

    // 位移
    private static final long MACHINE_SHIFT = SEQUENCE_BITS;
    private static final long DATA_CENTER_SHIFT = SEQUENCE_BITS + MACHINE_BITS;
    private static final long TIMESTAMP_SHIFT = SEQUENCE_BITS + MACHINE_BITS + DATA_CENTER_BITS;

    // 最大值
    private static final long MAX_SEQUENCE = ~(-1L << SEQUENCE_BITS);

    private final long dataCenterId;
    private final long machineId;

    private long sequence = 0L;
    private long lastTimestamp = -1L;

    public SnowflakeIdGenerator(long dataCenterId, long machineId) {
        if (dataCenterId > 31 || dataCenterId < 0) {
            throw new IllegalArgumentException("dataCenterId 必须在 0-31 之间");
        }
        if (machineId > 31 || machineId < 0) {
            throw new IllegalArgumentException("machineId 必须在 0-31 之间");
        }
        this.dataCenterId = dataCenterId;
        this.machineId = machineId;
    }

    @Override
    public synchronized Long nextId() {
        long currentTimestamp = System.currentTimeMillis();

        if (currentTimestamp < lastTimestamp) {
            throw new RuntimeException("时钟回拨，拒绝生成 ID");
        }

        if (currentTimestamp == lastTimestamp) {
            sequence = (sequence + 1) & MAX_SEQUENCE;
            if (sequence == 0) {
                currentTimestamp = waitNextMillis(lastTimestamp);
            }
        } else {
            sequence = 0L;
        }

        lastTimestamp = currentTimestamp;

        return ((currentTimestamp - START_TIMESTAMP) << TIMESTAMP_SHIFT)
                | (dataCenterId << DATA_CENTER_SHIFT)
                | (machineId << MACHINE_SHIFT)
                | sequence;
    }

    private long waitNextMillis(long lastTimestamp) {
        long timestamp = System.currentTimeMillis();
        while (timestamp <= lastTimestamp) {
            timestamp = System.currentTimeMillis();
        }
        return timestamp;
    }
}
