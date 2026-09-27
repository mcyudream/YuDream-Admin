import React, { createContext, useContext, useMemo, useState } from 'react';
import { useColorScheme } from 'react-native';
import {
  Provider as PaperProvider,
  MD3DarkTheme,
  MD3LightTheme,
  type MD3Theme,
} from 'react-native-paper';
import { darkTheme, lightTheme, mergeTokens, type ThemeTokens } from './tokens';

export type ThemeMode = 'system' | 'light' | 'dark';

interface ThemeContextValue {
  tokens: ThemeTokens;
  mode: ThemeMode;
  /** 远程主题包下发的局部覆盖（schema 未定义键已在 mergeTokens 丢弃） */
  applyRemoteOverride(override: unknown): void;
  setMode(mode: ThemeMode): void;
}

const ThemeContext = createContext<ThemeContextValue | null>(null);

/** T0 token -> react-native-paper MD3 主题映射：第三方组件与自研组件共享同一套视觉源。 */
function toPaperTheme(tokens: ThemeTokens): MD3Theme {
  const base = tokens.scheme === 'dark' ? MD3DarkTheme : MD3LightTheme;
  return {
    ...base,
    colors: {
      ...base.colors,
      primary: tokens.colors.accent,
      onPrimary: tokens.colors.onAccent,
      primaryContainer: tokens.colors.fillHover,
      onPrimaryContainer: tokens.colors.textPrimary,
      secondary: tokens.colors.textSecondary,
      background: tokens.colors.bgPage,
      onBackground: tokens.colors.textPrimary,
      surface: tokens.colors.bgSurface,
      onSurface: tokens.colors.textPrimary,
      surfaceVariant: tokens.colors.fillHover,
      onSurfaceVariant: tokens.colors.textSecondary,
      outline: tokens.colors.borderStrong,
      outlineVariant: tokens.colors.borderSubtle,
      error: tokens.colors.danger,
      backdrop: tokens.colors.scrim,
    },
    roundness: tokens.radii.md,
  };
}

export function ThemeProvider({ children }: { children: React.ReactNode }) {
  const systemScheme = useColorScheme();
  const [mode, setMode] = useState<ThemeMode>('system');
  const [override, setOverride] = useState<unknown>(null);

  const value = useMemo<ThemeContextValue>(() => {
    const effective = mode === 'system' ? (systemScheme === 'dark' ? 'dark' : 'light') : mode;
    const base = effective === 'dark' ? darkTheme : lightTheme;
    return {
      tokens: override ? mergeTokens(base, override) : base,
      mode,
      applyRemoteOverride: setOverride,
      setMode,
    };
  }, [mode, systemScheme, override]);

  const tokens = value.tokens;
  const paperTheme = useMemo(() => toPaperTheme(tokens), [tokens]);

  return (
    <ThemeContext.Provider value={value}>
      <PaperProvider theme={paperTheme}>{children}</PaperProvider>
    </ThemeContext.Provider>
  );
}

export function useTheme(): ThemeTokens {
  const ctx = useContext(ThemeContext);
  if (!ctx) {
    throw new Error('useTheme 必须在 <ThemeProvider> 内使用');
  }
  return ctx.tokens;
}

export function useThemeController(): ThemeContextValue {
  const ctx = useContext(ThemeContext);
  if (!ctx) {
    throw new Error('useThemeController 必须在 <ThemeProvider> 内使用');
  }
  return ctx;
}
