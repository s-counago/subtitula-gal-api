import { readFile, writeFile } from "node:fs/promises";

const fragmentPath = new URL("./dashboard.fragment.html", import.meta.url);
const outputPath = new URL("./dashboard.html", import.meta.url);
const fragment = await readFile(fragmentPath, "utf8");

const shell = `<!doctype html>
<html lang="es">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <meta name="referrer" content="no-referrer">
  <meta http-equiv="Content-Security-Policy" content="default-src 'none'; script-src 'unsafe-inline'; style-src 'unsafe-inline'; img-src data:; font-src data:; base-uri 'none'; form-action 'none'">
  <title>Centro operativo de Subtitula</title>
  <style>
    :root { color-scheme: light dark; --background: light-dark(#f8fafc, #171717); --foreground: light-dark(#172033, #f5f5f5); --card: light-dark(#fff, #242424); --muted: light-dark(#536174, #b4b4b4); --border: light-dark(#dbe3ee, #454545); --primary: light-dark(#1769aa, #8dcaff); --primary-foreground: #fff; }
    * { box-sizing: border-box; }
    body { max-width: 1040px; margin: 0 auto; padding: 1.25rem; background: var(--background); color: var(--foreground); font: 16px/1.5 system-ui, sans-serif; }
    h2, h3, p { margin-top: 0; } h2 { margin-bottom: .15rem; } h3 { margin-bottom: .45rem; }
    .card { padding: 1rem; border: 1px solid var(--border); border-radius: .7rem; background: var(--card); }
    .btn { display: inline-flex; border: 1px solid var(--border); border-radius: .45rem; padding: .45rem .7rem; background: var(--card); color: var(--foreground); cursor: pointer; font: inherit; text-decoration: none; }
    .btn-primary { border-color: var(--primary); background: var(--primary); color: var(--primary-foreground); } .btn-ghost { background: transparent; }
    .viz-stat { display: grid; gap: .15rem; } .viz-stat-value { font-size: 1.75rem; } .viz-badge { border: 1px solid var(--border); border-radius: 999px; padding: .08rem .45rem; font-size: .78rem; white-space: nowrap; }
    .text-muted { color: var(--muted); } .text-small { font-size: .875rem; }.form-label { display: block; margin-bottom: .25rem; font-size: .875rem; }.form-control, .form-select { width: 100%; min-height: 2.4rem; border: 1px solid var(--border); border-radius: .45rem; padding: .45rem; background: var(--card); color: var(--foreground); font: inherit; }.form-check { display: flex; gap: .45rem; align-items: start; cursor: pointer; }.form-check-input { margin-top: .3rem; }.sr-only { position:absolute; width:1px; height:1px; padding:0; margin:-1px; overflow:hidden; clip:rect(0,0,0,0); white-space:nowrap; border:0; }
  </style>
</head>
<body>
${fragment}
</body>
</html>
`;

await writeFile(outputPath, shell, "utf8");
