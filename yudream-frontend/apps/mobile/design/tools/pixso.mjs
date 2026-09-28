/**
 * Pixso MCP 直连辅助脚本（本会话无原生 pixso 工具，走本地 HTTP JSON-RPC）。
 *
 * 用法：
 *   node pixso.mjs list                     # 列出全部工具名
 *   node pixso.mjs call <tool> '<json-args>' # 调用工具（小参数直接给）
 *   node pixso.mjs callfile <tool> <file>   # 调用工具（大参数从 JSON 文件读）
 *   node pixso.mjs raw '<json-rpc-body>'    # 发原始 JSON-RPC
 *
 * 响应里的 text 内容直接打印；image 内容保存到 ./.pixso-out/ 并打印路径。
 * 会话 ID 缓存在 ./.pixso-out/session.txt，失效自动重新 initialize。
 */
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const ROOT = path.dirname(fileURLToPath(import.meta.url));
const OUT = path.join(ROOT, '.pixso-out');
fs.mkdirSync(OUT, { recursive: true });
const SID_FILE = path.join(OUT, 'session.txt');

const ENDPOINT = 'http://127.0.0.1:3667/mcp';
let nextId = Math.floor(Math.random() * 100000) + 10;

function readSid() {
  try { return fs.readFileSync(SID_FILE, 'utf8').trim(); } catch { return ''; }
}
function saveSid(sid) { fs.writeFileSync(SID_FILE, sid, 'utf8'); }

async function post(body, sid, timeoutMs = 120000) {
  const headers = { 'Content-Type': 'application/json', Accept: 'application/json, text/event-stream' };
  if (sid) headers['Mcp-Session-Id'] = sid;
  const ctrl = new AbortController();
  const timer = setTimeout(() => ctrl.abort(), timeoutMs);
  try {
    const res = await fetch(ENDPOINT, { method: 'POST', headers, body: JSON.stringify(body), signal: ctrl.signal });
    const text = await res.text();
    return { status: res.status, headers: res.headers, text };
  } finally { clearTimeout(timer); }
}

function parseSse(text) {
  // 兼容 SSE（data: 行）与纯 JSON 两种返回
  if (text.startsWith('event:') || text.includes('\ndata:') || text.startsWith('data:')) {
    const lines = text.split('\n').filter((l) => l.startsWith('data:'));
    const last = lines[lines.length - 1];
    return JSON.parse(last.slice(5).trim());
  }
  return JSON.parse(text);
}

async function initSession() {
  const { headers, text } = await post({
    jsonrpc: '2.0', id: ++nextId, method: 'initialize',
    params: { protocolVersion: '2024-11-05', capabilities: {}, clientInfo: { name: 'zcode-cli', version: '1.0' } },
  }, '');
  const sid = headers.get('mcp-session-id') || '';
  if (!sid) throw new Error('initialize 未下发 session id: ' + text.slice(0, 200));
  saveSid(sid);
  await post({ jsonrpc: '2.0', method: 'notifications/initialized' }, sid);
  return sid;
}

async function rpc(method, params) {
  let sid = readSid();
  for (let attempt = 0; attempt < 2; attempt++) {
    const { status, text } = await post({ jsonrpc: '2.0', id: ++nextId, method, params }, sid);
    if (status === 404 || status === 400 || /session/i.test(text.slice(0, 300))) {
      sid = await initSession();
      continue;
    }
    let msg;
    try { msg = parseSse(text); } catch (e) {
      throw new Error(`响应解析失败(${status}): ${text.slice(0, 300)}`);
    }
    if (msg.error) throw new Error('MCP error: ' + JSON.stringify(msg.error).slice(0, 800));
    return msg.result;
  }
  throw new Error('MCP 会话重建后仍失败');
}

function emitContent(result) {
  const items = (result && result.content) || [];
  const saved = [];
  for (const item of items) {
    if (item.type === 'text') {
      const txt = item.text;
      // take_screenshot 返回 JSON 文本（含 base64/dataURL），自动落盘
      if (txt.startsWith('{') && txt.includes('"base64"')) {
        try {
          const j = JSON.parse(txt);
          const b64 = j.base64 || (j.imageUrl || '').split(',').pop();
          if (b64 && b64.length > 100) {
            const file = path.join(OUT, `shot-${j.nodeId ? String(j.nodeId).replace(/:/g, '-') : Date.now()}.png`);
            fs.writeFileSync(file, Buffer.from(b64, 'base64'));
            saved.push(file);
            console.log(`[image saved] ${file}`);
            continue;
          }
        } catch { /* 按 text 处理 */ }
      }
      console.log(txt);
    } else if (item.type === 'image') {
      const n = saved.length;
      const file = path.join(OUT, `img-${Date.now()}-${n}.png`);
      fs.writeFileSync(file, Buffer.from(item.data, 'base64'));
      saved.push(file);
      console.log(`[image saved] ${file}`);
    } else if (item.type === 'resource') {
      console.log('[resource]', JSON.stringify(item.resource).slice(0, 500));
    } else console.log('[?]', JSON.stringify(item).slice(0, 300));
  }
  if (result && result.isError) { console.error('(tool reported isError)'); process.exitCode = 2; }
  return saved;
}

const [, , cmd, tool, arg] = process.argv;
if (cmd === 'dump') {
  const r = await rpc('tools/list', {});
  fs.writeFileSync(path.join(OUT, 'tools.json'), JSON.stringify(r, null, 1), 'utf8');
  console.log('written:', path.join(OUT, 'tools.json'));
} else if (cmd === 'list') {
  const r = await rpc('tools/list', {});
  for (const t of r.tools) console.log(t.name);
} else if (cmd === 'call') {
  const args = arg ? JSON.parse(arg) : {};
  emitContent(await rpc('tools/call', { name: tool, arguments: args }));
} else if (cmd === 'callfile') {
  const args = JSON.parse(fs.readFileSync(arg, 'utf8'));
  const r = await rpc('tools/call', { name: tool, arguments: args });
  emitContent(r);
} else if (cmd === 'raw') {
  const body = JSON.parse(tool);
  const r = await rpc(body.method, body.params);
  console.log(JSON.stringify(r, null, 1).slice(0, 4000));
} else {
  console.log('用法: node pixso.mjs list | call <tool> <json> | callfile <tool> <file> | raw <json>');
}
