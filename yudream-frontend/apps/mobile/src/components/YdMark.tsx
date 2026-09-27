import React from 'react';
import { View } from 'react-native';
import { useTheme } from '@/core/theme/ThemeProvider';
import { YdText } from './YdText';

/**
 * 品牌标：主题 accent 的圆角方块 + 首字母。不用位图，天然跟随主题。
 */
export function YdMark({ name, size = 56 }: { name: string; size?: number }) {
  const t = useTheme();
  const letter = (name.trim()[0] ?? 'Y').toUpperCase();
  return (
    <View
      accessibilityLabel={`站点标识：${name}`}
      style={{
        width: size,
        height: size,
        borderRadius: size * 0.28,
        backgroundColor: t.colors.accent,
        alignItems: 'center',
        justifyContent: 'center',
      }}
    >
      <YdText
        style={{
          color: t.colors.onAccent,
          fontSize: size * 0.44,
          fontWeight: t.typography.weightBold,
        }}
      >
        {letter}
      </YdText>
    </View>
  );
}
