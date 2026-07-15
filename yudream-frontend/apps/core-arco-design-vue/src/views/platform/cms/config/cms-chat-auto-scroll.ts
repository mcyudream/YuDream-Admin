interface ChatScrollViewport {
  scrollTop: number
  clientHeight: number
  scrollHeight: number
}

export function isNearChatBottom(viewport: ChatScrollViewport, threshold = 64) {
  return viewport.scrollHeight - viewport.clientHeight - viewport.scrollTop <= threshold
}

export function scrollChatToBottom(viewport: ChatScrollViewport) {
  viewport.scrollTop = viewport.scrollHeight
}
