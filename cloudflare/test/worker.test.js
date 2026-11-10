import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { runInNewContext } from 'node:vm';
import { createWorker, CHECK_CRON, PAUSE_CRON } from '../src/worker.js';

const when = Date.UTC(2026, 10, 10, 14, 0, 0);
const secret = 'dummy-account-token';
const env = { LEIGOD_TOKENS: secret };
const json = (data, status = 200) => new Response(JSON.stringify(data), { status });
const info = paused => json({ code: 0, data: { pause_status_id: paused ? 1 : 0 } });
const success = () => json({ code: 0 });
function fixture(responses) {
  const requests = [], logs = [], sleeps = [];
  const worker = createWorker({
    now: () => when + 5000,
    log: value => logs.push(JSON.parse(value)),
    sleep: async ms => { sleeps.push(ms); },
    fetchImpl: async (url, options) => {
      requests.push({ url, ...options, payload: JSON.parse(options.body) });
      assert.equal(options.redirect, 'manual');
      assert.ok(options.signal instanceof AbortSignal);
      assert.ok(responses.length, `Unexpected request: ${url}`);
      const response = responses.shift();
      if (response instanceof Error) throw response;
      return response;
    },
  });
  return { worker, requests, logs, sleeps };
}
const trigger = (worker, cron, bindings = env) => worker.scheduled({ cron, scheduledTime: when }, bindings);

test('deployment crons match the handler and the requested Beijing times', () => {
  const config = JSON.parse(readFileSync(new URL('../wrangler.jsonc', import.meta.url), 'utf8'));
  assert.deepEqual(config.triggers.crons, [CHECK_CRON, PAUSE_CRON]);
  assert.equal(CHECK_CRON, '0 14 * * *');
  assert.equal(PAUSE_CRON, '0 17 * * *');
});

test('22:00 checks a running account without pausing, records delay, and redacts tokens', async () => {
  const f = fixture([info(false)]);
  await trigger(f.worker, CHECK_CRON);
  assert.deepEqual(f.requests.map(r => new URL(r.url).pathname), ['/api/user/info']);
  assert.equal(f.requests[0].payload.account_token, secret);
  assert.equal(f.logs[0].accounts[0].state, 'valid_running');
  assert.equal(f.logs[0].delaySeconds, 5);
  assert.equal(f.logs[0].notification, 'not_needed');
  assert.ok(!JSON.stringify(f.logs).includes(secret));
});

test('01:00 verifies running -> pause -> paused using the same account token', async () => {
  const f = fixture([info(false), success(), info(true)]);
  await trigger(f.worker, PAUSE_CRON);
  assert.deepEqual(f.requests.map(r => new URL(r.url).pathname), ['/api/user/info', '/api/user/pause', '/api/user/info']);
  assert.ok(f.requests.every(r => r.payload.account_token === secret));
  assert.equal(f.logs[0].accounts[0].state, 'paused');
});

test('already paused is a successful no-op', async () => {
  const f = fixture([info(true)]);
  await trigger(f.worker, PAUSE_CRON);
  assert.equal(f.requests.length, 1);
  assert.equal(f.logs[0].accounts[0].state, 'already_paused');
});

test('pause returning success cannot hide an account that is still running', async () => {
  const f = fixture([info(false), success(), info(false)]);
  await assert.rejects(trigger(f.worker, PAUSE_CRON), /任务失败/);
  assert.equal(f.logs[0].accounts[0].kind, 'not_paused');
});

test('lost pause response is resolved by a successful state verification, without repeating the mutation', async () => {
  const f = fixture([info(false), new Error('connection reset'), info(true)]);
  await trigger(f.worker, PAUSE_CRON);
  assert.equal(f.requests.filter(r => r.url.endsWith('/pause')).length, 1);
  assert.equal(f.logs[0].accounts[0].state, 'paused');
});

test('expired credentials fail without retry or mutation; PushPlus alerts contain no account token or raw server message', async () => {
  const f = fixture([json({ code: 400006, msg: `secret ${secret}` }), json({ code: 200 })]);
  await assert.rejects(trigger(f.worker, CHECK_CRON, { ...env, PUSHPLUS_TOKEN: 'dummy-push-key' }), /任务失败/);
  assert.equal(f.requests.length, 2);
  assert.equal(f.sleeps.length, 0);
  assert.equal(f.logs[0].accounts[0].kind, 'auth');
  assert.equal(f.logs[0].notification, 'sent');
  assert.equal(f.requests[1].url, 'https://www.pushplus.plus/send');
  assert.equal(f.requests[1].payload.token, 'dummy-push-key');
  assert.ok(!JSON.stringify(f.requests[1].payload).includes(secret));
  assert.ok(!JSON.stringify(f.logs).includes('dummy-push-key'));
  assert.ok(!JSON.stringify(f.logs).includes(secret));
});

test('transient read errors get one bounded retry', async () => {
  const f = fixture([json({}, 503), info(true)]);
  await trigger(f.worker, CHECK_CRON);
  assert.deepEqual(f.sleeps, [400]);
  assert.equal(f.requests.length, 2);
});

test('HTTP 403 is not mislabeled as expired login and is not repeatedly retried', async () => {
  const f = fixture([json({}, 403)]);
  await assert.rejects(trigger(f.worker, CHECK_CRON), /任务失败/);
  assert.equal(f.logs[0].accounts[0].kind, 'network');
  assert.equal(f.logs[0].accounts[0].httpStatus, 403);
  assert.equal(f.requests.length, 1);
});

test('Leigod redirects fail without retry or forwarding account credentials', async () => {
  const f = fixture([new Response(null, { status: 302, headers: { Location: 'https://other.example/' } })]);
  await assert.rejects(trigger(f.worker, CHECK_CRON), /任务失败/);
  assert.equal(f.requests.length, 1);
  assert.equal(f.logs[0].accounts[0].httpStatus, 302);
});

test('notification redirects fail without forwarding the provider token', async () => {
  const f = fixture([new Response(null, { status: 302, headers: { Location: 'https://other.example/' } })]);
  const response = await f.worker.fetch(new Request('https://worker.test/notify-test', {
    method: 'POST', headers: { Authorization: 'Bearer admin' },
  }), { ADMIN_KEY: 'admin', PUSHPLUS_TOKEN: 'private-push-token' });
  assert.equal(response.status, 503);
  assert.equal(f.requests.length, 1);
  assert.deepEqual(await response.json(), { notification: 'failed', details: { kind: 'http', code: 302 } });
});

test('provider errors expose only a numeric status and fixed category', async () => {
  const f = fixture([json({ code: 600, msg: 'sensitive-provider-token' })]);
  const response = await f.worker.fetch(new Request('https://worker.test/notify-test', {
    method: 'POST', headers: { Authorization: 'Bearer admin' },
  }), { ADMIN_KEY: 'admin', PUSHPLUS_TOKEN: 'private-push-token' });
  assert.deepEqual(await response.json(), { notification: 'failed', details: { kind: 'provider', code: 600 } });
});

test('multiple accounts continue after failure and generate one combined alert', async () => {
  const f = fixture([json({ code: 400008 }), info(false), success(), info(true), json({ code: 200 })]);
  await assert.rejects(trigger(f.worker, PAUSE_CRON, { LEIGOD_TOKENS: 'first, second', PUSHPLUS_TOKEN: 'push' }), /任务失败/);
  assert.equal(f.logs[0].accounts.length, 2);
  assert.equal(f.logs[0].accounts[0].ok, false);
  assert.equal(f.logs[0].accounts[1].state, 'paused');
  assert.equal(f.requests.filter(r => r.url === 'https://www.pushplus.plus/send').length, 1);
});

test('unknown cron fails closed without any outbound calls', async () => {
  const f = fixture([]);
  await assert.rejects(trigger(f.worker, '* * * * *'), /未知 Cron/);
  assert.equal(f.requests.length, 0);
});

test('scheduler retries are disabled so failures do not repeatedly alert or pause later', async () => {
  const f = fixture([json({ code: 400006 })]);
  let disabled = false;
  await assert.rejects(f.worker.scheduled({ cron: CHECK_CRON, scheduledTime: when, noRetry() { disabled = true; } }, env), /任务失败/);
  assert.equal(disabled, true);
});

test('missing or oversized token sets never silently succeed', async () => {
  for (const LEIGOD_TOKENS of ['', ' , ', Array.from({ length: 9 }, (_, i) => `token${i}`).join(',')]) {
    const f = fixture([]);
    await assert.rejects(trigger(f.worker, CHECK_CRON, { LEIGOD_TOKENS }), /任务失败/);
    assert.equal(f.logs[0].configuration.kind, 'configuration');
    assert.equal(f.requests.length, 0);
  }
});

test('trimmed duplicate tokens are queried only once', async () => {
  const f = fixture([info(true)]);
  await trigger(f.worker, CHECK_CRON, { LEIGOD_TOKENS: ' one , one, ' });
  assert.equal(f.requests.length, 1);
});

test('malformed success payloads fail safely', async () => {
  for (const payload of [null, {}, { code: 0 }, { code: 0, data: { pause_status_id: false } }, { code: 0, data: { pause_status_id: 9 } }]) {
    const f = fixture([json(payload)]);
    await assert.rejects(trigger(f.worker, CHECK_CRON), /任务失败/);
    assert.equal(f.logs[0].accounts[0].kind, 'api');
  }
});

test('HTTP controls are disabled without ADMIN_KEY and require bearer auth plus POST', async () => {
  const f = fixture([]);
  assert.equal((await f.worker.fetch(new Request('https://worker.test/pause', { method: 'POST' }), env)).status, 404);
  const bindings = { ...env, ADMIN_KEY: 'dummy-admin-key' };
  assert.equal((await f.worker.fetch(new Request('https://worker.test/pause', { method: 'POST' }), bindings)).status, 401);
  assert.equal((await f.worker.fetch(new Request('https://worker.test/pause', { method: 'POST', headers: { Authorization: 'Bearer wrong' } }), bindings)).status, 401);
  assert.equal((await f.worker.fetch(new Request('https://worker.test/pause', { headers: { Authorization: 'Bearer dummy-admin-key' } }), bindings)).status, 405);
  assert.equal(f.requests.length, 0);
});

test('authorized manual check is read-only and response contains no token', async () => {
  const f = fixture([info(false)]);
  const response = await f.worker.fetch(new Request('https://worker.test/check', {
    method: 'POST', headers: { Authorization: 'Bearer dummy-admin-key' },
  }), { ...env, ADMIN_KEY: 'dummy-admin-key' });
  assert.equal(response.status, 200);
  assert.equal(response.headers.get('Cache-Control'), 'no-store');
  const text = await response.text();
  assert.ok(!text.includes(secret));
  assert.equal(JSON.parse(text).accounts[0].state, 'valid_running');
  assert.equal(f.requests.length, 1);
});

test('public health has no account queries or account data', async () => {
  const f = fixture([]);
  const response = await f.worker.fetch(new Request('https://worker.test/health'), env);
  assert.equal(response.status, 200);
  assert.equal((await response.json()).status, 'online');
  assert.equal(f.requests.length, 0);
});

test('Android association publishes only the release certificate and never queries accounts', async () => {
  const f = fixture([]);
  const response = await f.worker.fetch(new Request('https://worker.test/.well-known/assetlinks.json'), env);
  assert.equal(response.status, 200);
  const [association] = await response.json();
  assert.equal(association.target.package_name, 'com.evilnanan.leigodtoken');
  assert.match(association.target.sha256_cert_fingerprints[0], /^([A-F0-9]{2}:){31}[A-F0-9]{2}$/);
  assert.equal(f.requests.length, 0);
});

test('OAuth return page keeps callback values out of HTML and restricts inline scripts by hash', async () => {
  const f = fixture([]);
  const response = await f.worker.fetch(new Request('https://worker.test/oauth/callback?code=private-code&state=private-state'), env);
  assert.equal(response.status, 200);
  assert.equal(response.headers.get('Cache-Control'), 'no-store');
  assert.equal(response.headers.get('Referrer-Policy'), 'no-referrer');
  const html = await response.text();
  assert.ok(!html.includes('private-code'));
  assert.ok(!html.includes('private-state'));
  const script = html.match(/<script>([\s\S]*?)<\/script>/)[1];
  const hash = Buffer.from(await crypto.subtle.digest('SHA-256', new TextEncoder().encode(script))).toString('base64');
  assert.ok(response.headers.get('Content-Security-Policy').includes(`'sha256-${hash}'`));
  assert.equal(f.requests.length, 0);
});

test('OAuth browser fallback forwards only fixed callback fields and clears the browser URL', async () => {
  const f = fixture([]);
  const response = await f.worker.fetch(new Request('https://worker.test/oauth/callback'), env);
  const script = (await response.text()).match(/<script>([\s\S]*?)<\/script>/)[1];
  for (const [query, allowed] of [
    ['?code=a%26b&state=valid&redirect_uri=https://evil.example', true],
    ['?code=a&state=one&state=two', false], ['?code=a', false],
  ]) {
    const link = { hidden: true }, message = {}, cleared = [];
    runInNewContext(script, {
      URL, URLSearchParams, location: { href: `https://worker.test/oauth/callback${query}` },
      history: { replaceState: (...args) => cleared.push(args[2]) },
      document: { getElementById: id => id === 'return-app' ? link : message },
    });
    assert.deepEqual(cleared, ['/oauth/callback']);
    assert.equal(link.hidden, !allowed);
    if (allowed) {
      const target = new URL(link.href);
      assert.equal(target.protocol, 'com.evilnanan.leigodtoken:');
      assert.equal(target.hostname, 'oauth');
      assert.equal(target.searchParams.get('code'), 'a&b');
      assert.equal(target.searchParams.has('redirect_uri'), false);
    } else assert.equal(link.href, undefined);
  }
});

test('Telegram notification failures cannot leak bot keys through exception text', async () => {
  const bot = '123456:dummy_bot_key';
  const f = fixture([json({ code: 400006 }), new Error(`request https://api.telegram.org/bot${bot}/sendMessage failed`)]);
  await assert.rejects(trigger(f.worker, CHECK_CRON, { ...env, TELEGRAM_BOT_TOKEN: bot, TELEGRAM_CHAT_ID: '123' }), /任务失败/);
  assert.equal(f.logs[0].notification, 'failed');
  assert.ok(!JSON.stringify(f.logs).includes(bot));
});

test('notification self-test does not query or mutate Leigod', async () => {
  const f = fixture([json({ ok: true })]);
  const response = await f.worker.fetch(new Request('https://worker.test/notify-test', {
    method: 'POST', headers: { Authorization: 'Bearer admin' },
  }), { ADMIN_KEY: 'admin', TELEGRAM_BOT_TOKEN: '123456:bot_key', TELEGRAM_CHAT_ID: '123' });
  assert.equal(response.status, 200);
  assert.equal(f.requests.length, 1);
  assert.ok(f.requests[0].url.startsWith('https://api.telegram.org/'));
});
