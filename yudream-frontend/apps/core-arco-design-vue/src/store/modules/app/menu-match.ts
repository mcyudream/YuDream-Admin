interface MenuMatchNode {
  path?: string
  children?: MenuMatchNode[]
}

interface MenuMatchGroup {
  children?: MenuMatchNode[]
}

interface MatchScore {
  exact: boolean
  pathLength: number
}

function isBetterMatch(score: MatchScore, current?: MatchScore) {
  return !current
    || (score.exact && !current.exact)
    || (score.exact === current.exact && score.pathLength > current.pathLength)
}

function getBestMatchScore(nodes: MenuMatchNode[], path: string): MatchScore | undefined {
  let bestScore: MatchScore | undefined

  for (const node of nodes) {
    if (node.path && (path === node.path || path.startsWith(`${node.path}/`))) {
      const score = {
        exact: path === node.path,
        pathLength: node.path.length,
      }
      if (isBetterMatch(score, bestScore)) {
        bestScore = score
      }
    }

    const childScore = getBestMatchScore(node.children ?? [], path)
    if (childScore && isBetterMatch(childScore, bestScore)) {
      bestScore = childScore
    }
  }

  return bestScore
}

export function findBestMenuGroupIndex(groups: MenuMatchGroup[], path: string): number {
  let bestGroupIndex = -1
  let bestScore: MatchScore | undefined

  groups.forEach((group, index) => {
    const score = getBestMatchScore(group.children ?? [], path)
    if (score && isBetterMatch(score, bestScore)) {
      bestGroupIndex = index
      bestScore = score
    }
  })

  return bestGroupIndex
}
