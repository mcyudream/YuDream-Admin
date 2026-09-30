import React from 'react';
import { Text, type TextProps, type TextStyle } from 'react-native';
import { useTheme } from '@/core/theme/ThemeProvider';

type Variant = 'display' | 'title' | 'body' | 'secondary' | 'caption' | 'link';

/**
 * 排版基元：字号/字重/颜色只允许来自 token，禁止调用方传裸色值。
 */
export function YdText({
  variant = 'body',
  style,
  children,
  ...rest
}: TextProps & { variant?: Variant }) {
  const t = useTheme();
  const ty = t.typography;
  const styles: Record<Variant, TextStyle> = {
    display: { fontSize: ty.sizeDisplay, fontWeight: ty.weightBold, color: t.colors.textPrimary },
    title: { fontSize: ty.sizeXl, fontWeight: ty.weightBold, color: t.colors.textPrimary },
    body: { fontSize: ty.sizeMd, fontWeight: ty.weightRegular, color: t.colors.textPrimary },
    secondary: { fontSize: ty.sizeSm, fontWeight: ty.weightRegular, color: t.colors.textSecondary },
    caption: { fontSize: ty.sizeXs, fontWeight: ty.weightRegular, color: t.colors.textTertiary },
    link: { fontSize: ty.sizeMd, fontWeight: ty.weightMedium, color: t.colors.textLink },
  };
  const base = styles[variant];
  return (
    <Text
      {...rest}
      style={[{ fontFamily: ty.fontFamilyBase, lineHeight: Math.round((base.fontSize ?? 15) * ty.lineHeight) }, base, style]}
    >
      {children}
    </Text>
  );
}
