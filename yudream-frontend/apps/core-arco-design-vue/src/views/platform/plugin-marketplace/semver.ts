export interface ParsedSemVer {
  major: number
  minor: number
  patch: number
  prerelease: string[]
}

const SEMVER_PATTERN = /^(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)(?:-([0-9a-z-]+(?:\.[0-9a-z-]+)*))?(?:\+[0-9a-z-]+(?:\.[0-9a-z-]+)*)?$/i

export function parseSemVer(version?: string): ParsedSemVer | undefined {
  if (!version) {
    return undefined
  }
  const match = SEMVER_PATTERN.exec(version)
  if (!match) {
    return undefined
  }
  return {
    major: Number(match[1]),
    minor: Number(match[2]),
    patch: Number(match[3]),
    prerelease: match[4]?.split('.') || [],
  }
}

export function compareSemVer(left: string, right: string): number | undefined {
  const leftVersion = parseSemVer(left)
  const rightVersion = parseSemVer(right)
  if (!leftVersion || !rightVersion) {
    return undefined
  }
  for (const key of ['major', 'minor', 'patch'] as const) {
    if (leftVersion[key] !== rightVersion[key]) {
      return leftVersion[key] > rightVersion[key] ? 1 : -1
    }
  }
  if (!leftVersion.prerelease.length || !rightVersion.prerelease.length) {
    if (leftVersion.prerelease.length === rightVersion.prerelease.length) {
      return 0
    }
    return leftVersion.prerelease.length ? -1 : 1
  }
  const length = Math.max(leftVersion.prerelease.length, rightVersion.prerelease.length)
  for (let index = 0; index < length; index += 1) {
    const leftPart = leftVersion.prerelease[index]
    const rightPart = rightVersion.prerelease[index]
    if (leftPart === undefined || rightPart === undefined) {
      return leftPart === undefined ? -1 : 1
    }
    if (leftPart === rightPart) {
      continue
    }
    const leftNumeric = /^\d+$/.test(leftPart)
    const rightNumeric = /^\d+$/.test(rightPart)
    if (leftNumeric && rightNumeric) {
      return Number(leftPart) > Number(rightPart) ? 1 : -1
    }
    if (leftNumeric !== rightNumeric) {
      return leftNumeric ? -1 : 1
    }
    return leftPart > rightPart ? 1 : -1
  }
  return 0
}
