package online.yudream.base.infra.system.monitor;

import lombok.extern.slf4j.Slf4j;
import online.yudream.base.domain.system.monitor.dto.HostDiskDTO;
import online.yudream.base.domain.system.monitor.dto.HostNetworkDTO;
import online.yudream.base.domain.system.monitor.dto.HostProcessDTO;
import online.yudream.base.domain.system.monitor.dto.HostResourceSnapshotDTO;
import online.yudream.base.domain.system.monitor.dto.HostThreadHotspotDTO;
import online.yudream.base.domain.system.monitor.dto.JvmGcDTO;
import online.yudream.base.domain.system.monitor.dto.JvmMemoryPoolDTO;
import online.yudream.base.domain.system.monitor.service.HostResourceGateway;
import org.springframework.stereotype.Service;
import oshi.SystemInfo;
import oshi.hardware.CentralProcessor;
import oshi.hardware.GlobalMemory;
import oshi.hardware.HardwareAbstractionLayer;
import oshi.hardware.NetworkIF;
import oshi.hardware.VirtualMemory;
import oshi.software.os.OSFileStore;
import oshi.software.os.OSProcess;
import oshi.software.os.OperatingSystem;
import oshi.software.os.OperatingSystem.ProcessSorting;

import java.lang.management.BufferPoolMXBean;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryUsage;
import java.lang.management.RuntimeMXBean;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class HostResourceGatewayImpl implements HostResourceGateway {

    static final String NOTICE = "指标来自当前进程可见的操作系统。容器部署时 CPU、内存、磁盘可能反映宿主机或 cgroup 视图。";
    private static final int TOP_PROCESS_LIMIT = 20;
    private static final int TOP_THREAD_LIMIT = 20;
    private static final int STACK_FRAME_LIMIT = 8;
    private static final int COMMAND_LINE_LIMIT = 240;
    private static final long CPU_SAMPLE_WAIT_MS = 220L;

    private final SystemInfo systemInfo = new SystemInfo();
    private final Object cpuLock = new Object();
    private final Object netLock = new Object();

    private long[] previousCpuTicks;
    private long previousCpuSampleAtMs;
    private Map<String, NetworkSnapshot> previousNetworks = Map.of();
    private long previousNetworkSampleAtMs;

    @Override
    public HostResourceSnapshotDTO snapshot() {
        HardwareAbstractionLayer hardware = systemInfo.getHardware();
        OperatingSystem os = systemInfo.getOperatingSystem();
        CentralProcessor processor = hardware.getProcessor();
        GlobalMemory memory = hardware.getMemory();
        VirtualMemory swap = memory.getVirtualMemory();

        CpuSample cpu = sampleCpu(processor);
        List<HostDiskDTO> disks = disks(os);
        NetworkSample network = sampleNetwork(safeNetworkIfs(hardware));
        List<HostProcessDTO> processes = topProcesses(os);
        List<JvmMemoryPoolDTO> pools = memoryPools();
        BufferUsage direct = directBuffer();
        MemoryMXBean memoryMx = ManagementFactory.getMemoryMXBean();
        RuntimeMXBean runtimeMx = ManagementFactory.getRuntimeMXBean();
        ThreadMXBean threadMx = ManagementFactory.getThreadMXBean();
        MemoryUsage heap = memoryMx.getHeapMemoryUsage();
        MemoryUsage nonHeap = memoryMx.getNonHeapMemoryUsage();
        long diskTotal = disks.stream().mapToLong(item -> zero(item.getTotalBytes())).sum();
        long diskUsed = disks.stream().mapToLong(item -> zero(item.getUsedBytes())).sum();

        return HostResourceSnapshotDTO.builder()
                .hostname(os.getNetworkParams().getHostName())
                .osName(os.getFamily())
                .osVersion(os.getVersionInfo() == null ? "" : os.getVersionInfo().getVersion())
                .architecture(System.getProperty("os.arch", ""))
                .cpuName(processor.getProcessorIdentifier().getName())
                .cpuPhysicalCount(processor.getPhysicalProcessorCount())
                .cpuLogicalCount(processor.getLogicalProcessorCount())
                .cpuUsagePercent(round(cpu.usagePercent()))
                .load1(loadAverage(processor, 0))
                .load5(loadAverage(processor, 1))
                .load15(loadAverage(processor, 2))
                .memoryTotalBytes(memory.getTotal())
                .memoryUsedBytes(memory.getTotal() - memory.getAvailable())
                .memoryAvailableBytes(memory.getAvailable())
                .memoryUsagePercent(percent(memory.getTotal() - memory.getAvailable(), memory.getTotal()))
                .swapTotalBytes(swap.getSwapTotal())
                .swapUsedBytes(swap.getSwapUsed())
                .disks(disks)
                .diskTotalBytes(diskTotal)
                .diskUsedBytes(diskUsed)
                .diskUsagePercent(percent(diskUsed, diskTotal))
                .networks(network.interfaces())
                .networkRecvBytesPerSec(network.recvBytesPerSec())
                .networkSentBytesPerSec(network.sentBytesPerSec())
                .jvmPid((long) os.getProcessId())
                .javaVersion(System.getProperty("java.version", ""))
                .jvmUptimeMs(runtimeMx.getUptime())
                .jvmHeapUsedBytes(heap.getUsed())
                .jvmHeapMaxBytes(heap.getMax() < 0 ? heap.getCommitted() : heap.getMax())
                .jvmNonHeapUsedBytes(nonHeap.getUsed())
                .jvmNonHeapMaxBytes(nonHeap.getMax() < 0 ? nonHeap.getCommitted() : nonHeap.getMax())
                .jvmDirectUsedBytes(direct.used())
                .jvmDirectMaxBytes(direct.max())
                .jvmThreadCount(threadMx.getThreadCount())
                .jvmDaemonThreadCount(threadMx.getDaemonThreadCount())
                .jvmLoadedClassCount((long) ManagementFactory.getClassLoadingMXBean().getLoadedClassCount())
                .memoryPools(pools)
                .garbageCollectors(garbageCollectors())
                .topProcesses(processes)
                .topThreads(topThreads())
                .notice(NOTICE)
                .sampledAt(LocalDateTime.now())
                .build();
    }

    private CpuSample sampleCpu(CentralProcessor processor) {
        synchronized (cpuLock) {
            long now = System.currentTimeMillis();
            long[] current = processor.getSystemCpuLoadTicks();
            if (previousCpuTicks == null) {
                previousCpuTicks = current;
                previousCpuSampleAtMs = now;
                sleepQuietly(CPU_SAMPLE_WAIT_MS);
                current = processor.getSystemCpuLoadTicks();
                now = System.currentTimeMillis();
            }
            double load = processor.getSystemCpuLoadBetweenTicks(previousCpuTicks);
            if (now - previousCpuSampleAtMs > TimeUnit.MINUTES.toMillis(2)) {
                sleepQuietly(CPU_SAMPLE_WAIT_MS);
                long[] refreshed = processor.getSystemCpuLoadTicks();
                load = processor.getSystemCpuLoadBetweenTicks(current);
                previousCpuTicks = refreshed;
            } else {
                previousCpuTicks = current;
            }
            previousCpuSampleAtMs = now;
            return new CpuSample(clampPercent(load * 100));
        }
    }

    private NetworkSample sampleNetwork(List<NetworkIF> interfaces) {
        synchronized (netLock) {
            long now = System.currentTimeMillis();
            Map<String, NetworkSnapshot> current = new HashMap<>();
            List<HostNetworkDTO> rows = new ArrayList<>();
            long elapsedMs = previousNetworkSampleAtMs == 0 ? 0 : Math.max(1, now - previousNetworkSampleAtMs);
            if (previousNetworks.isEmpty()) {
                elapsedMs = 0;
            }
            long recvRate = 0;
            long sentRate = 0;
            for (NetworkIF nic : interfaces) {
                nic.updateAttributes();
                if (skipNic(nic)) {
                    continue;
                }
                NetworkSnapshot previous = previousNetworks.get(nic.getName());
                long recvPerSec = rate(nic.getBytesRecv(), previous == null ? null : previous.recvBytes(), elapsedMs);
                long sentPerSec = rate(nic.getBytesSent(), previous == null ? null : previous.sentBytes(), elapsedMs);
                recvRate += recvPerSec;
                sentRate += sentPerSec;
                current.put(nic.getName(), new NetworkSnapshot(nic.getBytesRecv(), nic.getBytesSent()));
                rows.add(HostNetworkDTO.builder()
                        .name(nic.getName())
                        .displayName(nic.getDisplayName())
                        .ipv4(first(nic.getIPv4addr()))
                        .mac(nic.getMacaddr())
                        .recvBytes(nic.getBytesRecv())
                        .sentBytes(nic.getBytesSent())
                        .recvBytesPerSec(recvPerSec)
                        .sentBytesPerSec(sentPerSec)
                        .build());
            }
            previousNetworks = current;
            previousNetworkSampleAtMs = now;
            rows.sort(Comparator.comparingLong((HostNetworkDTO item) -> zero(item.getRecvBytesPerSec()) + zero(item.getSentBytesPerSec())).reversed());
            return new NetworkSample(rows, recvRate, sentRate);
        }
    }

    private List<HostDiskDTO> disks(OperatingSystem os) {
        List<HostDiskDTO> disks = new ArrayList<>();
        List<OSFileStore> stores;
        try {
            stores = os.getFileSystem().getFileStores();
        } catch (RuntimeException e) {
            log.warn("读取磁盘失败：{}", e.getMessage());
            return disks;
        }
        for (OSFileStore store : stores) {
            long total = store.getTotalSpace();
            if (total <= 0) {
                continue;
            }
            long usable = Math.max(0, store.getUsableSpace());
            long used = Math.max(0, total - usable);
            disks.add(HostDiskDTO.builder()
                    .name(store.getName())
                    .mount(store.getMount())
                    .type(store.getType())
                    .totalBytes(total)
                    .usedBytes(used)
                    .usableBytes(usable)
                    .usagePercent(percent(used, total))
                    .build());
        }
        disks.sort(Comparator.comparingLong((HostDiskDTO item) -> zero(item.getTotalBytes())).reversed());
        return disks;
    }

    private List<NetworkIF> safeNetworkIfs(HardwareAbstractionLayer hardware) {
        try {
            return hardware.getNetworkIFs(true);
        } catch (RuntimeException e) {
            log.warn("读取网卡失败：{}", e.getMessage());
            return List.of();
        }
    }

    private List<HostProcessDTO> topProcesses(OperatingSystem os) {
        try {
            int currentPid = os.getProcessId();
            List<OSProcess> processes = os.getProcesses(null, ProcessSorting.RSS_DESC, TOP_PROCESS_LIMIT);
            List<HostProcessDTO> rows = new ArrayList<>();
            for (OSProcess process : processes) {
                rows.add(HostProcessDTO.builder()
                        .pid(process.getProcessID())
                        .name(process.getName())
                        .user(process.getUser())
                        .rssBytes(process.getResidentSetSize())
                        .virtualBytes(process.getVirtualSize())
                        .cpuPercent(round(clampPercent(process.getProcessCpuLoadCumulative() * 100)))
                        .currentJvm(process.getProcessID() == currentPid)
                        .commandLine(truncate(process.getCommandLine(), COMMAND_LINE_LIMIT))
                        .build());
            }
            return rows;
        } catch (RuntimeException e) {
            log.warn("读取进程列表失败：{}", e.getMessage());
            return List.of();
        }
    }

    private List<JvmMemoryPoolDTO> memoryPools() {
        List<JvmMemoryPoolDTO> pools = new ArrayList<>();
        for (MemoryPoolMXBean pool : ManagementFactory.getMemoryPoolMXBeans()) {
            MemoryUsage usage = pool.getUsage();
            if (usage == null) {
                continue;
            }
            long max = usage.getMax() < 0 ? usage.getCommitted() : usage.getMax();
            pools.add(JvmMemoryPoolDTO.builder()
                    .name(pool.getName())
                    .type(pool.getType() == null ? "" : pool.getType().name())
                    .usedBytes(usage.getUsed())
                    .committedBytes(usage.getCommitted())
                    .maxBytes(max)
                    .usagePercent(percent(usage.getUsed(), max))
                    .build());
        }
        return pools;
    }

    private List<JvmGcDTO> garbageCollectors() {
        List<JvmGcDTO> collectors = new ArrayList<>();
        for (GarbageCollectorMXBean collector : ManagementFactory.getGarbageCollectorMXBeans()) {
            collectors.add(JvmGcDTO.builder()
                    .name(collector.getName())
                    .collectionCount(Math.max(0, collector.getCollectionCount()))
                    .collectionTimeMs(Math.max(0, collector.getCollectionTime()))
                    .build());
        }
        return collectors;
    }

    private List<HostThreadHotspotDTO> topThreads() {
        ThreadMXBean threadMx = ManagementFactory.getThreadMXBean();
        if (!threadMx.isThreadCpuTimeSupported()) {
            return List.of();
        }
        try {
            if (!threadMx.isThreadCpuTimeEnabled()) {
                threadMx.setThreadCpuTimeEnabled(true);
            }
        } catch (RuntimeException e) {
            log.warn("启用线程 CPU 计时失败：{}", e.getMessage());
            return List.of();
        }
        long[] ids = threadMx.getAllThreadIds();
        List<ThreadCpu> ranked = new ArrayList<>();
        for (long id : ids) {
            long cpuTime = threadMx.getThreadCpuTime(id);
            if (cpuTime <= 0) {
                continue;
            }
            ranked.add(new ThreadCpu(id, cpuTime, Math.max(0, threadMx.getThreadUserTime(id))));
        }
        ranked.sort(Comparator.comparingLong(ThreadCpu::cpuTimeNs).reversed());
        int limit = Math.min(TOP_THREAD_LIMIT, ranked.size());
        long[] topIds = ranked.stream().limit(limit).mapToLong(ThreadCpu::id).toArray();
        ThreadInfo[] infos = threadMx.getThreadInfo(topIds, STACK_FRAME_LIMIT);
        Map<Long, ThreadInfo> infoMap = new HashMap<>();
        if (infos != null) {
            for (ThreadInfo info : infos) {
                if (info != null) {
                    infoMap.put(info.getThreadId(), info);
                }
            }
        }
        List<HostThreadHotspotDTO> rows = new ArrayList<>();
        for (int i = 0; i < limit; i++) {
            ThreadCpu cpu = ranked.get(i);
            ThreadInfo info = infoMap.get(cpu.id());
            rows.add(HostThreadHotspotDTO.builder()
                    .threadId(cpu.id())
                    .name(info == null ? ("thread-" + cpu.id()) : info.getThreadName())
                    .state(info == null || info.getThreadState() == null ? "" : info.getThreadState().name())
                    .cpuTimeMs(TimeUnit.NANOSECONDS.toMillis(cpu.cpuTimeNs()))
                    .userTimeMs(TimeUnit.NANOSECONDS.toMillis(cpu.userTimeNs()))
                    .daemon(info != null && info.isDaemon())
                    .stackTop(stackTop(info))
                    .build());
        }
        return rows;
    }

    private static List<String> stackTop(ThreadInfo info) {
        if (info == null || info.getStackTrace() == null) {
            return List.of();
        }
        return Arrays.stream(info.getStackTrace())
                .limit(STACK_FRAME_LIMIT)
                .map(StackTraceElement::toString)
                .toList();
    }

    private static BufferUsage directBuffer() {
        long used = 0;
        long max = 0;
        for (BufferPoolMXBean pool : ManagementFactory.getPlatformMXBeans(BufferPoolMXBean.class)) {
            if (!"direct".equalsIgnoreCase(pool.getName())) {
                continue;
            }
            used += Math.max(0, pool.getMemoryUsed());
            max += Math.max(0, pool.getTotalCapacity());
        }
        return new BufferUsage(used, max);
    }

    private static boolean skipNic(NetworkIF nic) {
        String name = nic.getName() == null ? "" : nic.getName().toLowerCase();
        String display = nic.getDisplayName() == null ? "" : nic.getDisplayName().toLowerCase();
        if (name.startsWith("lo") || display.contains("loopback")) {
            return true;
        }
        return nic.getBytesRecv() == 0 && nic.getBytesSent() == 0 && (nic.getIPv4addr() == null || nic.getIPv4addr().length == 0);
    }

    private static Double loadAverage(CentralProcessor processor, int index) {
        double[] loads = processor.getSystemLoadAverage(3);
        if (loads == null || loads.length <= index || loads[index] < 0) {
            return null;
        }
        return round(loads[index]);
    }

    private static long rate(long current, Long previous, long elapsedMs) {
        if (previous == null || elapsedMs <= 0 || current < previous) {
            return 0L;
        }
        return Math.round((current - previous) * 1000.0 / elapsedMs);
    }

    private static Double percent(long used, long total) {
        if (total <= 0) {
            return 0D;
        }
        return round(used * 100.0 / total);
    }

    private static double clampPercent(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return 0D;
        }
        return Math.max(0D, Math.min(100D, value));
    }

    private static Double round(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    private static long zero(Long value) {
        return value == null ? 0L : value;
    }

    private static String first(String[] values) {
        return values == null || values.length == 0 ? "" : values[0];
    }

    private static String truncate(String value, int limit) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        return trimmed.length() <= limit ? trimmed : trimmed.substring(0, limit) + "...";
    }

    private static void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private record CpuSample(double usagePercent) {
    }

    private record NetworkSample(List<HostNetworkDTO> interfaces, long recvBytesPerSec, long sentBytesPerSec) {
    }

    private record NetworkSnapshot(long recvBytes, long sentBytes) {
    }

    private record BufferUsage(long used, long max) {
    }

    private record ThreadCpu(long id, long cpuTimeNs, long userTimeNs) {
    }
}
