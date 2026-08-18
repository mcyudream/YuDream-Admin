export function authoritativeAguiText(current: string | undefined, value: unknown): string {
  return typeof value === 'string' ? value : current ?? ''
}
