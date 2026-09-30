declare module 'react-native-vector-icons/Ionicons' {
  import type { ComponentType } from 'react';

  export interface IconProps {
    name: string;
    size?: number;
    color?: string;
    onPress?: () => void;
    hitSlop?: number | { top?: number; bottom?: number; left?: number; right?: number };
    style?: unknown;
    accessibilityLabel?: string;
  }

  const Icon: ComponentType<IconProps>;
  export default Icon;
}
