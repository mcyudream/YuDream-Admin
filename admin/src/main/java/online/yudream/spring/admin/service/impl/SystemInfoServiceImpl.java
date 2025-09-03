package online.yudream.spring.admin.service.impl;

import online.yudream.spring.admin.service.SystemInfoService;
import online.yudream.spring.entity.system.*;
import org.springframework.stereotype.Service;
import oshi.SystemInfo;
import oshi.hardware.CentralProcessor;
import oshi.hardware.GlobalMemory;
import oshi.hardware.HardwareAbstractionLayer;
import oshi.software.os.OSFileStore;
import oshi.software.os.OperatingSystem;
import oshi.util.Util;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

@Service
public class SystemInfoServiceImpl implements SystemInfoService {
    private static final int WAIT_TIME_MS = 1000;

    @Override
    public SystemData getSystemInfo() {
        SystemInfo systemInfo = new SystemInfo();
        return SystemData.builder()
                .cpuInfo(getCpuInfo(systemInfo))
                .memoryInfo(getMemoryInfo(systemInfo))
                .diskInfo(getDiskInfo(systemInfo))
                .javaVmInfo(getJavaVmInfo(systemInfo))
                .serverInfo(getServerInfo(systemInfo))
                .build();
    }

    private JavaVmInfo getJavaVmInfo(SystemInfo systemInfo) {
        OperatingSystem os = systemInfo.getOperatingSystem();
        return JavaVmInfo.builder()
                .installPath(System.getProperty("java.home"))
                .runParameters(List.of(System.getProperty("sun.java.command").split(" ")))
                .startTime(LocalDateTime.ofInstant(Instant.ofEpochMilli(os.getSystemBootTime()), ZoneId.systemDefault()))
                .javaVersion(System.getProperty("java.version"))
                .javaName(System.getProperty("java.vm.name"))
                .build();
    }

    private ServerInfo getServerInfo(SystemInfo systemInfo) {
        OperatingSystem os = systemInfo.getOperatingSystem();
        return ServerInfo.builder()
                .serverIp(os.getNetworkParams().getIpv4DefaultGateway())
                .osName(os.toString())
                .serverName(os.getNetworkParams().getHostName())
                .build();
    }

    private MemoryInfo getMemoryInfo(SystemInfo systemInfo) {
        HardwareAbstractionLayer hal = systemInfo.getHardware();
        GlobalMemory memory = hal.getMemory();
        return MemoryInfo.builder()
                .freeMemory(memory.getAvailable()/ (1024*1024* 1024.0))
                .totalMemory(memory.getTotal()/ (1024.0 *1024*1024))
                .usedMemory((memory.getTotal() - memory.getAvailable() )/(1024.0 *1024*1024))
                .usageRate((100.0 * (memory.getTotal()-memory.getAvailable())) / memory.getTotal())
                 .build();
    }

    private List<DiskInfo> getDiskInfo(SystemInfo systemInfo) {
        OperatingSystem os = systemInfo.getOperatingSystem();
        List<OSFileStore> fileStores = os.getFileSystem().getFileStores();
        List<DiskInfo> diskInfos = new ArrayList<>();
        for (OSFileStore fs : fileStores) {
            long usable = fs.getUsableSpace();
            long total = fs.getTotalSpace();
            diskInfos.add(DiskInfo.builder()
                            .availableSize(usable)
                            .totalSize(total)
                            .diskPath(fs.getMount())
                            .diskType(fs.getDescription())
                            .fileSystem(fs.getType())
                            .usedPercentage((double) usable / total)
                    .build());
        }
        return diskInfos;
    }

    private CpuInfo getCpuInfo(SystemInfo systemInfo) {
        HardwareAbstractionLayer hal = systemInfo.getHardware();
        CentralProcessor processor = hal.getProcessor();
        // CPU信息
        long[] prevTicks = processor.getSystemCpuLoadTicks();
        Util.sleep(WAIT_TIME_MS);
        long[] ticks = processor.getSystemCpuLoadTicks();
        long cSys = ticks[CentralProcessor.TickType.SYSTEM.getIndex()] - prevTicks[CentralProcessor.TickType.SYSTEM.getIndex()];
        long user = ticks[CentralProcessor.TickType.USER.getIndex()] - prevTicks[CentralProcessor.TickType.USER.getIndex()];
        long idle = ticks[CentralProcessor.TickType.IDLE.getIndex()] - prevTicks[CentralProcessor.TickType.IDLE.getIndex()];
        long total = cSys + user + idle;
        return CpuInfo.builder()
                .coreCount(processor.getLogicalProcessorCount())
                .systemUsageRate(100.0*cSys/total)
                .userUsageRate(100.0*user/total)
                .free(100.0*idle/total)
                .build();
    }

}
