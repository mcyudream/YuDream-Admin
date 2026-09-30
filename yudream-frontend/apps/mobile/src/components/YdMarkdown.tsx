import { Image } from 'react-native';
/**
 * 统一 Markdown 渲染（宿主权威实现）：T0 token 全量映射样式。
 * 插件侧使用同源副本（视觉一致），宿主页面直接复用本组件。
 */
import React, { useMemo } from 'react';
import Markdown from '@ronradtke/react-native-markdown-display';
import type { PluginThemeTokens } from '@yudream/plugin-sdk-mobile';
import type { ThemeTokens } from '@/core/theme/tokens';

let currentTokens: PluginThemeTokens | null = null;

/** 宿主在主题装载/切换时调用；此后所有 YdMarkdown 按当前主题渲染。 */
export function setMarkdownTheme(tokens: ThemeTokens): void {
  currentTokens = tokens as unknown as PluginThemeTokens;
}

function color(tokens: PluginThemeTokens | null, key: string, fallback: string): string {
  return tokens?.colors?.[key] ?? fallback;
}

function buildStyles(tokens: PluginThemeTokens | null): Record<string, Record<string, unknown>> {
  const textPrimary = color(tokens, 'textPrimary', '#0a0a0a');
  const textSecondary = color(tokens, 'textSecondary', '#737373');
  const textTertiary = color(tokens, 'textTertiary', '#a3a3a3');
  const accent = color(tokens, 'accent', '#171717');
  const borderSubtle = color(tokens, 'borderSubtle', '#e5e5e5');
  const bgSurface = color(tokens, 'bgSurface', '#ffffff');
  const fillHover = color(tokens, 'fillHover', 'rgba(23,23,23,0.05)');
  const spacing = tokens?.spacing ?? { xs: 4, sm: 8, md: 12, lg: 16, xl: 24 };

  return {
    body: { color: textPrimary, fontSize: 15, lineHeight: 25 },
    heading1: { color: textPrimary, fontSize: 22, fontWeight: '700', marginTop: spacing.lg, marginBottom: spacing.sm },
    heading2: { color: textPrimary, fontSize: 19, fontWeight: '700', marginTop: spacing.lg, marginBottom: spacing.sm },
    heading3: { color: textPrimary, fontSize: 17, fontWeight: '700', marginTop: spacing.md, marginBottom: spacing.sm },
    heading4: { color: textPrimary, fontSize: 15, fontWeight: '700', marginTop: spacing.md },
    heading5: { color: textPrimary, fontSize: 15, fontWeight: '600', marginTop: spacing.md },
    heading6: { color: textSecondary, fontSize: 14, fontWeight: '600', marginTop: spacing.md },
    strong: { color: textPrimary, fontWeight: '700' },
    em: { fontStyle: 'italic' },
    s: { color: textTertiary, textDecorationLine: 'line-through' },
    link: { color: accent, textDecorationLine: 'underline' },
    blocklink: { color: accent },
    list_item: { marginBottom: spacing.xs },
    bullet_list_icon: { color: textTertiary, marginLeft: 0, marginRight: 8 },
    ordered_list_icon: { color: textTertiary, marginLeft: 0, marginRight: 8 },
    code_inline: {
      color: accent,
      backgroundColor: fillHover,
      fontSize: 13,
      fontStyle: 'normal',
      paddingHorizontal: 4,
      borderRadius: 4,
    },
    fence: {
      backgroundColor: bgSurface,
      borderColor: borderSubtle,
      borderWidth: 1,
      borderRadius: 8,
      padding: spacing.md,
      color: textPrimary,
      fontSize: 12,
    },
    blockquote: {
      backgroundColor: fillHover,
      borderLeftColor: accent,
      borderLeftWidth: 3,
      paddingHorizontal: spacing.md,
      paddingVertical: spacing.sm,
      marginVertical: spacing.sm,
    },
    hr: { backgroundColor: borderSubtle, height: 1, marginVertical: spacing.md },
    table: { borderColor: borderSubtle, borderWidth: 1, borderRadius: 8 },
    thead: { backgroundColor: fillHover },
    th: { color: textSecondary, fontWeight: '600', padding: spacing.sm, borderColor: borderSubtle },
    td: { color: textPrimary, padding: spacing.sm, borderColor: borderSubtle },
    image: { width: '100%', height: 210, resizeMode: 'contain', backgroundColor: bgSurface, borderRadius: 8, marginVertical: spacing.sm },
    paragraph: { marginTop: 0, marginBottom: spacing.sm },
    quote: {},
  };
}

export interface YdMarkdownProps {
  /** Markdown 源文本 */
  source: string;
}

export function YdMarkdown({ source }: YdMarkdownProps) {
  const styles = useMemo(() => buildStyles(currentTokens), [currentTokens]);
  return (
    <Markdown
      style={styles}
      rules={{
        image: (node) => (
          <FitImage
            key={node.key}
            uri={String(node.attributes?.src ?? '')}
            style={styles.image as Record<string, unknown>}
          />
        ),
      }}
    >
      {source}
    </Markdown>
  );
}

/** 图片自适应：onLoad 读取原始宽高比动态撑高，杜绝截断与空占位。 */
function FitImage({
  uri,
  style,
}: {
  uri: string;
  style?: Record<string, unknown>;
}) {
  const [ratio, setRatio] = React.useState(16 / 9);
  return (
    <Image
      source={{ uri }}
      style={[{ width: '100%', aspectRatio: ratio, resizeMode: 'contain', borderRadius: 8 }, style]}
      onLoad={(e) => {
        const src = (e as { nativeEvent?: { source?: { width?: number; height?: number } } }).nativeEvent?.source;
        if (src?.width && src?.height) setRatio(src.width / src.height);
      }}
    />
  );
}
