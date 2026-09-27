import React from 'react';
import { createBottomTabNavigator } from '@react-navigation/bottom-tabs';
import Icon from 'react-native-vector-icons/Ionicons';
import { useTheme } from '@/core/theme/ThemeProvider';
import { HomeTabScreen } from '@/screens/HomeTabScreen';
import { PluginsTabScreen } from '@/screens/PluginsTabScreen';
import { ProfileTabScreen } from '@/screens/ProfileTabScreen';
import type { MainTabParamList } from '@/navigation/types';

const Tabs = createBottomTabNavigator<MainTabParamList>();

/** 主界面：底部导航（首页 / 应用 / 我的）。 */
export function MainTabs() {
  const t = useTheme();

  return (
    <Tabs.Navigator
      screenOptions={({ route }) => ({
        headerShown: false,
        tabBarActiveTintColor: t.colors.accent,
        tabBarInactiveTintColor: t.colors.textTertiary,
        tabBarStyle: {
          backgroundColor: t.colors.bgSurface,
          borderTopColor: t.colors.borderSubtle,
        },
        tabBarIcon: ({ color, size }) => {
          const name =
            route.name === '首页'
              ? 'home-outline'
              : route.name === '应用'
                ? 'grid-outline'
                : 'person-outline';
          return <Icon name={name} size={size} color={color} />;
        },
      })}
    >
      <Tabs.Screen name="首页" component={HomeTabScreen} />
      <Tabs.Screen name="应用" component={PluginsTabScreen} />
      <Tabs.Screen name="我的" component={ProfileTabScreen} />
    </Tabs.Navigator>
  );
}
