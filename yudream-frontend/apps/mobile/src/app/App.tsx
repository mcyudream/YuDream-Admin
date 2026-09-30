import React from 'react';
import { View } from 'react-native';
import { SafeAreaProvider } from 'react-native-safe-area-context';
import { ThemeProvider } from '@/core/theme/ThemeProvider';
import { useKeyboardResizePad } from '@/core/keyboardResize';
import { RootNavigator } from '@/navigation/RootNavigator';
import { YdDialogHost } from '@/components/YdDialog';

export function App() {
  // edge-to-edge 后 adjustResize 失效，在根部模拟窗口 resize（键盘不遮挡输入框）
  const keyboardPad = useKeyboardResizePad();
  return (
    <SafeAreaProvider>
      <View style={{ flex: 1, paddingBottom: keyboardPad }}>
        <ThemeProvider>
          <RootNavigator />
          <YdDialogHost />
        </ThemeProvider>
      </View>
    </SafeAreaProvider>
  );
}
