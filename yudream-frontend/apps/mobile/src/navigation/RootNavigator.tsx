import React from 'react';
import { NavigationContainer, DefaultTheme, DarkTheme } from '@react-navigation/native';
import { createNativeStackNavigator } from '@react-navigation/native-stack';
import { View } from 'react-native';
import { useTheme } from '@/core/theme/ThemeProvider';
import type { RootStackParamList } from './types';
import { SplashScreen } from '@/screens/SplashScreen';
import { WelcomeScreen } from '@/screens/WelcomeScreen';
import { DomainAddScreen } from '@/screens/DomainAddScreen';
import { ScanScreen } from '@/screens/ScanScreen';
import { LoginScreen } from '@/screens/LoginScreen';
import { MainTabs } from '@/screens/MainTabs';
import { PluginHostScreen } from '@/screens/PluginHostScreen';
import { DomainManageScreen } from '@/screens/DomainManageScreen';
import { AboutScreen } from '@/screens/AboutScreen';

const Stack = createNativeStackNavigator<RootStackParamList>();

export function RootNavigator() {
  const t = useTheme();

  const navTheme = {
    ...(t.scheme === 'dark' ? DarkTheme : DefaultTheme),
    colors: {
      ...(t.scheme === 'dark' ? DarkTheme : DefaultTheme).colors,
      background: t.colors.bgPage,
      card: t.colors.bgSurface,
      text: t.colors.textPrimary,
      border: t.colors.borderSubtle,
      primary: t.colors.accent,
    },
  };

  return (
    <NavigationContainer theme={navTheme}>
      <Stack.Navigator
        initialRouteName="Splash"
        screenOptions={{
          // edge-to-edge：状态栏常透明，视觉由页面自身底色 + statusBarStyle 决定；
          // 禁止再设 statusBarBackgroundColor——RNS 会在每次屏幕挂载后回落覆盖透明色。
          // 标题条与页面同底色（bgPage）+ 去阴影；原生 header 自动按状态栏高度下沉。
          headerShadowVisible: false,
          headerTintColor: t.colors.textPrimary,
          headerStyle: { backgroundColor: t.colors.bgPage },
          statusBarStyle: t.scheme === 'dark' ? 'light' : 'dark',
        }}
      >
        <Stack.Screen name="Splash" component={SplashScreen} options={{ headerShown: false }} />
        <Stack.Screen
          name="Welcome"
          component={WelcomeScreen}
          options={{ headerShown: false }}
        />
        <Stack.Screen
          name="DomainAdd"
          component={DomainAddScreen}
          options={{ headerShown: false, presentation: 'modal' }}
        />
        <Stack.Screen
          name="Scan"
          component={ScanScreen}
          options={{ headerShown: false, presentation: 'fullScreenModal' }}
        />
        <Stack.Screen name="Login" component={LoginScreen} options={{ headerShown: false }} />
        <Stack.Screen name="Main" component={MainTabs} options={{ headerShown: false }} />
        <Stack.Screen
          name="PluginHost"
          component={PluginHostScreen}
          options={({ route }) => ({ title: route.params.title })}
        />
        <Stack.Screen
          name="DomainManage"
          component={DomainManageScreen}
          options={{ title: '域管理' }}
        />
        <Stack.Screen
          name="About"
          component={AboutScreen}
          options={{ title: '关于软件' }}
        />
      </Stack.Navigator>
    </NavigationContainer>
  );
}
