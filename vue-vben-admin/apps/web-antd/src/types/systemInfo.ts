
export interface SystemInfo {
  cpuInfo?: CpuInfo;
  memoryInfo?: MemoryInfo;
  diskInfo?: DiskInfo[];
  javaVmInfo?: JavaVmInfo;
  serverInfo?: ServerInfo;
}

export interface CpuInfo {
  coreCount: number;
  userUsageRate: number;
  systemUsageRate: number;
  free: number;
}

export interface JavaVmInfo{
  javaName: string;
  javaVersion: string;
  startTime: Date;
  installPath: string;
  runParameters: string[];
}

export interface MemoryInfo{
  totalMemory: number;
  usedMemory: number;
  freeMemory: number;
  usageRate: number;
}

export interface DiskInfo{
  diskPath: string;
  fileSystem: string;
  diskType: string;
  totalSize: number;
  availableSize: number;
  usedSize: number;
  usedPercentage: number;
}

export interface ServerInfo{
  serverName: string;
  osName: string;
  serverIp: string;
}
