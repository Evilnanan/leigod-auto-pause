const API = 'https://webapi.leigod.com';
export const CHECK_CRON = '0 14 * * *'; // 北京时间 22:00
export const PAUSE_CRON = '0 17 * * *'; // 北京时间次日 01:00
const AUTH_CODES = new Set([400006, 400008, 400816, 400007, 400334, 400027]);
const MESSAGES = {
  auth: '登录已失效，请重新登录并更新 Worker 的 LEIGOD_TOKENS Secret。',
  network: '网络请求失败或超时，请检查服务连接；不一定是 Token 过期。',
  api: '服务端响应异常，请查看雷神服务状态后重试。',
  not_paused: '已尝试暂停，但复查仍在计费，请手动检查。',
  configuration: '请配置 LEIGOD_TOKENS（最多 8 个账号，用英文逗号分隔）。',
};

class TaskError extends Error {
  constructor(kind, retryable = false, httpStatus) {
    super(MESSAGES[kind]);
    this.kind = kind;
    this.retryable = retryable;
    this.httpStatus = httpStatus;
  }
}

class NotificationError extends Error {
  constructor(kind, code) {
    super('推送发送失败，请检查通知 Secret 和服务状态。');
    this.details = { kind, ...(Number.isInteger(code) ? { code } : {}) };
  }
}

function tokensFrom(env) {
  const tokens = [...new Set(String(env.LEIGOD_TOKENS || '').split(',').map(v => v.trim()).filter(Boolean))];
  if (tokens.length === 0 || tokens.length > 8) throw new TaskError('configuration');
  return tokens;
}

function safeFailure(error) {
  const kind = error instanceof TaskError ? error.kind : 'api';
  return {
    ok: false, kind, message: MESSAGES[kind],
    ...(error instanceof TaskError && error.httpStatus ? { httpStatus: error.httpStatus } : {}),
    ...(error instanceof TaskError && error.networkCause ? { networkCause: error.networkCause } : {}),
  };
}

function networkFailure(error) {
  const message = String(error?.message || '').toLowerCase();
  const failure = new TaskError('network', true);
  // Fixed categories only; never return or log the original exception.
  failure.networkCause = /illegal invocation|incorrect this/.test(message) ? 'runtime_receiver'
    : /timeout|timed out|aborted/.test(message) ? 'timeout'
    : /dns|resolve|lookup/.test(message) ? 'dns'
    : /tls|ssl|certificate/.test(message) ? 'tls'
    : /connection/.test(message) ? 'connection' : 'unknown';
  return failure;
}

function beijingTime(ms) {
  return new Date(ms + 8 * 3600 * 1000).toISOString().slice(0, 19).replace('T', ' ') + ' 北京时间';
}

export function createWorker({ fetchImpl = (...args) => globalThis.fetch(...args), now = Date.now, sleep = ms => new Promise(r => setTimeout(r, ms)), log = value => console.log(value) } = {}) {
  async function requestApi(path, token) {
    let response;
    try {
      response = await fetchImpl(API + path, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', Accept: 'application/json', Referer: 'https://www.leigod.com/' },
        body: JSON.stringify({ account_token: token, lang: 'zh_CN' }),
        // Workers supports only follow/manual. Never forward credentials on redirects.
        redirect: 'manual',
        signal: AbortSignal.timeout(10000),
      });
    } catch (error) {
      throw networkFailure(error);
    }
    if (response.status === 401) throw new TaskError('auth', false, response.status);
    if (!response.ok) throw new TaskError('network', response.status === 429 || response.status >= 500, response.status);
    let result;
    try {
      const text = await response.text();
      if (text.length > 128000) throw new Error('oversized');
      result = JSON.parse(text);
    } catch {
      throw new TaskError('api');
    }
    if (AUTH_CODES.has(Number(result?.code))) throw new TaskError('auth');
    if (result?.code !== 0 && result?.code !== '0') throw new TaskError('api');
    return result.data;
  }

  async function readPaused(token) {
    for (let attempt = 0; attempt < 2; attempt++) {
      try {
        const info = await requestApi('/api/user/info', token);
        const status = info?.pause_status_id;
        if (![0, 1, '0', '1'].includes(status)) throw new TaskError('api');
        return Number(status) === 1;
      } catch (error) {
        if (attempt === 0 && error instanceof TaskError && error.retryable) { await sleep(400); continue; }
        throw error;
      }
    }
  }

  async function run(mode, env, scheduledTime = now()) {
    const started = now();
    const report = {
      mode,
      scheduledAt: new Date(scheduledTime).toISOString(),
      startedAt: new Date(started).toISOString(),
      delaySeconds: Math.max(0, Math.round((started - scheduledTime) / 1000)),
      ok: false,
      accounts: [],
    };
    let tokens;
    try { tokens = tokensFrom(env); }
    catch (error) { report.configuration = safeFailure(error); return report; }
    for (const [index, token] of tokens.entries()) {
      try {
        const paused = await readPaused(token);
        if (mode === 'check') {
          report.accounts.push({ account: index + 1, ok: true, state: paused ? 'valid_paused' : 'valid_running' });
        } else if (paused) {
          report.accounts.push({ account: index + 1, ok: true, state: 'already_paused' });
        } else {
          // One pause request; always recheck, even if its response was lost.
          // At most 5 API calls per account, within the free tier's subrequest budget.
          let pauseError;
          try { await requestApi('/api/user/pause', token); } catch (error) { pauseError = error; }
          if (!await readPaused(token)) throw pauseError || new TaskError('not_paused');
          report.accounts.push({ account: index + 1, ok: true, state: 'paused' });
        }
      } catch (error) {
        report.accounts.push({ account: index + 1, ...safeFailure(error) });
      }
    }
    report.ok = report.accounts.every(account => account.ok);
    return report;
  }

  function notificationChannel(env) {
    const channel = env.NOTIFY_CHANNEL || 'auto';
    if (channel !== 'auto') return channel;
    if (env.PUSHPLUS_TOKEN) return 'pushplus';
    if (env.TELEGRAM_BOT_TOKEN && env.TELEGRAM_CHAT_ID) return 'telegram';
    return 'none';
  }

  async function notify(env, title, content) {
    const channel = notificationChannel(env);
    if (channel === 'none') return 'not_configured';
    let url, body;
    if (channel === 'pushplus' && env.PUSHPLUS_TOKEN) {
      url = 'https://www.pushplus.plus/send';
      body = { token: env.PUSHPLUS_TOKEN, title, content, template: 'txt' };
    } else if (channel === 'telegram' && /^\d+:[A-Za-z0-9_-]+$/.test(env.TELEGRAM_BOT_TOKEN || '') && env.TELEGRAM_CHAT_ID) {
      url = `https://api.telegram.org/bot${env.TELEGRAM_BOT_TOKEN}/sendMessage`;
      body = { chat_id: env.TELEGRAM_CHAT_ID, text: `${title}\n\n${content}` };
    } else {
      throw new Error('推送渠道未正确配置。');
    }
    try {
      const response = await fetchImpl(url, {
        method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body),
        redirect: 'manual', signal: AbortSignal.timeout(7000),
      });
      if (!response.ok) throw new NotificationError('http', response.status);
      const data = await response.json();
      if (channel === 'pushplus' ? Number(data.code) !== 200 : data.ok !== true) {
        throw new NotificationError('provider', channel === 'pushplus' ? Number(data.code) : undefined);
      }
      return 'sent';
    } catch (error) {
      // Never propagate provider response text or an exception containing bot-token URLs.
      throw error instanceof NotificationError ? error : new NotificationError('connection');
    }
  }

  async function finish(report, env) {
    report.notification = 'not_needed';
    if (!report.ok) {
      const failures = report.accounts.filter(account => !account.ok).map(account => `账号 #${account.account}：${account.message}`);
      if (report.configuration) failures.push(report.configuration.message);
      const text = [
        `计划：${beijingTime(Date.parse(report.scheduledAt))}`,
        `开始：${beijingTime(Date.parse(report.startedAt))}，延迟 ${report.delaySeconds} 秒`,
        ...failures,
        'Token 请保存到 Cloudflare Worker 的 LEIGOD_TOKENS Secret。',
      ].join('\n');
      try { report.notification = await notify(env, `雷神${report.mode === 'check' ? '登录检查' : '自动暂停'}失败`, text); }
      catch (error) {
        report.notification = 'failed';
        if (error instanceof NotificationError) report.notificationDetails = error.details;
      }
    }
    // Only fixed messages, account indexes and timestamps; no tokens, API bodies or raw exceptions.
    log(JSON.stringify({ event: 'leigod_task', ...report }));
    return report;
  }

  async function authorized(request, env) {
    if (!env.ADMIN_KEY) return false;
    const presented = request.headers.get('Authorization')?.match(/^Bearer (.+)$/i)?.[1] || '';
    if (!presented || presented.length > 1024) return false;
    const encode = new TextEncoder();
    const hashes = await Promise.all([presented, env.ADMIN_KEY].map(value => crypto.subtle.digest('SHA-256', encode.encode(value))));
    const a = new Uint8Array(hashes[0]), b = new Uint8Array(hashes[1]);
    let different = 0;
    for (let i = 0; i < a.length; i++) different |= a[i] ^ b[i];
    return different === 0;
  }

  const json = (value, status = 200) => new Response(JSON.stringify(value), {
    status, headers: { 'Content-Type': 'application/json; charset=utf-8', 'Cache-Control': 'no-store' },
  });

  return {
    async scheduled(controller, env) {
      // Reads have their own bounded retry. Avoid repeated alerts or much later mutations.
      controller.noRetry?.();
      const mode = controller.cron === CHECK_CRON ? 'check' : controller.cron === PAUSE_CRON ? 'pause' : null;
      if (!mode) throw new Error('未知 Cron 配置，未执行任何账号操作。');
      const report = await finish(await run(mode, env, controller.scheduledTime), env);
      if (!report.ok) throw new Error('雷神定时任务失败，请查看已脱敏的执行日志。');
    },
    async fetch(request, env) {
      const path = new URL(request.url).pathname;
      if ((path === '/' || path === '/health') && request.method === 'GET') {
        return json({ service: 'leigod-auto-pause', status: 'online', schedules: { check: '22:00 Asia/Shanghai', pause: '01:00 Asia/Shanghai' } });
      }
      if (!['/check', '/pause', '/notify-test'].includes(path) || !env.ADMIN_KEY) return json({ error: 'Not found' }, 404);
      if (!await authorized(request, env)) return json({ error: 'Unauthorized' }, 401);
      if (request.method !== 'POST') return json({ error: 'Use POST' }, 405);
      if (path === '/notify-test') {
        try {
          const result = await notify(env, '雷神 Worker 通知测试', '提醒渠道已连通。此测试不执行暂停或恢复操作。');
          return json({ notification: result }, result === 'sent' ? 200 : 503);
        } catch (error) {
          return json({ notification: 'failed', ...(error instanceof NotificationError ? { details: error.details } : {}) }, 503);
        }
      }
      const report = await finish(await run(path === '/check' ? 'check' : 'pause', env), env);
      return json(report, report.ok ? 200 : 503);
    },
  };
}

