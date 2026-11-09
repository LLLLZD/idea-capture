#!/usr/bin/env node
// token-guard: 主题 token 门禁。扫描指定目录（默认 shared/src）的 .kt，禁止硬编码视觉值。
// 拦截: Color(0x…) / RoundedCornerShape(数字dp…) / fontSize = 数字 / AppSpacing.大驼峰 / AppDecor.大驼峰 直连
// 豁免: 行尾 // token-guard:allow <理由>；胶囊 RoundedCornerShape(50)；theme/ 目录（唯一定义处）
// 用法: node scripts/token-guard.mjs [扫描目录] [--report 报告文件]
// 退出码: 0=全绿  1=有违规（Gradle 的 tokenGuard 任务据此中断构建）
// 注: 违规行采用 Kotlin 编译器诊断格式（e: file:///绝对路径:行:列 消息），
//     以便 Android Studio / IntelliJ 的 Build 窗口识别为可点击跳转的链接。
import fs from 'node:fs';
import path from 'node:path';
import { pathToFileURL } from 'node:url';

const argv = process.argv.slice(2);
let root = 'shared/src';
let reportPath = null;
for (let i = 0; i < argv.length; i++) {
  if (argv[i] === '--report') reportPath = argv[++i];
  else if (!argv[i].startsWith('--')) root = argv[i];
}

const ALLOW_FILES = [/[/\\]theme[/\\]/];
const RULES = [
  [/Color\(0x/, '硬编码颜色', '改用 MaterialTheme.colorScheme.* / AppSemantic.* / AppDecor.current.*'],
  [/RoundedCornerShape\((?!50\)?)\d/, '字面圆角', '改用 MaterialTheme.shapes.extraSmall/small/medium/large/extraLarge（或扩展 mini、listCard）'],
  [/fontSize\s*=\s*\d/, '字面字号', '改用 MaterialTheme.typography 五档（10 / 12 / 14 / 16 / 20）'],
  [/AppSpacing\.[A-Z]/, '旧间距直连', '改用 AppSpacing.current.小驼峰'],
  [/AppDecor\.[A-Z]/, '旧装饰直连', '改用 AppDecor.current.小驼峰'],
];

function walk(d, out) {
  for (const e of fs.readdirSync(d, { withFileTypes: true })) {
    const p = path.join(d, e.name);
    if (e.isDirectory()) walk(p, out);
    else if (e.name.endsWith('.kt')) out.push(p);
  }
  return out;
}

const viol = [];
for (const f of walk(root, [])) {
  if (ALLOW_FILES.some((r) => r.test(f))) continue;
  const url = pathToFileURL(path.resolve(f)).href;   // file:///D:/.../ConfirmDialog.kt
  const lines = fs.readFileSync(f, 'utf8').split(/\r?\n/);
  lines.forEach((l, i) => {
    if (l.includes('token-guard:allow')) return;
    for (const [re, kind, fix] of RULES) {
      const m = re.exec(l);
      if (m) {
        viol.push({ url, line: i + 1, col: m.index + 1, kind, fix, code: l.trim().slice(0, 72) });
        break;
      }
    }
  });
}

const MAX = 20;
let out;
if (viol.length === 0) {
  out = 'PASS: token-guard 全绿';
} else {
  const head = [
    '✗ token-guard 未通过：' + viol.length + ' 处硬编码视觉值，构建已中断',
    '',
    '为什么拦：硬编码的色值 / 圆角 / 字号会绕过主题体系，换肤（亮暗切换、换品牌色）时失效；',
    '          视觉值必须取自主题槽位，唯一定义处是 presentation/theme/ 目录。',
    '',
    '要改的地方（点击下方链接直接跳到该行）：',
  ].join('\n');
  const items = viol
    .slice(0, MAX)
    .map((v) => 'e: ' + v.url + ':' + v.line + ':' + v.col + ' ' + v.kind + ' → ' + v.fix + '（当前：' + v.code + '）')
    .join('\n');
  const tail = [
    '',
    viol.length > MAX ? '… 其余 ' + (viol.length - MAX) + ' 处见报告文件' : '',
    '行内豁免（仅图形 / 品牌资产）：该行行尾加  // token-guard:allow <理由>',
    '紧急放行（不推荐）：./gradlew <任务名> -PskipTokenGuard',
  ].filter(Boolean).join('\n');
  out = [head, items, tail].join('\n');
}
console.log(out);
if (reportPath) {
  fs.mkdirSync(path.dirname(reportPath), { recursive: true });
  fs.writeFileSync(reportPath, out + '\n');
}
process.exit(viol.length ? 1 : 0);
