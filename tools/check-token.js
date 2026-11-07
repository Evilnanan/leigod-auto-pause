import 'dotenv/config';
import * as core from '@actions/core';
import { LeigodAPI } from '../leigod.js';

const tokens = (process.env.LEIGOD_TOKENS || '').split(',').map(token => token.trim()).filter(Boolean);
for (const token of tokens) core.setSecret(token);

if (!tokens.length) {
  core.setFailed('请先在仓库 Actions Secrets 中设置 LEIGOD_TOKENS。');
}

for (const [index, token] of tokens.entries()) {
  const label = `账号 #${index + 1}`;
  const api = new LeigodAPI(token);
  api.session.defaults.timeout = 15000;
  try {
    const paused = await api.isTimePaused();
    core.info(`${label}：Token 有效，${paused ? '时长已暂停' : '时长正在计费'}（本任务只检查）。`);
  } catch (error) {
    // Avoid printing Axios request objects, which include the account token.
    core.setFailed(`${label}：检查失败：${error.message}。若登录已过期，请重新登录官网并更新 LEIGOD_TOKENS。`);
  }
}
