import React from 'react';
import { Image, View } from 'react-native';
import { useTheme } from '@/core/theme/ThemeProvider';
import { YdText } from './YdText';

/**
 * 域标识：优先站点 logo 图（复用 web 站点设置），无 logo 回退首字母色块。
 */
export function YdDomainAvatar({
  name,
  logoUrl,
  size = 46,
}: {
  name: string;
  logoUrl?: string;
  size?: number;
}) {
  const t = useTheme();
  const letter = (name.trim()[0] ?? 'Y').toUpperCase();
  return (
    <View
      style={{
        width: size,
        height: size,
        borderRadius: size * 0.28,
        overflow: 'hidden',
        backgroundColor: logoUrl ? t.colors.bgSurface : t.colors.accent,
        alignItems: 'center',
        justifyContent: 'center',
      }}
    >
      {logoUrl ? (
        <Image source={{ uri: logoUrl }} style={{ width: size, height: size }} resizeMode="cover" />
      ) : (
        <YdText style={{ color: t.colors.onAccent, fontSize: size * 0.42, fontWeight: t.typography.weightBold }}>
          {letter}
        </YdText>
      )}
    </View>
  );
}
