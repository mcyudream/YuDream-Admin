/**
 * 主题 T0：纯聚合值 token。颜色只放中性语义槽位 + 一个品牌强调位，
 * 业务/插件只消费语义名，主题包才可改具体值（与 web 侧规则同构）。
 * 远程主题包下发的 theme.json 即为此结构的深局部覆盖。
 */

export interface ColorTokens {
  /** 页面底 */
  bgPage: string;
  /** 卡片/浮层底 */
  bgSurface: string;
  /** 悬浮更高一层（弹层、卡片压卡片） */
  bgElevated: string;
  textPrimary: string;
  textSecondary: string;
  textTertiary: string;
  /** 强调文本/链接 */
  textLink: string;
  borderSubtle: string;
  borderStrong: string;
  fillHover: string;
  fillPressed: string;
  /** 品牌强调（按钮主色、激活态），由主题配置统一下发 */
  accent: string;
  accentPressed: string;
  onAccent: string;
  success: string;
  warning: string;
  danger: string;
  /** 遮罩 */
  scrim: string;
}

export interface SpacingTokens {
  xs: number;
  sm: number;
  md: number;
  lg: number;
  xl: number;
}

export interface RadiusTokens {
  sm: number;
  md: number;
  lg: number;
  full: number;
}

export interface TypographyTokens {
  fontFamilyBase: string | undefined;
  fontFamilyDisplay: string | undefined;
  sizeXs: number;
  sizeSm: number;
  sizeMd: number;
  sizeLg: number;
  sizeXl: number;
  sizeDisplay: number;
  weightRegular: '400';
  weightMedium: '500';
  weightBold: '700';
  /** 行高系数 */
  lineHeight: number;
}

export interface ThemeTokens {
  scheme: 'light' | 'dark';
  colors: ColorTokens;
  spacing: SpacingTokens;
  radii: RadiusTokens;
  typography: TypographyTokens;
}

const spacing: SpacingTokens = { xs: 4, sm: 8, md: 16, lg: 24, xl: 32 };
const radii: RadiusTokens = { sm: 6, md: 10, lg: 16, full: 9999 };
const typography: TypographyTokens = {
  fontFamilyBase: undefined,
  fontFamilyDisplay: undefined,
  sizeXs: 11,
  sizeSm: 13,
  sizeMd: 15,
  sizeLg: 17,
  sizeXl: 20,
  sizeDisplay: 28,
  weightRegular: '400',
  weightMedium: '500',
  weightBold: '700',
  lineHeight: 1.4,
};

export const lightTheme: ThemeTokens = {
  scheme: 'light',
  colors: {
    bgPage: '#f6f7f9',
    bgSurface: '#ffffff',
    bgElevated: '#ffffff',
    textPrimary: '#1d2129',
    textSecondary: '#4e5969',
    textTertiary: '#86909c',
    textLink: '#165dff',
    borderSubtle: '#e5e6eb',
    borderStrong: '#c9cdd4',
    fillHover: 'rgba(29,33,41,0.06)',
    fillPressed: 'rgba(29,33,41,0.12)',
    accent: '#165dff',
    accentPressed: '#0e42d2',
    onAccent: '#ffffff',
    success: '#00b42a',
    warning: '#ff7d00',
    danger: '#f53f3f',
    scrim: 'rgba(0,0,0,0.45)',
  },
  spacing,
  radii,
  typography,
};

export const darkTheme: ThemeTokens = {
  scheme: 'dark',
  colors: {
    bgPage: '#0f1115',
    bgSurface: '#171a21',
    bgElevated: '#1f232c',
    textPrimary: '#f2f3f5',
    textSecondary: '#c5c9d1',
    textTertiary: '#86909c',
    textLink: '#6a9bff',
    borderSubtle: '#2a2e37',
    borderStrong: '#3d424d',
    fillHover: 'rgba(255,255,255,0.08)',
    fillPressed: 'rgba(255,255,255,0.14)',
    accent: '#4e7cff',
    accentPressed: '#6a9bff',
    onAccent: '#ffffff',
    success: '#3dd598',
    warning: '#ffb54d',
    danger: '#ff6b6b',
    scrim: 'rgba(0,0,0,0.6)',
  },
  spacing,
  radii,
  typography,
};

/** 深合并：远程主题包只允许覆盖叶子值，结构以本文件为准（未知键丢弃）。 */
export function mergeTokens(base: ThemeTokens, override: unknown): ThemeTokens {
  if (typeof override !== 'object' || override === null) {
    return base;
  }
  const merge = <T extends Record<string, unknown>>(b: T, o: Record<string, unknown>): T => {
    const out: Record<string, unknown> = { ...b };
    for (const [k, v] of Object.entries(o)) {
      if (!(k in b)) {
        continue;
      }
      const bv = b[k];
      if (
        typeof bv === 'object' && bv !== null &&
        typeof v === 'object' && v !== null && !Array.isArray(v)
      ) {
        out[k] = merge(bv as Record<string, unknown>, v as Record<string, unknown>);
      } else if (typeof bv === typeof v) {
        out[k] = v;
      }
    }
    return out as T;
  };
  return merge(base as unknown as Record<string, unknown>, override as Record<string, unknown>) as unknown as ThemeTokens;
}
