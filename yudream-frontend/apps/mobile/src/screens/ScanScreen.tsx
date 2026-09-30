import React, { useCallback, useRef, useState } from 'react';
import { Pressable, View } from 'react-native';
import { StatusBar } from 'react-native';
import { useCodeScanner, useCameraDevice, useCameraPermission, Camera } from 'react-native-vision-camera';
import type { NativeStackScreenProps } from '@react-navigation/native-stack';
import Icon from 'react-native-vector-icons/Ionicons';
import { YdButton, YdText } from '@/components';
import { useTheme } from '@/core/theme/ThemeProvider';
import type { RootStackParamList } from '@/navigation/types';

type Props = NativeStackScreenProps<RootStackParamList, 'Scan'>;

/**
 * 相机扫一扫：识别站点二维码后把地址 merge 回传给 DomainAdd。
 * 扫到一条即锁定，避免连续命中重复回传。
 */
export function ScanScreen({ navigation }: Props) {
  const t = useTheme();
  const { hasPermission, requestPermission } = useCameraPermission();
  const device = useCameraDevice('back');
  const lockedRef = useRef(false);
  const [scannedPreview, setScannedPreview] = useState<string | null>(null);

  const codeScanner = useCodeScanner({
    codeTypes: ['qr'],
    onCodeScanned: (codes) => {
      if (lockedRef.current) {
        return;
      }
      const value = codes.map((code) => code.value).find((text): text is string => Boolean(text));
      if (!value) {
        return;
      }
      lockedRef.current = true;
      setScannedPreview(value);
      navigation.navigate({ name: 'DomainAdd', params: { scanned: value }, merge: true });
      navigation.goBack();
    },
  });

  const grant = useCallback(() => {
    void requestPermission();
  }, [requestPermission]);

  const isActive = hasPermission && device != null;

  return (
    <View style={{ flex: 1, backgroundColor: '#000' }}>
      <StatusBar barStyle="light-content" />
      {isActive ? (
        <Camera
          style={{ flex: 1 }}
          device={device}
          isActive
          codeScanner={codeScanner}
        />
      ) : (
        <View style={{ flex: 1, alignItems: 'center', justifyContent: 'center', gap: 16, padding: 24 }}>
          <YdText style={{ color: '#fff', fontSize: 16, textAlign: 'center' }}>
            {hasPermission
              ? '未找到可用相机设备'
              : '扫码需要相机权限，用于识别站点二维码'}
          </YdText>
          {!hasPermission ? (
            <YdButton title="授权相机" onPress={grant} style={{ minWidth: 160 }} />
          ) : null}
        </View>
      )}

      {/* 顶栏：返回 + 标题（叠加在相机/背景之上） */}
      <View
        pointerEvents="box-none"
        style={{
          position: 'absolute',
          top: 0,
          left: 0,
          right: 0,
          flexDirection: 'row',
          alignItems: 'center',
          gap: 8,
          paddingHorizontal: 12,
          paddingTop: 8,
        }}
      >
        <Pressable
          accessibilityRole="button"
          onPress={() => navigation.goBack()}
          hitSlop={10}
          style={{
            width: 40,
            height: 40,
            alignItems: 'center',
            justifyContent: 'center',
            borderRadius: 20,
            backgroundColor: 'rgba(0,0,0,0.35)',
          }}
        >
          <Icon name="chevron-back" size={24} color="#fff" />
        </Pressable>
        <View
          style={{
            paddingHorizontal: 12,
            paddingVertical: 6,
            borderRadius: 16,
            backgroundColor: 'rgba(0,0,0,0.35)',
          }}
        >
          <YdText style={{ color: '#fff' }}>扫描站点二维码</YdText>
        </View>
      </View>

      {/* 取景框提示 */}
      {isActive && !scannedPreview ? (
        <View pointerEvents="none" style={{ position: 'absolute', left: 0, right: 0, bottom: 96, alignItems: 'center' }}>
          <View
            style={{
              width: 232,
              height: 232,
              borderRadius: 20,
              borderWidth: 2,
              borderColor: 'rgba(255,255,255,0.85)',
              backgroundColor: 'transparent',
            }}
          />
          <YdText style={{ color: 'rgba(255,255,255,0.9)', marginTop: 14, fontSize: 13 }}>
            对准站点页面的二维码即可自动填入
          </YdText>
        </View>
      ) : null}
    </View>
  );
}
