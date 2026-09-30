import React from 'react';
import { Card as PaperCard } from 'react-native-paper';
import { useTheme } from '@/core/theme/ThemeProvider';

/**
 * 卡片容器（react-native-paper Card 封装）：surface 底 + 主题圆角 + 细描边。
 */
export function YdCard({
  children,
  onPress,
  style,
}: {
  children: React.ReactNode;
  onPress?: () => void;
  style?: object;
}) {
  const t = useTheme();
  return (
    <PaperCard
      mode="contained"
      onPress={onPress}
      style={[
        {
          backgroundColor: t.colors.bgSurface,
          borderRadius: t.radii.lg,
          borderWidth: 1,
          borderColor: t.colors.borderSubtle,
        },
        style,
      ]}
      contentStyle={{ padding: t.spacing.md }}
    >
      {children}
    </PaperCard>
  );
}
