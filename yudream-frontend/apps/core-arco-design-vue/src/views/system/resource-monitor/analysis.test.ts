import assert from 'node:assert/strict'
import test from 'node:test'
import type { HostResourceSnapshot } from '@/api/modules/system-monitor'
import { analyzeResource } from './analysis'

test('points at the other process when host memory is high but current JVM is not the top RSS', () => {
  const snapshot: HostResourceSnapshot = {
    memoryUsedBytes: 15 * 1024 ** 3,
    memoryTotalBytes: 16 * 1024 ** 3,
    memoryUsagePercent: 93.8,
    jvmHeapUsedBytes: 512 * 1024 ** 2,
    jvmHeapMaxBytes: 2 * 1024 ** 3,
    disks: [],
    networks: [],
    memoryPools: [{ name: 'G1 Eden Space', type: 'HEAP', usedBytes: 200 * 1024 ** 2 }],
    garbageCollectors: [],
    topProcesses: [
      { pid: 22, name: 'mysqld', rssBytes: 8 * 1024 ** 3, cpuPercent: 12, currentJvm: false },
      { pid: 88, name: 'java', rssBytes: 900 * 1024 ** 2, cpuPercent: 8, currentJvm: true },
    ],
    topThreads: [],
  }

  const result = analyzeResource(snapshot, [])

  assert.equal(result.severity, 'warn')
  assert.match(result.headline, /mysqld/)
  assert.ok(result.findings.some(item => item.id === 'host-memory'))
  assert.equal(result.memorySpenders[0]?.name, 'mysqld #22')
})

test('explains RSS much larger than heap as native or off-heap spend', () => {
  const snapshot: HostResourceSnapshot = {
    memoryUsedBytes: 6 * 1024 ** 3,
    memoryTotalBytes: 16 * 1024 ** 3,
    jvmHeapUsedBytes: 400 * 1024 ** 2,
    jvmHeapMaxBytes: 2 * 1024 ** 3,
    jvmNonHeapUsedBytes: 80 * 1024 ** 2,
    jvmDirectUsedBytes: 300 * 1024 ** 2,
    disks: [],
    networks: [],
    memoryPools: [{ name: 'Metaspace', type: 'NON_HEAP', usedBytes: 80 * 1024 ** 2 }],
    garbageCollectors: [],
    topProcesses: [
      { pid: 88, name: 'java', rssBytes: 3 * 1024 ** 3, cpuPercent: 20, currentJvm: true },
    ],
    topThreads: [],
  }

  const result = analyzeResource(snapshot, [])

  assert.ok(result.findings.some(item => item.id === 'rss'))
  assert.match(result.headline, /物理内存/)
})

test('stays calm when host and program usage are moderate', () => {
  const snapshot: HostResourceSnapshot = {
    cpuUsagePercent: 18,
    memoryUsedBytes: 6 * 1024 ** 3,
    memoryTotalBytes: 16 * 1024 ** 3,
    memoryUsagePercent: 37.5,
    jvmHeapUsedBytes: 400 * 1024 ** 2,
    jvmHeapMaxBytes: 2 * 1024 ** 3,
    diskUsagePercent: 41,
    disks: [],
    networks: [],
    memoryPools: [],
    garbageCollectors: [],
    topProcesses: [
      { pid: 88, name: 'java', rssBytes: 700 * 1024 ** 2, cpuPercent: 5, currentJvm: true },
    ],
    topThreads: [{ threadId: '1', name: 'http-nio-8080-exec-1', state: 'WAITING', cpuTimeMs: 1200 }],
  }

  const result = analyzeResource(snapshot, [])

  assert.equal(result.severity, 'ok')
  assert.equal(result.findings.length, 0)
  assert.match(result.headline, /平稳/)
})
