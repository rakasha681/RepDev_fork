import fs from "node:fs";
import path from "node:path";

const sourceDir = "/tmp/fluentui-svg-icons/icons";
const outDir = "/opt/RepDev_fork/repdev-icons";

const C = {
  blue: "#60A5FA",
  cyan: "#22D3EE",
  green: "#34D399",
  yellow: "#FBBF24",
  orange: "#FB923C",
  red: "#F87171",
  purple: "#A78BFA",
  muted: "#94A3B8",
  slate: "#CBD5E1",
};

const iconMap = {
  "small-action-save": ["save", C.blue],
  "small-action-save-as": ["save_copy", C.blue],
  "small-add": ["add_circle", C.green],
  "small-remove": ["subtract_circle", C.orange],
  "small-delete": ["delete", C.red],
  "small-file": ["document_one_page", C.blue],
  "small-file-add": ["document_add", C.blue],
  "small-file-remove": ["document_dismiss", C.red],
  "small-file-new": ["document_one_page_add", C.blue],
  "small-file-open": ["open", C.blue],
  "small-folder": ["folder", C.yellow],
  "small-folder-add": ["folder_add", C.yellow],
  "small-folder-remove": ["folder", C.yellow, { badge: ["subtract_circle", C.red] }],
  "small-project": ["box", C.purple],
  "small-project-add": ["box", C.purple, { badge: ["add_circle", C.green] }],
  "small-project-remove": ["box", C.purple, { badge: ["subtract_circle", C.red] }],
  "small-sym": ["server", C.cyan],
  "small-sym-on": ["server", C.green],
  "small-sym-add": ["server", C.cyan, { badge: ["add_circle", C.green] }],
  "small-sym-remove": ["server", C.cyan, { badge: ["subtract_circle", C.red] }],
  "small-data": ["document_data", C.green],
  "small-db-record": ["database_stack", C.green],
  "small-db-field": ["table_simple", C.green],
  "small-repgen": ["document_code", C.purple],
  "small-repgen-demand": ["document_code", C.orange, { badge: ["play_circle", C.green] }],
  "small-run": ["play_circle", C.green],
  "small-run-fm": ["window_play", C.green],
  "small-install-repgen": ["document_arrow_up", C.green],
  "small-import": ["document_arrow_down", C.blue],
  "small-reports": ["document_bullet_list", C.blue],
  "small-print": ["print", C.slate],
  "small-compare": ["branch_compare", C.purple],
  "small-options": ["settings", C.slate],
  "small-exit": ["sign_out", C.red],
  "small-indent-less": ["text_indent_decrease_ltr", C.slate],
  "small-indent-more": ["text_indent_increase_ltr", C.slate],
  "small-cut": ["cut", C.slate],
  "small-copy": ["copy", C.slate],
  "small-paste": ["clipboard_paste", C.slate],
  "small-select-all": ["select_all_on", C.slate],
  "small-redo": ["arrow_redo", C.slate],
  "small-undo": ["arrow_undo", C.slate],
  "small-find": ["search", C.blue],
  "small-find-replace": ["search_settings", C.blue],
  "small-warning": ["warning", C.yellow],
  "small-errors": ["error_circle", C.red],
  "small-tasks": ["task_list_square_ltr", C.blue],
  "small-task-todo": ["checkbox_unchecked", C.blue],
  "small-task-fixme": ["wrench", C.orange],
  "small-task-bug": ["bug", C.red],
  "small-task-wtf": ["question_circle", C.purple],
  "small-task-test": ["beaker", C.green],
  "small-task-bookmark": ["bookmark", C.blue],
  "small-task-note": ["note", C.yellow],
  "small-highlight": ["highlight", C.yellow],
  "small-highlight-grey": ["highlight", C.muted],
  "small-format-code": ["code", C.purple],
  "small-insert-snippet": ["code_block", C.blue],
  "small-function": ["braces", C.purple],
  "small-keyword": ["key", C.orange],
  "small-snippet": ["text_bullet_list_square", C.blue],
  "small-variable": ["text_field", C.cyan],
  "small-define-var": ["textbox_settings", C.cyan],
  "small-surround": ["braces", C.purple],
  "small-surround-print": ["braces", C.purple, { badge: ["print", C.slate] }],
};

function readIconSource(name, style = "regular") {
  const candidates = [16, 20, 24, 28, 32, 48];
  for (const size of candidates) {
    const file = path.join(sourceDir, `${name}_${size}_${style}.svg`);
    if (fs.existsSync(file)) {
      return fs.readFileSync(file, "utf8");
    }
  }
  throw new Error(`Missing Fluent icon: ${name}_${style}`);
}

function parseSvg(raw) {
  const viewBoxMatch = raw.match(/\bviewBox="([^"]+)"/);
  const viewBox = viewBoxMatch ? viewBoxMatch[1] : "0 0 16 16";
  const parts = viewBox.trim().split(/\s+/).map(Number);
  const width = parts.length === 4 ? parts[2] : 16;
  const height = parts.length === 4 ? parts[3] : 16;
  const body = raw.replace(/^[\s\S]*?<svg\b[^>]*>/, "").replace(/<\/svg>\s*$/, "");
  return { body, width, height };
}

function renderLayer(name, color, canvasSize, layerSize, style, left, top) {
  const parsed = parseSvg(readIconSource(name, style));
  const scaleX = layerSize / parsed.width;
  const scaleY = layerSize / parsed.height;
  return [
    `<g transform="translate(${left} ${top}) scale(${scaleX} ${scaleY})"`,
    ` color="${color}" fill="${color}" stroke="${color}" style="color:${color};fill:${color};stroke:${color}">`,
    parsed.body,
    `</g>`,
  ].join("");
}

function renderIcon(spec, canvasSize) {
  const [base, color, opts = {}] = spec;
  const badgeSize = opts.badge ? Math.max(10, Math.round(canvasSize * 0.58)) : 0;
  const layers = [];
  layers.push(renderLayer(base, color, canvasSize, canvasSize, opts.style || "regular", 0, 0));
  if (opts.badge) {
    const [badgeName, badgeColor] = opts.badge;
    layers.push(renderLayer(badgeName, badgeColor, canvasSize, badgeSize, "filled", canvasSize - badgeSize, canvasSize - badgeSize));
  }
  return [
    `<?xml version="1.0" encoding="UTF-8"?>`,
    `<svg xmlns="http://www.w3.org/2000/svg" width="${canvasSize}" height="${canvasSize}" viewBox="0 0 ${canvasSize} ${canvasSize}">`,
    layers.join(""),
    `</svg>`,
    "",
  ].join("\n");
}

for (const [name, spec] of Object.entries(iconMap)) {
  fs.writeFileSync(path.join(outDir, `${name}.svg`), renderIcon(spec, 16), "utf8");
  fs.writeFileSync(path.join(outDir, `${name}-large.svg`), renderIcon(spec, 24), "utf8");
}

console.log(`Generated ${Object.keys(iconMap).length * 2} Fluent UI SVG assets.`);
