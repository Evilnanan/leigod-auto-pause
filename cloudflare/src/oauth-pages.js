// Public Android signing certificate, never a private key or account credential.
const ANDROID_CERT = 'D5:D8:73:52:BD:EF:7A:A1:E4:06:8C:04:52:2A:36:D1:54:53:E1:F7:5E:80:EB:89:59:0B:0B:9B:5E:6F:57:B2';
const CALLBACK_SCRIPT = `(() => {
  const url = new URL(location.href);
  const params = new URLSearchParams();
  let valid = url.search.length <= 8192;
  for (const key of ['code', 'state', 'error']) {
    if (url.searchParams.getAll(key).length > 1) valid = false;
    if (url.searchParams.has(key)) params.set(key, url.searchParams.get(key));
  }
  history.replaceState(null, '', '/oauth/callback');
  const link = document.getElementById('return-app');
  if (!valid || !params.get('state') || (!params.get('code') && !params.get('error'))) {
    document.getElementById('message').textContent = '请从手机 App 重新发起 Cloudflare 登录。';
    link.hidden = true;
    return;
  }
  link.href = 'com.evilnanan.leigodtoken://oauth/callback?' + params.toString();
  link.hidden = false;
})();`;

export async function oauthPage(path) {
  if (path === '/.well-known/assetlinks.json') {
    return new Response(JSON.stringify([{
      relation: ['delegate_permission/common.handle_all_urls'],
      target: { namespace: 'android_app', package_name: 'com.evilnanan.leigodtoken', sha256_cert_fingerprints: [ANDROID_CERT] },
    }]), { headers: { 'Content-Type': 'application/json', 'Cache-Control': 'public, max-age=3600' } });
  }
  if (path !== '/oauth/callback') return null;
  const bytes = new Uint8Array(await crypto.subtle.digest('SHA-256', new TextEncoder().encode(CALLBACK_SCRIPT)));
  const hash = btoa(String.fromCharCode(...bytes));
  return new Response(`<!doctype html><html lang="zh-CN"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1"><title>返回雷神 Cloudflare 助手</title><style>[hidden]{display:none!important}body{font:16px system-ui;margin:48px auto;padding:24px;max-width:480px;color:#192d27;background:#f3f6f4}a{display:block;padding:16px;background:#216b5e;color:white;text-align:center;border-radius:12px;text-decoration:none}small{display:block;margin-top:20px;line-height:1.7;color:#586b63}</style></head><body><h1>返回手机 App</h1><p id="message">点击下方按钮完成 Cloudflare 授权。</p><a id="return-app" hidden>返回雷神 Cloudflare 助手</a><small>请先安装 v0.2.0 或更新版本。此页面仅负责返回 App，不接收或保存 Cloudflare 的访问令牌。</small><script>${CALLBACK_SCRIPT}</script></body></html>`, {
    headers: {
      'Content-Type': 'text/html; charset=utf-8', 'Cache-Control': 'no-store', 'Referrer-Policy': 'no-referrer',
      'Content-Security-Policy': `default-src 'none'; script-src 'sha256-${hash}'; style-src 'unsafe-inline'; base-uri 'none'; frame-ancestors 'none'; form-action 'none'`,
      'X-Content-Type-Options': 'nosniff',
    },
  });
}
