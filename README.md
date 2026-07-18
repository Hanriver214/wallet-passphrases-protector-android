# 加密币钱包助记词保护器 - Android 离线版

本仓库是 [ahui2016/wallet-passphrases-protector](https://github.com/ahui2016/wallet-passphrases-protector) 的 fork，并在其基础上添加了 Android 打包工程，将其打包为**完全离线**的 Android 应用（APK / AAB）。

> 原项目说明见 [README-original.md](README-original.md)

## 关于离线

本 Android 应用严格遵守原项目的"完全本地运行"理念，并进一步从系统层面保证离线：

- **不声明 `android.permission.INTERNET` 权限** —— 应用在系统层面无法发起任何网络请求
- 所有 HTML / JS / CSS 资源打包在 APK 的 `assets/www/` 目录，通过 `file:///android_asset/www/` 加载
- `network_security_config.xml` 显式禁止明文流量，仅信任系统证书库（防御性措施）
- 不引入任何第三方原生库，仅使用 Android 原生 WebView

## 使用方法

1. 从 [Releases](../../releases) 下载 APK 文件
2. 在 Android 手机上允许"安装未知来源应用"后安装
3. 打开应用即可使用，无需联网

## 功能

与原 Web 项目完全一致：

- 创建密码表（英语单词与汉字一一对应）
- 把英语助记词转换为汉字助记词
- 通过密码表 + 汉字助记词找回英语助记词

## 技术参数

| 项目 | 值 |
|---|---|
| applicationId | `com.walletprotector.android` |
| minSdk | 26 (Android 8.0) |
| targetSdk | 34 (Android 14) |
| 签名 | debug keystore（仅供侧载安装，不可上架 Play Store） |

## 本地构建

```bash
# 需要 JDK 17、Android SDK 34、Gradle 8.x
export ANDROID_HOME=/path/to/android-sdk
./gradlew assembleRelease bundleRelease
# 产物：
#   app/build/outputs/apk/release/app-release.apk
#   app/build/outputs/bundle/release/app-release.aab
```

## 风险提示

本工具免费开源，供大家参考、研究，但原作者与打包者水平有限，本工具可能存在不完善的地方，若因使用本工具而产生任何损失，本人一概不负任何责任。

## License

MIT（继承自原项目）
