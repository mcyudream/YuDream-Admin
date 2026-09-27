import React, { useState } from 'react';
import { ActivityIndicator, Pressable, StyleSheet, View, type ViewStyle } from 'react-native';
import Icon from 'react-native-vector-icons/Ionicons';
import { useTheme } from '@/core/theme/ThemeProvider';
import { YdText } from './YdText';

type Variant = 'primary' | 'secondary' | 'ghost' | 'danger' | 'hero';

/**
 * 按钮：最小触控 44pt、pressed 反馈走 fill/accent 变体 token。
 * hero：设计稿风格的胶囊主操作——高对比底色 + 右侧圆形箭头。
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

  const palette: Record<
    Variant,
    { bg: string; bgPressed: string; text: string; border?: string; radius: number; height: number }
  > = {
    primary: {
      bg: t.colors.accent,
      bgPressed: t.colors.accentPressed,
      text: t.colors.onAccent,
      radius: t.radii.md,
      height: 44,
    },
    secondary: {
      bg: 'transparent',
      bgPressed: t.colors.fillPressed,
      text: t.colors.textPrimary,
      border: t.colors.borderStrong,
      radius: t.radii.md,
      height: 44,
    },
    ghost: {
      bg: 'transparent',
      bgPressed: t.colors.fillPressed,
      text: t.colors.textLink,
      radius: t.radii.md,
      height: 44,
    },
    danger: {
      bg: t.colors.danger,
      bgPressed: t.colors.fillPressed,
      text: t.colors.onAccent,
      radius: t.radii.md,
      height: 44,
    },
    hero: {
      // 高对比主操作：明暗模式自动反转（浅色下深底白字、深色下白底黑字）
      bg: t.colors.textPrimary,
      bgPressed: t.colors.textSecondary,
      text: t.colors.bgPage,
      radius: t.radii.full,
      height: 54,
    },
  };
  const p = palette[variant];
  const isHero = variant === 'hero';

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
          minHeight: p.height,
          borderRadius: p.radius,
          backgroundColor: pressed ? p.bgPressed : p.bg,
          borderColor: p.border,
          borderWidth: p.border ? StyleSheet.hairlineWidth : 0,
          opacity: blocked ? 0.5 : 1,
          paddingHorizontal: isHero ? t.spacing.lg : t.spacing.lg,
          paddingVertical: isHero ? 14 : 10,
        },
        style,
      ]}
    >
      {loading ? (
        <ActivityIndicator color={p.text} />
      ) : (
        <>
          <YdText
            style={{
              color: p.text,
              fontWeight: t.typography.weightMedium,
              fontSize: isHero ? t.typography.sizeLg : t.typography.sizeMd,
              flexShrink: 1,
            }}
          >
            {title}
          </YdText>
          {isHero ? (
            <View
              style={{
                width: 34,
                height: 34,
                borderRadius: 17,
                backgroundColor: `${p.text}33`,
                alignItems: 'center',
                justifyContent: 'center',
                marginLeft: t.spacing.sm,
              }}
            >
              <Icon name="arrow-forward" size={18} color={p.text} />
            </View>
          ) : null}
        </>
      )}
    </Pressable>
  );
}

const styles = StyleSheet.create({
  base: { alignItems: 'center', justifyContent: 'center', flexDirection: 'row' },
});
