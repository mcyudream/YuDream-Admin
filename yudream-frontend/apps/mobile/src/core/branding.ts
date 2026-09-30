/**
 * 宿主品牌常量与内置资源。应用名 / 图标随品牌统一维护，
 * 版本号与 android/app/build.gradle 的 versionCode/versionName 手工同步。
 */
import { ImageRequireSource } from 'react-native';

export const APP_NAME = 'YDAM';

/** 内置应用 logo（蓝紫浮岛），启动页与关于页共用。 */
export const APP_LOGO: ImageRequireSource = require('@/assets/logo.png');
