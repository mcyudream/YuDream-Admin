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
          // 状态栏/标题条与页面主体同底色（bgPage）+ 去阴影，整页通栏无色差；
          // 与 android 主题初始色 ydam_system_bar（bgPage）一致，冷启动也不会闪色带。
          headerShadowVisible: false,
          headerTintColor: t.colors.textPrimary,
          headerStyle: { backgroundColor: t.colors.bgPage },
          statusBarBackgroundColor: t.colors.bgPage,
          statusBarStyle: t.scheme === 'dark' ? 'light' : 'dark',
          statusBarTranslucent: false,
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
