# 雷神 Token 助手 · Android

个人辅助工具，非雷神官方 App。支持 Android 8.0 及以上，建议更新系统的 Android System WebView / Chrome。

## 安装包

[下载已签名 APK：v0.1.0](https://github.com/Evilnanan/leigod-auto-pause/raw/refs/heads/feat/android-token-app/android/downloads/leigod-token-helper-v0.1.0.apk)

安装时如系统询问，请允许当前浏览器安装应用。安装包与 [SHA256SUMS.txt](downloads/SHA256SUMS.txt) 一同保存在本分支 `android/downloads/` 目录，发布包关闭调试。源码和包内均不包含用户的账号、密码、token 或私钥。

## 使用

1. 安装 APK，打开 App，在下方雷神官网点击 **我的奖励** 唤起登录窗口。
2. 手动登录并完成验证码。App 使用桌面版官网，并会自动检测登录后的 token。
3. 顶部显示登录验证结果。点击 **复制 Token**。
4. 点击 **打开 GitHub Secret 设置**，在浏览器登录 GitHub，更新 `LEIGOD_TOKENS` 并保存。
5. 可以运行仓库里的 **检查雷神 Token（只读）** 验证更新结果。

默认 GitHub 仓库为 `Evilnanan/leigod-auto-pause`，可从右上角菜单修改。多个账号需要将全部 token 用英文逗号合并后保存，避免覆盖其他账号。

此 App 的登录状态独立于 Edge、Chrome 和雷神 App，首次需要在 App 内登录。登录后官网数据保存在本机 App 的 WebView 中，下次可继续使用；有效期以雷神服务端为准，提取 token 不会自动续期。

App 不会自动修改 GitHub Secret，也不会暂停或恢复时长。清除本机登录记录仅删除 App 内的官网数据，不修改 GitHub Secret。

## 数据与权限

- 唯一权限是网络访问；没有联系人、文件存储、通知或后台服务权限。
- 用户名、密码和验证码在官网页面中输入，App 不读取这些输入。
- 只从 `https://www.leigod.com` 的本地存储读取 `account_token`，没有 JavaScript 到原生对象的接口。
- 原生网络请求仅向 `https://webapi.leigod.com/api/user/info` 查询状态，禁止跟随重定向，不记录 token 或账号响应。
- token 不额外写入原生配置文件；点击复制后进入系统剪贴板。Android 13+ 将该剪贴板内容标为敏感，避免系统预览直接展示。
- 禁止系统备份和截图；发布包不允许 WebView 远程调试。
- 非官网的外部 HTTPS 链接会交给浏览器；GitHub 设置地址不包含 token。

官网及验证码可能要求第三方 Cookie，因此 WebView 允许官网页面中的第三方 Cookie。若验证码不兼容当前系统 WebView，请先更新 WebView 后重试。浏览器书签提取方式仍可使用。

## 构建

工具链：JDK 17+、Android SDK 35、Build Tools 35.0.0、Gradle 8.11.1（Wrapper）、AGP 8.9.3。直接用 Android Studio 打开此目录也可以。

```bash
./gradlew testDebugUnitTest lintRelease assembleDebug
./gradlew connectedDebugAndroidTest  # 已连接模拟器或设备时
```

生成的调试包位于 `app/build/outputs/apk/debug/app-debug.apk`。CI 会测试并生成可安装包，在 Actions 构建结果的 Artifacts 中下载，解压后安装 `.apk`。未配置签名 Secrets 时，CI 提供名称带 `-debug` 的调试包；该包只用于测试，和正式签名版本不能相互覆盖安装。

签名构建需要以下环境变量：

```text
ANDROID_KEYSTORE_PATH=/path/to/token-helper.p12
ANDROID_KEYSTORE_PASSWORD=...
```

密钥别名为 `token-helper`，密钥密码与 keystore 密码相同。签名文件和密码应保留在安全位置，切勿提交到公开仓库。

如需 CI 构建正式签名版本，可配置两个 Actions Secrets：`ANDROID_KEYSTORE_BASE64` 和 `ANDROID_KEYSTORE_PASSWORD`。这些签名信息只用于构建 APK，与 `LEIGOD_TOKENS` 分开。后续版本继续使用相同签名，才能覆盖安装并保留 App 的登录数据。

```bash
./gradlew testDebugUnitTest lintRelease assembleRelease
```

JVM 测试覆盖官网域名边界、仓库地址校验、只读 API 请求、登录失效与网络异常区分，以及拒绝向重定向地址转发 token。模拟器测试使用本地网页和虚拟 token，验证原生页面、WebView 配置及读取脚本；真实账号的登录和验证码仍需在自己的手机上验证。
