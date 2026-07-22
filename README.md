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

## 安全特性（v1.2.0+）

| 特性 | 说明 |
|------|------|
| **FLAG_SECURE** | 禁止系统截图和录屏，保护敏感信息 |
| **完整性校验** | 密码表导出时附加 SHA-256 哈希，导入时自动验证 |
| **AES-256 加密** | 密码表支持 AES-256-GCM + PBKDF2 加密，用户可自选 |
| **内存清理** | 应用进入后台时自动清空输入框、输出框和映射表 |
| **Overlay 防护** | WebView 启用 `filterTouchesWhenObscured`，防止点击劫持 |
| **防干扰** | `singleTask` + 自定义 `taskAffinity` + `excludeFromRecents`，防止任务劫持 |
| **代码混淆** | Release 构建启用 R8 混淆与资源压缩，提高反编译门槛 |
| **WebView 安全** | 禁用 `allowUniversalAccessFromFileURLs` 和 `allowFileAccessFromFileURLs` |

## 可验证开源（Reproducible Build）

本应用完全开源，任何人都可以验证发布的 APK 与源码一致：

1. 克隆本仓库到本地
2. 使用相同的构建环境（JDK 17、Android SDK 34、Gradle 8.x）
3. 执行 `./gradlew assembleRelease`
4. 使用 `apktool` 或 `diff` 对比官方 APK 与自行构建的 APK 中的 `classes.dex` 和 `assets/www/` 内容

Release 页面附带的 `.sig` 和 `.crt` 文件提供 Sigstore/Cosign 签名验证，确保 APK 未被篡改。

## 技术参数

| 项目 | 值 |
|---|---|
| applicationId | `com.walletprotector.android` |
| minSdk | 26 (Android 8.0) |
| targetSdk | 34 (Android 14) |
| 加密算法 | AES-256-GCM + PBKDF2（100,000 次迭代） |
| 校验算法 | SHA-256 |

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
