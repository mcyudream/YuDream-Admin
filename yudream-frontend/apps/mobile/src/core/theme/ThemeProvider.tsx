import React, { createContext, useContext, useMemo, useState } from 'react';
import { useColorScheme } from 'react-native';
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

  return <ThemeContext.Provider value={value}>{children}</ThemeContext.Provider>;
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
