/**
 * YuDream CMS 工作区运行时库（v1）
 * 静态页一行接入：<script src="/api/platform/ai/cms/workspace/lib.js"></script>
 * 职责：拉取模板上下文 → 渲染 data-yb-for/repeat、data-yb-if、data-yb-limit、
 * data-yb-html/markdown、{{dotted.path}} 变量；暴露 window.YuCMS。
 * 语义与公开 SPA 的 renderDynamicHtml/cms-template-render 保持一致（无 eval）。
 */
(function () {
  'use strict';

  var CONTEXT_URL = '/api/public/cms/template-context';
  var context = null;

  function get(path, root) {
    var parts = String(path).split('.');
    var current = root;
    for (var i = 0; i < parts.length; i++) {
      if (current == null) return undefined;
      current = current[parts[i]];
    }
    return current;
  }

  function escapeHtml(value) {
    return String(value == null ? '' : value)
      .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;').replace(/'/g, '&#39;');
  }

  function renderVars(template, root) {
    return String(template || '').replace(/\{\{\s*([\w.]+)\s*\}\}/g, function (_, path) {
      if (path === 'item' || path.indexOf('item.') === 0) return _; // item 仅在循环内展开
      var value = get(path, root);
      return value === undefined || value === null ? '' : escapeHtml(value);
    });
  }

  // 受限表达式求值：路径真值、字面量、!、比较（== != === !== > >= < <=）与 || && 组合。
  // 与 cms-template-render 语义一致：禁函数调用、赋值与任意 JS。
  function evaluate(expr, root) {
    return orExpr(tokenize(String(expr || '')), root);
  }
  function tokenize(expr) {
    var tokens = String(expr).match(/\s*(===|!==|>=|<=|&&|\|\||>|<|!|[^\s><=!&|]+)/g) || [];
    return tokens.map(function (t) { return t.trim(); }).filter(function (t) { return t.length; });
  }
  function orExpr(tokens, root) {
    var left = andExpr(tokens, root);
    while (tokens[0] === '||') { tokens.shift(); var right = andExpr(tokens, root); left = left || right; }
    return left;
  }
  function andExpr(tokens, root) {
    var left = compareExpr(tokens, root);
    while (tokens[0] === '&&') { tokens.shift(); var right = compareExpr(tokens, root); left = left && right; }
    return left;
  }
  function compareExpr(tokens, root) {
    var left = unaryExpr(tokens, root);
    var op = tokens[0];
    if (op === '==' || op === '!=' || op === '===' || op === '!==' || op === '>' || op === '>=' || op === '<' || op === '<=') {
      tokens.shift();
      var right = unaryExpr(tokens, root);
      switch (op) {
        case '==': return left == right; // eslint-disable-line eqeqeq
        case '!=': return left != right; // eslint-disable-line eqeqeq
        case '===': return left === right;
        case '!==': return left !== right;
        case '>': return Number(left) > Number(right);
        case '>=': return Number(left) >= Number(right);
        case '<': return Number(left) < Number(right);
        default: return Number(left) <= Number(right);
      }
    }
    return left;
  }
  function unaryExpr(tokens, root) {
    if (tokens[0] === '!') { tokens.shift(); return !unaryExpr(tokens, root); }
    var token = tokens.shift();
    if (token === undefined) return false;
    if (token === 'true') return true;
    if (token === 'false') return false;
    if (token === 'null') return null;
    if (/^["'].*["']$/.test(token)) return token.slice(1, -1);
    if (/^\d+(\.\d+)?$/.test(token)) return Number(token);
    var value = get(token, root);
    return value === undefined ? false : value;
  }

  function renderList(el, root) {
    var expr = el.getAttribute('data-yb-for') || el.getAttribute('data-yb-repeat') || '';
    var match = expr.match(/^\s*([\w$]+)\s+in\s+([\w.]+)\s*$/) || expr.match(/^\s*([\w.]+)\s*$/);
    if (!match) return;
    var itemName = match[2] ? match[1] : 'item';
    var path = match[2] || match[1];
    var limitAttr = el.getAttribute('data-yb-limit') || '';
    var limit = limitAttr ? parseInt(renderVars(limitAttr, root), 10) : NaN;
    var rows = get(path, root) || [];
    if (Array.isArray(rows) && !isNaN(limit)) rows = rows.slice(0, limit);
    var template = el.innerHTML;
    var html = '';
    for (var i = 0; i < rows.length; i++) {
      var scope = Object.assign({}, root);
      scope[itemName] = rows[i];
      scope.index = i;
      html += renderVars(template, scope).replace(/\{\{\s*item\./g, '{{' + itemName + '.');
    }
    el.innerHTML = html;
  }

  function render(root) {
    var nodes = Array.prototype.slice.call(document.querySelectorAll('[data-yb-for],[data-yb-repeat]'));
    nodes.forEach(function (el) {
      var ifAttr = el.getAttribute('data-yb-if');
      if (ifAttr && !evaluate(ifAttr, root)) { el.innerHTML = ''; return; }
      renderList(el, root);
    });
    Array.prototype.slice.call(document.querySelectorAll('[data-yb-if]')).forEach(function (el) {
      if (el.querySelector && el.hasAttribute('data-yb-for')) return;
      var visible = evaluate(el.getAttribute('data-yb-if'), root);
      el.style.display = visible ? '' : 'none';
    });
    // 循环外的纯变量文本替换
    var walker = document.createTreeWalker(document.body, NodeFilter.SHOW_TEXT);
    var texts = [];
    while (walker.nextNode()) texts.push(walker.currentNode);
    texts.forEach(function (node) {
      var replaced = renderVars(node.nodeValue, root);
      if (replaced !== node.nodeValue) node.nodeValue = replaced;
    });
  }

  function fetchContext() {
    return fetch(CONTEXT_URL, { credentials: 'include' })
      .then(function (res) { return res.json(); })
      .then(function (data) { return data && data.data ? data.data : data; })
      .catch(function () { return {}; });
  }

  function boot() {
    var ready = window.__YU_CMS_READY_LIST__ || [];
    fetchContext().then(function (remote) {
      context = Object.assign({}, window.__YU_CMS_CONTEXT__ || {}, remote || {});
      render(context);
      ready.forEach(function (cb) { try { cb(context); } catch (e) { console.error(e); } });
    });
  }

  window.YuCMS = {
    ready: function (cb) { boot(); if (cb) cb(context); },
    context: function () { return context; },
    refresh: function () { boot(); },
    escapeHtml: escapeHtml
  };
  window.__YU_CMS_READY__ = window.YuCMS.ready;
  window.__YU_CMS_REGISTER_CLEANUP__ = function () { };

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', boot);
  } else {
    boot();
  }
})();
