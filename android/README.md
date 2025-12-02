# EasyRTC 音视频通话 Android 应用

本项目是一个基于 **EasyRTC** 库开发的 Android 应用，支持实时音视频通话功能。  
直接体验安卓效果：http://app.tsingsee.com/easyrtc

## 功能特性

- 使用 [EasyRTC](https://github.com/EasyGBS/EasyRTCDevice.git) 实现实时通信
- 支持一对一音视频通话
- 兼容 Android 平台（最低支持 API 级别请参考 `build.gradle`）

## 依赖库

- EasyRTC SDK
- WebRTC（由 EasyRTC 内部集成）
- AndroidX

## 快速开始

1. 克隆本仓库：
   ```bash
   git clone https://github.com/EasyGBS/EasyRTCDevice.git
   ```

2. 在 Android Studio 中打开项目。

3. 确保已配置好服务器地址（在 `EasyRTCSdk.kt` 中设置）。

4. 连接设备或启动模拟器，运行应用。

## 注意事项

- 请确保设备具有摄像头和麦克风权限。
- 需要部署并运行 EasyGBS 信令服务器以实现完整通话功能。
- 建议在真机上测试音视频效果。

## 许可证

本项目基于 MIT 许可证开源。详情请参阅 [LICENSE](LICENSE.txt) 文件。