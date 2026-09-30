/**
 * 主题 FaTheme0：宿主 web 端 shadcn neutral 单色系（packages/themes/index.ts，
 * OKLCH 已换算 sRGB）。纯聚合值 token：颜色只放中性语义槽位 + 一个品牌强调位，
 * 业务/插件只消费语义名，主题包才可改具体值（与 web 侧规则同构）。
 * 远程主题包下发的 theme.json 即为此结构的深局部覆盖（primaryColor → accent）。
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

/** 设计稿（design/tools/lib.mjs FaTheme0）同源：卡片 r14、控件 r10-12、页边距节奏 12/16 */
const spacing: SpacingTokens = { xs: 4, sm: 8, md: 12, lg: 20, xl: 28 };
const radii: RadiusTokens = { sm: 8, md: 12, lg: 14, full: 9999 };
const typography: TypographyTokens = {
  fontFamilyBase: undefined,
  fontFamilyDisplay: undefined,
  sizeXs: 11,
  sizeSm: 13,
  sizeMd: 15,
  sizeLg: 17,
  sizeXl: 20,
  sizeDisplay: 26,
  weightRegular: '400',
  weightMedium: '500',
  weightBold: '700',
  lineHeight: 1.45,
};

export const lightTheme: ThemeTokens = {
  scheme: 'light',
  colors: {
    bgPage: '#f2f2f2',
    bgSurface: '#ffffff',
    bgElevated: '#ffffff',
    textPrimary: '#0a0a0a',
    textSecondary: '#737373',
    textTertiary: '#a3a3a3',
    textLink: '#171717',
    borderSubtle: '#e5e5e5',
    borderStrong: '#d4d4d4',
    fillHover: 'rgba(23,23,23,0.05)',
    fillPressed: 'rgba(23,23,23,0.10)',
    accent: '#171717',
    accentPressed: '#404040',
    onAccent: '#fafafa',
    success: '#16a34a',
    warning: '#d97706',
    danger: '#dc2626',
    scrim: 'rgba(0,0,0,0.45)',
  },
  spacing,
  radii,
  typography,
};

export const darkTheme: ThemeTokens = {
  scheme: 'dark',
  colors: {
    bgPage: '#0a0a0a',
    bgSurface: '#171717',
    bgElevated: '#262626',
    textPrimary: '#fafafa',
    textSecondary: '#a3a3a3',
    textTertiary: '#737373',
    textLink: '#e5e5e5',
    borderSubtle: '#262626',
    borderStrong: '#404040',
    fillHover: 'rgba(250,250,250,0.07)',
    fillPressed: 'rgba(250,250,250,0.14)',
    accent: '#e5e5e5',
    accentPressed: '#fafafa',
    onAccent: '#171717',
    success: '#22c55e',
    warning: '#fbbf24',
    danger: '#ef4444',
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
