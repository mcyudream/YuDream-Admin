import React from 'react';
import { SafeAreaProvider } from 'react-native-safe-area-context';
import { ThemeProvider } from '@/core/theme/ThemeProvider';
import { RootNavigator } from '@/navigation/RootNavigator';
import { YdDialogHost } from '@/components/YdDialog';

export function App() {
  return (
    <SafeAreaProvider>
      <ThemeProvider>
        <RootNavigator />
        <YdDialogHost />
      </ThemeProvider>
    </SafeAreaProvider>
  );
}
