# 安卓手机更新雷神 Token

每周流程：官网登录 → 运行书签 → 复制 token → 更新 GitHub Secret → 运行只读检查。

此书签提取当前登录凭证，不会延长 token 有效期，也不会自动更新 GitHub。项目原文档估计 token 有效期约 7 天，实际以雷神返回的登录状态为准。无需额外服务器或 GitHub 访问令牌。

## 第一次：安装书签

1. 在安卓 Edge 或 Chrome 中收藏任意网页，编辑该收藏/书签。
2. 将名称改为 **雷神取token**。
3. 将网址完整替换成下面代码块中的一整行，确认包含开头的 `javascript:`，保存。代码较长，可使用 GitHub 代码块右上角的复制按钮。

<!-- bookmarklet:start -->

```text
javascript:(()%20%3D%3E%20%7B%0A%20%20const%20repository%20%3D%20'Evilnanan%2Fleigod-auto-pause'%3B%0A%20%20if%20(location.origin%20!%3D%3D%20'https%3A%2F%2Fwww.leigod.com')%20%7B%0A%20%20%20%20alert('%E8%AF%B7%E5%85%88%E6%89%93%E5%BC%80%20https%3A%2F%2Fwww.leigod.com%2F%20%E5%B9%B6%E7%99%BB%E5%BD%95%EF%BC%8C%E5%86%8D%E8%BF%90%E8%A1%8C%E6%AD%A4%E4%B9%A6%E7%AD%BE%E3%80%82')%3B%0A%20%20%20%20return%3B%0A%20%20%7D%0A%0A%20%20let%20token%3B%0A%20%20try%20%7B%0A%20%20%20%20token%20%3D%20JSON.parse(localStorage.getItem('account_token')%20%7C%7C%20'null')%3F.account_token%3B%0A%20%20%7D%20catch%20%7B%0A%20%20%20%20alert('%E6%97%A0%E6%B3%95%E8%AF%BB%E5%8F%96%E7%99%BB%E5%BD%95%E4%BF%A1%E6%81%AF%EF%BC%8C%E8%AF%B7%E9%87%8D%E6%96%B0%E7%99%BB%E5%BD%95%E9%9B%B7%E7%A5%9E%E5%AE%98%E7%BD%91%E5%90%8E%E5%86%8D%E8%AF%95%E3%80%82')%3B%0A%20%20%20%20return%3B%0A%20%20%7D%0A%20%20if%20(typeof%20token%20!%3D%3D%20'string'%20%7C%7C%20!token.trim()%20%7C%7C%20%2F%5E(undefined%7Cnull)%24%2F.test(token.trim()))%20%7B%0A%20%20%20%20alert('%E6%9C%AA%E6%89%BE%E5%88%B0%20token%EF%BC%8C%E8%AF%B7%E5%85%88%E7%99%BB%E5%BD%95%E9%9B%B7%E7%A5%9E%E5%AE%98%E7%BD%91%EF%BC%8C%E5%86%8D%E8%BF%90%E8%A1%8C%E6%AD%A4%E4%B9%A6%E7%AD%BE%E3%80%82')%3B%0A%20%20%20%20return%3B%0A%20%20%7D%0A%0A%20%20const%20id%20%3D%20'leigod-token-helper'%3B%0A%20%20document.getElementById(id)%3F.remove()%3B%0A%20%20const%20host%20%3D%20document.createElement('div')%3B%0A%20%20host.id%20%3D%20id%3B%0A%20%20const%20root%20%3D%20host.attachShadow(%7B%20mode%3A%20'closed'%20%7D)%3B%0A%20%20const%20style%20%3D%20document.createElement('style')%3B%0A%20%20style.textContent%20%3D%20'dialog%7Bbox-sizing%3Aborder-box%3Bwidth%3Amin(440px%2Ccalc(100vw%20-%2024px))%3Bmax-height%3A85vh%3Boverflow%3Aauto%3Bborder%3A0%3Bborder-radius%3A16px%3Bpadding%3A22px%3Bbackground%3A%23fff%3Bcolor%3A%23222%3Bfont%3A16px%2F1.6%20system-ui%7Ddialog%3A%3Abackdrop%7Bbackground%3A%230009%7Dh2%7Bmargin%3A0%200%2012px%3Bfont-size%3A21px%7Dp%7Bmargin%3A12px%200%7Dtextarea%7Bbox-sizing%3Aborder-box%3Bwidth%3A100%25%3Bheight%3A110px%3Bfont%3A16px%2F1.5%20monospace%3Boverflow-wrap%3Aanywhere%3Bresize%3Anone%7Dbutton%2Ca%7Bbox-sizing%3Aborder-box%3Bdisplay%3Ablock%3Bwidth%3A100%25%3Bpadding%3A12px%3Bmargin-top%3A12px%3Bborder%3A0%3Bborder-radius%3A8px%3Bfont%3Ainherit%3Btext-align%3Acenter%3Bcursor%3Apointer%3Btext-decoration%3Anone%3Bbackground%3A%23175cd3%3Bcolor%3A%23fff%7Dbutton%3Alast-child%7Bbackground%3A%23eee%3Bcolor%3A%23222%7Dsmall%7Bdisplay%3Ablock%3Bcolor%3A%23555%7D'%3B%0A%20%20const%20dialog%20%3D%20document.createElement('dialog')%3B%0A%20%20%2F%2F%20Keep%20controls%20readable%20when%20Android's%20desktop%20mode%20scales%20the%20page%20down.%0A%20%20const%20viewport%20%3D%20window.visualViewport%3B%0A%20%20const%20zoom%20%3D%201%20%2F%20Math.min(1%2C%20viewport%3F.scale%20%7C%7C%201)%3B%0A%20%20dialog.style.zoom%20%3D%20String(zoom)%3B%0A%20%20dialog.style.width%20%3D%20%60%24%7BMath.min(440%2C%20((viewport%3F.width%20%7C%7C%20innerWidth)%20-%2024)%20%2F%20zoom)%7Dpx%60%3B%0A%20%20dialog.style.maxHeight%20%3D%20%60%24%7B(viewport%3F.height%20%7C%7C%20innerHeight)%20*%200.85%20%2F%20zoom%7Dpx%60%3B%0A%20%20const%20title%20%3D%20document.createElement('h2')%3B%0A%20%20title.textContent%20%3D%20'%E6%9B%B4%E6%96%B0%E9%9B%B7%E7%A5%9E%20Token'%3B%0A%20%20const%20note%20%3D%20document.createElement('p')%3B%0A%20%20note.textContent%20%3D%20'%E5%88%B0%E6%9C%9F%E8%AF%B7%E5%85%88%E9%87%8D%E6%96%B0%E7%99%BB%E5%BD%95%E5%AE%98%E7%BD%91%EF%BC%9B%E6%8F%90%E5%8F%96%20token%20%E4%B8%8D%E4%BC%9A%E5%BB%B6%E9%95%BF%E6%9C%89%E6%95%88%E6%9C%9F%E3%80%82'%3B%0A%20%20const%20field%20%3D%20document.createElement('textarea')%3B%0A%20%20field.readOnly%20%3D%20true%3B%0A%20%20field.value%20%3D%20token.trim()%3B%0A%20%20field.setAttribute('aria-label'%2C%20'%E9%9B%B7%E7%A5%9E%20account_token')%3B%0A%20%20const%20copy%20%3D%20document.createElement('button')%3B%0A%20%20copy.textContent%20%3D%20'%E5%A4%8D%E5%88%B6%20token'%3B%0A%20%20const%20status%20%3D%20document.createElement('p')%3B%0A%20%20status.setAttribute('role'%2C%20'status')%3B%0A%20%20status.textContent%20%3D%20'%E5%A4%8D%E5%88%B6%E5%90%8E%E5%9C%A8%20GitHub%20%E6%9B%B4%E6%96%B0%20LEIGOD_TOKENS%E3%80%82'%3B%0A%20%20copy.onclick%20%3D%20async%20()%20%3D%3E%20%7B%0A%20%20%20%20try%20%7B%0A%20%20%20%20%20%20await%20navigator.clipboard.writeText(field.value)%3B%0A%20%20%20%20%20%20status.textContent%20%3D%20'%E5%B7%B2%E5%A4%8D%E5%88%B6%EF%BC%8C%E8%AF%B7%E6%89%93%E5%BC%80%20GitHub%20%E8%AE%BE%E7%BD%AE%E6%9B%B4%E6%96%B0%20LEIGOD_TOKENS%E3%80%82'%3B%0A%20%20%20%20%7D%20catch%20%7B%0A%20%20%20%20%20%20field.focus()%3B%0A%20%20%20%20%20%20field.select()%3B%0A%20%20%20%20%20%20field.setSelectionRange(0%2C%20field.value.length)%3B%0A%20%20%20%20%20%20status.textContent%20%3D%20'%E8%87%AA%E5%8A%A8%E5%A4%8D%E5%88%B6%E4%B8%8D%E5%8F%AF%E7%94%A8%EF%BC%8C%E8%AF%B7%E9%95%BF%E6%8C%89%E4%B8%8A%E6%96%B9%E6%96%87%E6%9C%AC%E5%B9%B6%E5%A4%8D%E5%88%B6%E3%80%82'%3B%0A%20%20%20%20%7D%0A%20%20%7D%3B%0A%20%20const%20settings%20%3D%20document.createElement('a')%3B%0A%20%20settings.href%20%3D%20%60https%3A%2F%2Fgithub.com%2F%24%7Brepository%7D%2Fsettings%2Fsecrets%2Factions%60%3B%0A%20%20settings.target%20%3D%20'_blank'%3B%0A%20%20settings.rel%20%3D%20'noopener%20noreferrer'%3B%0A%20%20settings.textContent%20%3D%20'%E6%89%93%E5%BC%80%20GitHub%20Secret%20%E8%AE%BE%E7%BD%AE'%3B%0A%20%20const%20hint%20%3D%20document.createElement('small')%3B%0A%20%20hint.textContent%20%3D%20'%E5%A4%9A%E4%B8%AA%E8%B4%A6%E5%8F%B7%E8%AF%B7%E5%B0%86%E5%85%A8%E9%83%A8%20token%20%E7%94%A8%E8%8B%B1%E6%96%87%E9%80%97%E5%8F%B7%E5%90%88%E5%B9%B6%E5%90%8E%E4%BF%9D%E5%AD%98%EF%BC%8C%E9%81%BF%E5%85%8D%E8%A6%86%E7%9B%96%E5%85%B6%E4%BB%96%E8%B4%A6%E5%8F%B7%E3%80%82'%3B%0A%20%20const%20close%20%3D%20document.createElement('button')%3B%0A%20%20close.textContent%20%3D%20'%E5%85%B3%E9%97%AD'%3B%0A%20%20close.onclick%20%3D%20()%20%3D%3E%20dialog.close()%3B%0A%20%20dialog.onclose%20%3D%20()%20%3D%3E%20host.remove()%3B%0A%20%20dialog.append(title%2C%20note%2C%20field%2C%20copy%2C%20status%2C%20settings%2C%20hint%2C%20close)%3B%0A%20%20root.append(style%2C%20dialog)%3B%0A%20%20document.body.append(host)%3B%0A%20%20dialog.showModal()%3B%0A%7D)()%3B
```

<!-- bookmarklet:end -->

代码对应 [tools/token-bookmarklet.js](../tools/token-bookmarklet.js)。它只读取雷神官网当前页面的本地 token，复制到剪贴板需手动点击，不发送网络请求。GitHub 按钮只打开设置页，网址不包含 token。

本 fork 的按钮指向 `Evilnanan/leigod-auto-pause`。其他 fork 请在脚本中修改 `repository`，再于项目目录执行 `node tools/build-bookmarklet.js` 更新上面的书签代码。已经安装的书签需重新编辑网址。

## 每周：登录并更新

1. 用同一个浏览器打开 [雷神官网](https://www.leigod.com/)，在浏览器菜单中启用 **桌面版网站** 并刷新。手机默认首页主要是下载引导。
2. 登录官网并完成页面要求的人工验证。若首页不显示登录入口，可尝试 [通行证活动页](https://www.leigod.com/activitys/leigodpass2) → **我的奖励** 唤起登录弹窗；无需购买或领取奖励。
3. 保持在 `https://www.leigod.com/` 域名的已登录网页，在地址栏输入 **雷神取token**，点击匹配的收藏/书签建议。应点击书签建议，而不是提交搜索。从收藏列表进入或直接粘贴 JavaScript 到地址栏，可能无法执行。
4. 在弹窗中点击 **复制 token**。如果自动复制失败，长按文本框内的 token 并复制。
5. 点击 **打开 GitHub Secret 设置**，登录 GitHub，编辑或新建名为 `LEIGOD_TOKENS` 的 repository secret，粘贴后保存。不要保存到 Actions Variables、仓库文件或 Issue 中。
6. 在仓库 Actions 中选择 **检查雷神 Token（只读）** → **Run workflow**。这一步只查询登录和暂停状态，不执行暂停或恢复操作。

如果配置多个账号，`LEIGOD_TOKENS` 必须包含所有账号的 token，用英文逗号连接。更新其中一个账号时保留其他账号的值；GitHub 不会展示已保存的 Secret 供你重新读取。

检查成功会显示“Token 有效”和当前计费状态。检查成功不代表时长已经暂停；如需立刻暂停，可手动运行 **雷神自动暂停**（`auto-pause.yml`）。定时暂停在北京时间每天凌晨 3 点执行。

## 常见问题

- **只做了弹窗测试，还没看到 token**：`javascript:alert(...)` 只能验证浏览器执行方式。请将书签网址换成上面完整的提取代码，再先登录雷神官网。
- **找不到书签建议或没有弹窗**：确认是在普通网页中输入完整书签名称，并选中收藏/书签建议；检查保存的网址仍以 `javascript:` 开头。Edge 的书签执行方式已由本 fork 用户在安卓实测，其他浏览器版本需自行确认。
- **提示未找到 token**：登录必须发生在执行书签的同一浏览器、同一普通/无痕窗口环境中。雷神 App 或其他域名的登录不能直接被该书签读取。
- **提取后仍提示登录过期**：读出旧 token 不等于重新登录。请重新完成官网的登录流程，再提取并替换 Secret。网络或服务端故障也可能导致检查失败，结合日志判断。
- **主流程仍报错**：确认更新的是当前 fork 的 repository secret `LEIGOD_TOKENS`，并在保存之后重新运行任务；原有任务不会在更新 Secret 后自动重跑。

本仓库为公开仓库，请仅提交书签源代码。token 只应保存在 GitHub Actions Secret 中。
