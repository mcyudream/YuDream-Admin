import React from 'react';
import { NavigationContainer, DefaultTheme, DarkTheme } from '@react-navigation/native';
import { createNativeStackNavigator } from '@react-navigation/native-stack';
import { useTheme } from '@/core/theme/ThemeProvider';
import type { RootStackParamList } from './types';
import { SplashScreen } from '@/screens/SplashScreen';
import { LoginScreen } from '@/screens/LoginScreen';
import { HomeScreen } from '@/screens/HomeScreen';
import { SettingsScreen } from '@/screens/SettingsScreen';
import { PluginHostScreen } from '@/screens/PluginHostScreen';

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
      <Stack.Navigator initialRouteName="Splash">
        <Stack.Screen name="Splash" component={SplashScreen} options={{ headerShown: false }} />
        <Stack.Screen name="Login" component={LoginScreen} options={{ headerShown: false }} />
        <Stack.Screen name="Home" component={HomeScreen} options={{ headerShown: false, title: '首页' }} />
        <Stack.Screen name="Settings" component={SettingsScreen} options={{ title: '设置' }} />
        <Stack.Screen
          name="PluginHost"
          component={PluginHostScreen}
          options={({ route }) => ({ title: route.params.title })}
        />
      </Stack.Navigator>
    </NavigationContainer>
  );
}
