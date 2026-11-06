// Interactive screenshot capture for the campus guide web app.
// Usage: node capture_shots.js <baseUrl> <outDir>
const { spawn } = require("node:child_process");
const fs = require("node:fs");
const os = require("node:os");
const path = require("node:path");

const BASE = process.argv[2] || "http://127.0.0.1:5173";
const OUT = process.argv[3] || ".";
const CHROME_CANDIDATES = [
  process.env.CHROME_PATH,
  "C:/Program Files/Google/Chrome/Application/chrome.exe",
  "C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe",
].filter(Boolean);
const CHROME = CHROME_CANDIDATES.find(p => fs.existsSync(p));
if (!CHROME) { console.error("Chrome/Edge not found"); process.exit(1); }
fs.mkdirSync(OUT, { recursive: true });

const port = 9412;
const profile = path.join(os.tmpdir(), "campus-capture-" + Date.now());
const chrome = spawn(CHROME, [
  "--headless=new", "--remote-debugging-port=" + port,
  "--user-data-dir=" + profile,
  "--disable-gpu-sandbox", "--no-first-run", "--no-default-browser-check",
  "--use-gl=angle", "--use-angle=swiftshader", "--enable-unsafe-swiftshader",
  "--window-size=1600,1000", "--force-device-scale-factor=1",
  "--hide-scrollbars", "about:blank",
], { stdio: "ignore" });

const sleep = ms => new Promise(r => setTimeout(r, ms));
const getJson = (u, opts) => fetch(u, opts).then(r => r.json());

const SETVAL = `function setVal(sel, v){const el=document.querySelector(sel);if(!el)return 'missing '+sel;const proto=el.tagName==='TEXTAREA'?HTMLTextAreaElement.prototype:HTMLInputElement.prototype;const set=Object.getOwnPropertyDescriptor(proto,'value').set;set.call(el,v);el.dispatchEvent(new Event('input',{bubbles:true}));return 'ok';}`;

const STEPS = [
  { name: "shot-public-overview", url: BASE + "/", wait: 9000 },
  { name: "shot-point-focus", url: BASE + "/", wait: 9000, acts: [
    { js: "document.querySelectorAll('.location-rail .rail-body ol button')[2].click(); 'ok'" },
    { wait: 3500 },
  ]},
  { name: "shot-chat-route", url: BASE + "/", wait: 9000, acts: [
    { js: "document.querySelector('.avatar-companion button').click(); 'ok'" },
    { wait: 1200 },
    { js: "const b=[...document.querySelectorAll('.chat-console button')].find(x=>x.textContent.includes('图书馆')); if(b){b.click();'ok'}else{'no quick question'}" },
    { wait: 6000 },
  ]},
  { name: "shot-tour", url: BASE + "/", wait: 9000, acts: [
    { js: "const b=document.querySelectorAll('.tour-rail .tour-list button')[1]; if(b){b.click();'ok'}else{'no tour'}" },
    { wait: 4000 },
  ]},
  { name: "shot-admin", url: BASE + "/admin/login", wait: 3000, acts: [
    { js: SETVAL + "setVal('input[type=text], input:not([type=password])', 'admin')" },
    { js: SETVAL + "setVal('input[type=password]', 'Demo@123')" },
    { js: "const b=document.querySelector('button[type=submit], .login-card button, main button'); if(b){b.click();'ok'}else{'no submit'}" },
    { wait: 4000 },
  ]},
  { name: "shot-viewer", url: BASE + "/campus-viewer.html", wait: 9000 },
];

async function attach(wsUrl) {
  const ws = new WebSocket(wsUrl);
  await new Promise(r => ws.addEventListener("open", r));
  let id = 0; const pending = new Map();
  ws.addEventListener("message", ev => {
    const m = JSON.parse(ev.data);
    if (m.id && pending.has(m.id)) { pending.get(m.id).resolve(m.result); pending.delete(m.id); }
  });
  const send = (method, params = {}) => new Promise(res => { const mid = ++id; pending.set(mid, { resolve: res }); ws.send(JSON.stringify({ id: mid, method, params })); });
  return { ws, send };
}

(async () => {
  let ver = null;
  for (let i = 0; i < 60; i++) { try { ver = await getJson(`http://127.0.0.1:${port}/json/version`); break; } catch { await sleep(250); } }
  if (!ver) throw new Error("chrome devtools not up");
  for (const step of STEPS) {
    const target = await getJson("http://127.0.0.1:" + port + "/json/new?" + encodeURIComponent(step.url), { method: "PUT" });
    const { ws, send } = await attach(target.webSocketDebuggerUrl);
    await send("Page.enable"); await send("Runtime.enable");
    await send("Page.navigate", { url: step.url });
    await sleep(step.wait);
    for (const a of (step.acts || [])) {
      if (a.js) { const r = await send("Runtime.evaluate", { expression: a.js, returnByValue: true }); console.log(step.name, "act:", JSON.stringify(r.result && r.result.value)); }
      if (a.wait) await sleep(a.wait);
    }
    const shot = await send("Page.captureScreenshot", { format: "png" });
    fs.writeFileSync(path.join(OUT, step.name + ".png"), Buffer.from(shot.data, "base64"));
    console.log("saved", step.name);
    await send("Page.close");
    ws.close();
    await sleep(500);
  }
  chrome.kill();
  process.exit(0);
})().catch(e => { console.error(e); chrome.kill(); process.exit(1); });
