import React, { useState } from 'react';
import { ActivityIndicator, Pressable, StyleSheet, type ViewStyle } from 'react-native';
import { useTheme } from '@/core/theme/ThemeProvider';
import { YdText } from './YdText';

type Variant = 'primary' | 'secondary' | 'ghost' | 'danger';

/**
 * 按钮：最小触控 44pt、pressed 反馈走 fill/accent 变体 token。
 */
export function YdButton({
  title,
  onPress,
  variant = 'primary',
  loading = false,
  disabled = false,
  style,
}: {
  title: string;
  onPress?: () => void;
  variant?: Variant;
  loading?: boolean;
  disabled?: boolean;
  style?: ViewStyle;
}) {
  const t = useTheme();
  const [pressed, setPressed] = useState(false);
  const blocked = disabled || loading;

  const palette: Record<Variant, { bg: string; bgPressed: string; text: string; border?: string }> = {
    primary: {
      bg: t.colors.accent,
      bgPressed: t.colors.accentPressed,
      text: t.colors.onAccent,
    },
    secondary: {
      bg: 'transparent',
      bgPressed: t.colors.fillPressed,
      text: t.colors.textPrimary,
      border: t.colors.borderStrong,
    },
    ghost: {
      bg: 'transparent',
      bgPressed: t.colors.fillPressed,
      text: t.colors.textLink,
    },
    danger: {
      bg: t.colors.danger,
      bgPressed: t.colors.fillPressed,
      text: t.colors.onAccent,
    },
  };
  const p = palette[variant];

  return (
    <Pressable
      accessibilityRole="button"
      accessibilityState={{ disabled: blocked, busy: loading }}
      disabled={blocked}
      onPress={onPress}
      onPressIn={() => setPressed(true)}
      onPressOut={() => setPressed(false)}
      style={[
        styles.base,
        {
          minHeight: 44,
          borderRadius: t.radii.md,
          backgroundColor: pressed ? p.bgPressed : p.bg,
          borderColor: p.border,
          borderWidth: p.border ? StyleSheet.hairlineWidth : 0,
          opacity: blocked ? 0.5 : 1,
          paddingHorizontal: t.spacing.lg,
        },
        style,
      ]}
    >
      {loading
        ? <ActivityIndicator color={p.text} />
        : <YdText style={{ color: p.text, fontWeight: t.typography.weightMedium }}>{title}</YdText>}
    </Pressable>
  );
}

const styles = StyleSheet.create({
  base: { alignItems: 'center', justifyContent: 'center' },
});
