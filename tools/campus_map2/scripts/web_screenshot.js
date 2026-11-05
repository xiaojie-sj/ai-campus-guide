const { spawn } = require("node:child_process");
const fs = require("node:fs");
const os = require("node:os");
const path = require("node:path");

const CHROME_CANDIDATES = [
  process.env.CHROME_PATH,
  "C:/Program Files/Google/Chrome/Application/chrome.exe",
  "C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe",
  "/usr/bin/google-chrome",
  "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome",
].filter(Boolean);
const CHROME = CHROME_CANDIDATES.find(p => fs.existsSync(p));
if (!CHROME) {
  console.error("Chrome/Edge not found. Set CHROME_PATH to a Chromium-based browser.");
  process.exit(1);
}
const URL = process.argv[2];
const OUT = process.argv[3];
const port = 9411;
const profile = path.join(os.tmpdir(), "campus-shot-" + Date.now());

const chrome = spawn(CHROME, [
  "--headless=new", "--remote-debugging-port=" + port,
  "--user-data-dir=" + profile,
  "--disable-gpu-sandbox", "--no-first-run", "--no-default-browser-check",
  "--use-gl=angle", "--use-angle=swiftshader", "--enable-unsafe-swiftshader",
  "--window-size=1600,1000", "--force-device-scale-factor=1",
  "--hide-scrollbars", "about:blank"
], { stdio: "ignore" });

const sleep = ms => new Promise(r => setTimeout(r, ms));

async function getJson(u, opts) {
  const r = await fetch(u, opts);
  return r.json();
}

(async () => {
  let ver = null;
  for (let i = 0; i < 60; i++) {
    try { ver = await getJson(`http://127.0.0.1:${port}/json/version`); break; } catch (e) { await sleep(250); }
  }
  if (!ver) throw new Error("chrome devtools not up");
  const target = await getJson(`http://127.0.0.1:${port}/json/new?` + encodeURIComponent(URL), { method: "PUT" });
  const ws = new WebSocket(target.webSocketDebuggerUrl);
  let id = 0; const pending = new Map();
  const send = (method, params = {}) => new Promise((res, rej) => {
    const mid = ++id; pending.set(mid, { res, rej });
    ws.send(JSON.stringify({ id: mid, method, params }));
  });
  const logs = [];
  await new Promise(r => ws.addEventListener("open", r));
  ws.addEventListener("message", ev => {
    const msg = JSON.parse(ev.data);
    if (msg.id && pending.has(msg.id)) { pending.get(msg.id).res(msg.result); pending.delete(msg.id); }
    if (msg.method === "Runtime.consoleAPICalled") logs.push(msg.params.args.map(a => a.value ?? a.description).join(" "));
    if (msg.method === "Runtime.exceptionThrown") logs.push("EXC " + JSON.stringify(msg.params.exceptionDetails.text));
  });
  await send("Page.enable"); await send("Runtime.enable");
  await send("Emulation.setDeviceMetricsOverride", { width: 1600, height: 1000, deviceScaleFactor: 1, mobile: false });
  await send("Page.navigate", { url: URL });
  await sleep(20000);
  const info = await send("Runtime.evaluate", { expression: "JSON.stringify({ready: document.readyState, stats: (document.getElementById('stats')||{}).textContent, loading: document.getElementById('loading') ? getComputedStyle(document.getElementById('loading')).opacity : null, canvas: document.querySelectorAll('canvas').length})", returnByValue: true });
  console.log("PAGE", info.result && info.result.value);
  console.log("LOGS", logs.slice(-15).join(" | "));
  const shot = await send("Page.captureScreenshot", { format: "png", captureBeyondViewport: false });
  fs.writeFileSync(OUT, Buffer.from(shot.data, "base64"));
  console.log("SAVED", OUT, fs.statSync(OUT).size);
  ws.close(); chrome.kill(); process.exit(0);
})().catch(e => { console.error("ERR", e.message); try { chrome.kill(); } catch (_) {} process.exit(1); });
