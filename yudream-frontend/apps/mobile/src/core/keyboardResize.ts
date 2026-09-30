import { useEffect, useState } from 'react';
import { Dimensions, Keyboard, type KeyboardEvent } from 'react-native';

/**
 * edge-to-edge 下（Android API 30+）windowSoftInputMode="adjustResize" 失效，
 * 窗口高度不再随键盘收缩。此 hook 返回需补在根视图底部的键盘高度，
 * 在宿主根部模拟旧的 resize 行为（登录表单、插件输入框零改动适配）。
 * 窗口仍会随键盘收缩的系统（API < 30 的旧行为）返回 0，避免双重补偿。
 */
export function useKeyboardResizePad(): number {
  const [pad, setPad] = useState(0);
  useEffect(() => {
    const onShow = (e: KeyboardEvent) => {
      const kb = e.endCoordinates.height;
      const win = Dimensions.get('window').height;
      const screen = Dimensions.get('screen').height;
      // 窗口高度未被键盘压缩 → adjustResize 未生效（edge-to-edge），需自行补偿
      setPad(win > screen - kb ? kb : 0);
    };
    const onHide = () => setPad(0);
    const show = Keyboard.addListener('keyboardDidShow', onShow);
    const hide = Keyboard.addListener('keyboardDidHide', onHide);
    return () => {
      show.remove();
      hide.remove();
    };
  }, []);
  return pad;
}
