(() => {
  const repository = 'Evilnanan/leigod-auto-pause';
  if (location.origin !== 'https://www.leigod.com') {
    alert('请先打开 https://www.leigod.com/ 并登录，再运行此书签。');
    return;
  }

  let token;
  try {
    token = JSON.parse(localStorage.getItem('account_token') || 'null')?.account_token;
  } catch {
    alert('无法读取登录信息，请重新登录雷神官网后再试。');
    return;
  }
  if (typeof token !== 'string' || !token.trim() || /^(undefined|null)$/.test(token.trim())) {
    alert('未找到 token，请先登录雷神官网，再运行此书签。');
    return;
  }

  const id = 'leigod-token-helper';
  document.getElementById(id)?.remove();
  const host = document.createElement('div');
  host.id = id;
  const root = host.attachShadow({ mode: 'closed' });
  const style = document.createElement('style');
  style.textContent = 'dialog{box-sizing:border-box;width:min(440px,calc(100vw - 24px));max-height:85vh;overflow:auto;border:0;border-radius:16px;padding:22px;background:#fff;color:#222;font:16px/1.6 system-ui}dialog::backdrop{background:#0009}h2{margin:0 0 12px;font-size:21px}p{margin:12px 0}textarea{box-sizing:border-box;width:100%;height:110px;font:16px/1.5 monospace;overflow-wrap:anywhere;resize:none}button,a{box-sizing:border-box;display:block;width:100%;padding:12px;margin-top:12px;border:0;border-radius:8px;font:inherit;text-align:center;cursor:pointer;text-decoration:none;background:#175cd3;color:#fff}button:last-child{background:#eee;color:#222}small{display:block;color:#555}';
  const dialog = document.createElement('dialog');
  // Keep controls readable when Android's desktop mode scales the page down.
  const viewport = window.visualViewport;
  const zoom = 1 / Math.min(1, viewport?.scale || 1);
  dialog.style.zoom = String(zoom);
  dialog.style.width = `${Math.min(440, ((viewport?.width || innerWidth) - 24) / zoom)}px`;
  dialog.style.maxHeight = `${(viewport?.height || innerHeight) * 0.85 / zoom}px`;
  const title = document.createElement('h2');
  title.textContent = '更新雷神 Token';
  const note = document.createElement('p');
  note.textContent = '到期请先重新登录官网；提取 token 不会延长有效期。';
  const field = document.createElement('textarea');
  field.readOnly = true;
  field.value = token.trim();
  field.setAttribute('aria-label', '雷神 account_token');
  const copy = document.createElement('button');
  copy.textContent = '复制 token';
  const status = document.createElement('p');
  status.setAttribute('role', 'status');
  status.textContent = '复制后在 GitHub 更新 LEIGOD_TOKENS。';
  copy.onclick = async () => {
    try {
      await navigator.clipboard.writeText(field.value);
      status.textContent = '已复制，请打开 GitHub 设置更新 LEIGOD_TOKENS。';
    } catch {
      field.focus();
      field.select();
      field.setSelectionRange(0, field.value.length);
      status.textContent = '自动复制不可用，请长按上方文本并复制。';
    }
  };
  const settings = document.createElement('a');
  settings.href = `https://github.com/${repository}/settings/secrets/actions`;
  settings.target = '_blank';
  settings.rel = 'noopener noreferrer';
  settings.textContent = '打开 GitHub Secret 设置';
  const hint = document.createElement('small');
  hint.textContent = '多个账号请将全部 token 用英文逗号合并后保存，避免覆盖其他账号。';
  const close = document.createElement('button');
  close.textContent = '关闭';
  close.onclick = () => dialog.close();
  dialog.onclose = () => host.remove();
  dialog.append(title, note, field, copy, status, settings, hint, close);
  root.append(style, dialog);
  document.body.append(host);
  dialog.showModal();
})();
