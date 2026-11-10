# 用 Cloudflare Workers 定时检查和暂停雷神时长

[![Deploy to Cloudflare](https://deploy.workers.cloudflare.com/button)](https://deploy.workers.cloudflare.com/?url=https%3A%2F%2Fgithub.com%2FEvilnanan%2Fleigod-auto-pause%2Ftree%2Fcloudflare-workers%2Fcloudflare)

定时任务由 Workers Cron Triggers 直接执行，不需要等待 GitHub Actions runner。原生 `fetch` 实现，运行时无 npm 依赖，无需数据库或 KV。

| 北京时间 | UTC Cron | 操作 |
| --- | --- | --- |
| 每天 22:00 | `0 14 * * *` | 检查 Token 和计费状态，不暂停、不恢复 |
| 每天 01:00 | `0 17 * * *` | 查询状态；如正在计费则暂停，并复查结果 |

Cron Triggers 不是硬实时服务；初次添加或修改触发器可能需要最多 15 分钟传播。日志会记录计划时间、实际开始时间和 `delaySeconds`，方便核对执行延迟。[Cloudflare Cron 文档](https://developers.cloudflare.com/workers/configuration/cron-triggers/)

如需修改时间，请同步修改 `wrangler.jsonc` 的两条 Cron 和 `src/worker.js` 的 `CHECK_CRON` / `PAUSE_CRON`，测试会核对两者是否一致。

## 一键部署

1. 点击上面的 **Deploy to Cloudflare**，登录自己的 Cloudflare 账号并授权读取源代码。该流程会将此目录复制成你账号里的独立代码仓库；可自定义新仓库和 Worker 名称。
2. 在 Secret 设置中填入 **`LEIGOD_TOKENS`**。使用安卓 App / 官网书签重新复制即可，多账号用英文逗号分隔，最多 8 个账号。GitHub 已保存的 Secret 无法读取明文，因此不会自动搬过去。
3. 完成部署后，打开 Worker **Settings → Triggers / Cron Triggers**，确认上表两条 Cron 都存在。代码仓库的 `wrangler.jsonc` 是计划配置的来源。
4. 按下一节配置失败推送。未配置推送时，失败只记录在 Cloudflare Logs，不会自动产生 GitHub Actions 通知。
5. 验证 Worker 能访问雷神 API、Token 有效且提醒能送达，再停用原 GitHub 的 **雷神自动暂停** 和 **检查雷神 Token（只读）**，避免两边重复定时执行。停用不会删除工作流，仍可作为回退方案。

如果一键部署页面没有显示 Secret 输入框，可在 Worker 的 **Settings → Variables and Secrets** 新增 **Secret** 类型的 `LEIGOD_TOKENS`，保存并部署后再验证。

现有 GitHub 定时任务不会因下载这份代码或提交 PR 自动停止；真正切换需要完成上述部署和验证。

## 失败提醒

正常执行不推送。登录失效、网络异常、暂停复查失败或未配置 Token 时，合并为一条失败提醒，区分账号序号和原因。消息不含雷神 token、手机号或原始账号响应。

读取状态时遇到临时网络故障会进行一次短暂重试。任务禁用平台级的再次投递，避免同一次失败反复提醒或在很晚以后重复执行暂停；失败后可通过受保护的接口手动重试。

默认 `NOTIFY_CHANNEL=auto`：配置 PushPlus 时使用 PushPlus，否则配置完整的 Telegram 参数时使用 Telegram；都没有时只记日志。也可将该普通变量显式设置为 `pushplus`、`telegram` 或 `none`。

### PushPlus 微信

在 PushPlus 获取自己的用户 token 或单独创建一个消息 token，按其页面完成微信绑定和实名认证；然后在 Worker 的 **Variables and Secrets** 新增：

| 名称 | 类型 | 内容 |
| --- | --- | --- |
| `PUSHPLUS_TOKEN` | Secret | 自己的 PushPlus token |

两种 token 均可用于发送消息，消息 token 可独立撤销，适合脚本使用。[官方说明](https://www.pushplus.plus/doc/help/token.html)

通知失败时，受保护的 `/notify-test` 只返回固定错误类别及数字状态，不回传服务端原始消息或 token。`provider` / `905` 表示账号未实名认证，`903` 表示 token 无效。[返回码说明](https://www.pushplus.plus/doc/guide/code.html)

### Telegram

在 Worker 新增：

| 名称 | 类型 | 内容 |
| --- | --- | --- |
| `TELEGRAM_BOT_TOKEN` | Secret | Bot token |
| `TELEGRAM_CHAT_ID` | Secret | 接收提醒的 chat ID |

先在 Telegram 中向自己的 Bot 发送 `/start`，再测试通知。

## 部署后手动验证

`GET /health` 仅表示 Worker 在线，不查询账户，不能证明 Token 有效。

如需即时验证，为 Worker 设置一个足够长的随机 **`ADMIN_KEY` Secret**，启用下面的受保护接口。未设置该 Secret 时，这些控制接口均返回 404；设置后还必须使用 Bearer 鉴权，账号操作只允许 POST。

| 接口 | 操作 |
| --- | --- |
| `POST /check` | 立即只读检查，失败时按已配置渠道推送 |
| `POST /pause` | 立即检查并暂停，再复查状态 |
| `POST /notify-test` | 仅测试推送，不访问雷神账户 |

可用 Cloudflare 请求面板，或在终端执行以下示例。将 `WORKER_URL` 和 `ADMIN_KEY` 设置为自己的值：

```bash
curl -X POST "$WORKER_URL/check" -H "Authorization: Bearer $ADMIN_KEY"
curl -X POST "$WORKER_URL/notify-test" -H "Authorization: Bearer $ADMIN_KEY"
# 需要立即暂停时再执行：
curl -X POST "$WORKER_URL/pause" -H "Authorization: Bearer $ADMIN_KEY"
```

`/check` 返回 `ok:true` 即登录有效；`/pause` 返回 `paused` 或 `already_paused` 表示复查已暂停；`/notify-test` 应返回 `notification:sent`。HTTP 503 表示检查或通知失败，查看脱敏原因后处理。

Cloudflare 机器的出口 IP 与 GitHub 不同，部署后应进行上述真实检查，以确认雷神接口没有访问限制。这里的自动化测试均使用虚拟 token，不会替代真实账号验证。

对外请求使用 `redirect: 'manual'`，遇到重定向直接报错，防止凭据转发到其他地址。Workers 不支持 Fetch 标准中的 `redirect: 'error'`；该选项会在发出请求前抛出异常。[Workers Request 文档](https://developers.cloudflare.com/workers/runtime-apis/request/)

## 以后更新 Token

1. 在原安卓 App 或官网书签中提取并复制 token。
2. 打开 **Cloudflare → Workers & Pages → 本 Worker → Settings → Variables and Secrets**。
3. 编辑 `LEIGOD_TOKENS`，保存并部署；多个账号保留全部 token，用英文逗号分隔。
4. 手动运行 `/check`，或查看下一次检查结果。

安卓 App v0.1.1 仍可用于官网登录、验证和复制。它的 GitHub 设置按钮指向旧保存位置；迁移后请直接在 Cloudflare 设置中保存，而不是只修改 GitHub Secret。Token 有效期仍由雷神决定，迁移平台不会自动续期。

## 开发与 CLI 部署

使用 Node.js 22+：

```bash
cd cloudflare
npm ci
npm test
npm run check
cp .dev.vars.example .dev.vars
# 编辑 .dev.vars，填写 LEIGOD_TOKENS 和需要的可选提醒 Secret。
npx wrangler login
npm run deploy -- --secrets-file .dev.vars
```

`.dev.vars` 已加入忽略规则，只在本地保存。`--secrets-file` 会把文件中的值作为 Worker Secrets 上传。[Secrets 文档](https://developers.cloudflare.com/workers/configuration/secrets/)

也可在部署后单独更新某个 Secret：

```bash
npx wrangler secret put LEIGOD_TOKENS
npx wrangler secret put PUSHPLUS_TOKEN
```

本地调试：`npm run dev`。可通过本地 `/check` 验证鉴权与配置，通过 Wrangler 的本地 Cron 调试入口测试 scheduled 事件。生产环境不会暴露开发用的 Cron 调试入口。

测试覆盖两条 Cron 的操作边界、真实的“查询→暂停→复查”流程、已暂停跳过、登录失效、多账号继续处理、有限重试、失败合并通知、鉴权、重定向限制及日志/推送脱敏。
