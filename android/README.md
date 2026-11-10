# 雷神 Cloudflare 助手 · Android

个人辅助工具，非雷神或 Cloudflare 官方 App。支持 Android 8.0 及以上。

## 安装包

[下载已签名 APK：v0.2.0](https://github.com/Evilnanan/leigod-auto-pause/raw/refs/heads/feat/android-token-app/android/downloads/leigod-token-helper-v0.2.0.apk)

使用与 v0.1.0 / v0.1.1 相同的应用 ID 和正式签名，可以直接覆盖安装并保留官网登录数据。安装包与 [SHA256SUMS.txt](downloads/SHA256SUMS.txt) 同目录保存。源码和安装包不含账号密码、访问令牌、刷新令牌或私钥。Client ID、Account ID 和应用签名证书指纹是公开配置。

## 日常使用

1. 在 App 下方雷神官网主页登录，手动完成验证码，等待登录检查通过。
2. 首次点击 **连接云端**，在手机的系统浏览器中登录 Cloudflare 并授权。授权完成后通常自动返回 App；若留在回调网页，点击 **返回雷神 Cloudflare 助手**。
3. 点击 **一键更新 Cloudflare**。App 会重新只读验证雷神登录，然后直接向 Cloudflare 官方 API 保存并部署 `LEIGOD_TOKENS`。
4. 后续刷新雷神登录时，通常只需要第 1、3 步。Cloudflare 短期访问令牌过期后会尝试用加密保存的刷新令牌续期；授权被撤销或刷新失效时需要重新连接。

App 不执行暂停或恢复时长。定时检查和暂停由已有 Worker 执行，不由手机后台运行；更新凭据不会改变 Worker 代码、定时设置或其他 Secret。

一键更新适用于单账号，会替换 `LEIGOD_TOKENS` 的全部值。多账号继续使用 **复制 Token → 云端设置**，合并全部 token 后保存，避免覆盖其他账号。

默认目标是自己的 `leigod-auto-pause` Worker；右上角或 **云端设置 → 连接设置** 可修改 Account ID、Worker 名称与公开 OAuth Client ID。复制 Token 和打开 Cloudflare Secret 设置仍可作为手动更新方式。

官网采用桌面版入口，保留 **− / + / 适应 / 全屏** 按钮及可滚动登录窗口，不依赖活动页。官网登录数据保存在本机 WebView，独立于 Edge、Chrome 和雷神 App；提取 token 不会延长雷神服务端有效期。

## 首次配置 OAuth 客户端

Cloudflare [支持原生应用的 Authorization Code + PKCE](https://developers.cloudflare.com/fundamentals/oauth/create-an-oauth-client/)。客户端注册一次即可，以后用户在浏览器登录授权。不能直接把 Wrangler 的 Client ID 当成自己的 App，也不读取 Cloudflare 控制台 Cookie。

在自己的 Cloudflare 账号 **Manage Account → OAuth clients** 创建：

| 配置 | 内容 |
| --- | --- |
| Client name | 雷神 Cloudflare 助手 |
| Response types | `code` |
| Grant types | `authorization_code`、`refresh_token` |
| Token endpoint authentication method | `none`，使用 PKCE S256 |
| Redirect URL | `https://leigod-auto-pause.evilnanan.workers.dev/oauth/callback` |
| Scopes | `Workers Scripts Write`，作为必需权限；刷新令牌对应的 `offline_access` 由支持 refresh_token 的客户端配置提供 |
| Visibility | `private`，只允许客户端所属账号的成员授权 |

Cloudflare 客户端注册页要求 HTTP/HTTPS 回调，不能直接填 App 自定义协议。现有 Worker 提供 HTTPS 回调页与 `/.well-known/assetlinks.json`；正式签名 App 注册了对应 Android App Link，也保留用户手动点击的自定义协议回跳。回调页不交换或保存访问令牌，只把一次性授权码和 state 交回 App。

创建后把 **Client ID** 填入 App 连接设置；这是公开标识。选择 `none` 的原生客户端不需要也不能在 App 中保存 Client Secret。Account ID 和 Worker 名称已预填。

API / OAuth 权限通常覆盖被授权账号下的 Workers，不是只授予某个 Secret 的权限；App 自身把写入目标固定为指定 Worker 的 `LEIGOD_TOKENS`。可在 Cloudflare 的第三方授权管理中撤销。**断开本机连接** 仅删除手机保存的凭据，不撤销服务端授权，也不修改已有 Secret 或停止任务。

当前 API OAuth 连接不支持创建 OAuth 客户端：该管理接口要求 `OAuth Client Write` 的 API Token 或控制台操作，因此首次注册需在 Cloudflare 控制台完成。[官方注册说明](https://developers.cloudflare.com/fundamentals/oauth/create-an-oauth-client/)

## v0.2.0 更新

- GitHub Secret 入口改为 Cloudflare，一键更新并保留手动复制方式。
- 浏览器 OAuth + PKCE S256；state 校验、10 分钟登录有效期、一次性回调和刷新令牌轮换。
- Android Keystore AES-GCM 加密保存授权凭据及待完成登录状态；禁止系统备份和设备迁移提取。
- 仅向官方 Cloudflare API 更新一个命名 Secret，所有凭据请求禁止自动跟随重定向，不输出原始响应或异常。

## v0.1.1 更新

- 默认入口改为官网主页 `https://www.leigod.com/`，不依赖活动页面。
- 修复官网固定 860px 登录窗口被裁切的问题，手机号、验证码和发送按钮适配手机宽度。
- 解除官网的禁止缩放设置；登录窗口可竖向滚动，输入框可随键盘调整位置。
- 新增网页缩放、适应和全屏按钮。使用相同签名，可直接覆盖安装 v0.1.0，无需卸载或清除登录记录。

## 数据与权限

- 唯一权限是网络访问，没有后台服务、联系人或文件存储权限。
- 雷神账号密码和验证码只在官网页面输入，App 不读取这些输入；只从严格限定的官网 HTTPS origin 提取 account_token，没有 JavaScript 原生桥接对象。
- 雷神原生 API 只查询 `/api/user/info`；不会暂停或恢复。
- Cloudflare 密码、验证码和二次验证只在系统浏览器中输入；App 不获取浏览器 Cookie。
- OAuth 授权码通过 PKCE 验证后直接向 `https://dash.cloudflare.com/oauth2/token` 交换和续期。访问和刷新令牌仅以 Android Keystore 密钥加密后的密文存储；不上传到第三方服务器、不写入日志、不进入剪贴板。
- Secret 直接发送至 `https://api.cloudflare.com/client/v4/accounts/.../workers/scripts/.../secrets`。不读取其他 Secret 明文，也不把 Worker 的 ADMIN_KEY 放进 App。
- 雷神 token 仍只保存在官网 WebView 数据与运行内存中；手动点击复制时才进入敏感剪贴板。
- 禁止截图和系统备份；正式包关闭 WebView 远程调试。清除雷神登录与断开 Cloudflare 连接分开处理。

## 构建与验证

JDK 17+、Android SDK 35、Build Tools 35.0.0、Gradle 8.11.1、AGP 8.9.3。Android Studio 可直接打开此目录。

```bash
./gradlew testDebugUnitTest lintRelease assembleDebug
./gradlew connectedDebugAndroidTest
```

正式签名使用原有密钥：

```text
ANDROID_KEYSTORE_PATH=/path/to/token-helper.p12
ANDROID_KEYSTORE_PASSWORD=...
```

别名 `token-helper`，密钥密码与 keystore 密码一致。可用 `./gradlew assembleRelease` 构建；密钥、密码均不得提交仓库。CI 支持 `ANDROID_KEYSTORE_BASE64` 与 `ANDROID_KEYSTORE_PASSWORD` Secrets。调试包和正式包签名不同，不能相互覆盖安装。

JVM 测试覆盖 PKCE 标准向量、state/回调边界、登录过期、授权码交换、刷新轮换、指定 Secret 写入、目标不存在、权限错误、重定向及响应脱敏。设备测试覆盖 Keystore 加密存储、伪造回调不能连接、WebView 和验证码窗口的触摸/缩放。测试均使用虚拟凭据；真实官网验证码、Cloudflare 登录与实际 Secret 更新仍需要在自己的手机上验证。
