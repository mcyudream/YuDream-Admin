import React from 'react';
import { Pressable, StyleSheet, View, type ViewStyle } from 'react-native';
import { useTheme } from '@/core/theme/ThemeProvider';

/**
 * 卡片容器：圆角/底色/间距全部来自 token；可点卡片带 pressed 反馈。
 */
export function YdCard({
  children,
  onPress,
  style,
}: {
  children: React.ReactNode;
  onPress?: () => void;
  style?: ViewStyle;
}) {
  const t = useTheme();
  const base: ViewStyle = {
    backgroundColor: t.colors.bgSurface,
    borderRadius: t.radii.lg,
    padding: t.spacing.md,
    borderWidth: StyleSheet.hairlineWidth,
    borderColor: t.colors.borderSubtle,
  };
  if (!onPress) {
    return <View style={[base, style]}>{children}</View>;
  }
  return (
    <Pressable
      accessibilityRole="button"
      onPress={onPress}
      style={({ pressed }) => [
        base,
        pressed && { backgroundColor: t.colors.fillHover },
        style,
      ]}
    >
      {children}
    </Pressable>
  );
}
