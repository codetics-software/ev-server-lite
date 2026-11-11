import { readdirSync, readFileSync, statSync } from "node:fs";
import { join, relative } from "node:path";
import ts from "typescript";

const root = new URL("..", import.meta.url).pathname;
const sourceDirs = ["app", "features", "components", "hooks", "lib"];
const skippedDir = join(root, "lib/i18n");
const messagesDir = join(root, "lib/i18n/messages");
const ignoredAttributes = new Set([
  "className",
  "href",
  "src",
  "type",
  "variant",
  "size",
  "key",
  "id",
  "name",
  "role",
  "side",
  "align",
  "lang",
  "autoComplete",
  "inputMode",
  "rel",
  "target",
  "method",
  "action",
  "accept",
  "pattern",
  "orientation",
  "data-slot",
]);
const translators = new Set(["t", "tp", "translate"]);

function walk(dir) {
  return readdirSync(dir).flatMap((entry) => {
    const path = join(dir, entry);
    if (statSync(path).isDirectory()) {
      return path === skippedDir ? [] : walk(path);
    }
    return /\.(ts|tsx)$/.test(entry) && !entry.endsWith(".d.ts") ? [path] : [];
  });
}

function parse(path) {
  return ts.createSourceFile(
    path,
    readFileSync(path, "utf8"),
    ts.ScriptTarget.Latest,
    true,
    path.endsWith(".tsx") ? ts.ScriptKind.TSX : ts.ScriptKind.TS,
  );
}

function literalText(node) {
  if (ts.isStringLiteral(node) || ts.isNoSubstitutionTemplateLiteral(node)) {
    return node.text;
  }
  return null;
}

function loadCatalog() {
  const catalog = new Map();
  const problems = [];
  for (const path of walk(messagesDir)) {
    const file = parse(path);
    const visit = (node) => {
      if (ts.isPropertyAssignment(node) && ts.isObjectLiteralExpression(node.initializer)) {
        const key = literalText(node.name) ?? (ts.isIdentifier(node.name) ? node.name.text : null);
        const values = {};
        for (const property of node.initializer.properties) {
          if (ts.isPropertyAssignment(property) && ts.isIdentifier(property.name)) {
            values[property.name.text] = literalText(property.initializer);
          }
        }
        if (key !== null && ("en" in values || "es" in values)) {
          if (catalog.has(key)) {
            const previous = catalog.get(key);
            if (previous.en !== values.en || previous.es !== values.es) {
              problems.push(`Conflicting translations for "${key}" in ${relative(root, path)}`);
            }
          }
          catalog.set(key, values);
          const placeholders = (text) =>
            [...(text ?? "").matchAll(/\{(\w+)\}/g)]
              .map((m) => m[1])
              .sort()
              .join(",");
          for (const locale of ["en", "es"]) {
            if (!values[locale]) {
              problems.push(`Missing ${locale} for "${key}" in ${relative(root, path)}`);
            } else if (placeholders(values[locale]) !== placeholders(key)) {
              problems.push(
                `Placeholder mismatch (${locale}) for "${key}" in ${relative(root, path)}`,
              );
            }
          }
          return;
        }
      }
      ts.forEachChild(node, visit);
    };
    visit(file);
  }
  return { catalog, problems };
}

const untranslatable = new Set([
  "Kevin Vavelin · Codetics",
  "Kevin Vavelin,",
  "EV Supervision Lite — built by Kevin Vavelin, Codetics",
  "codetics.fr",
  "Codetics",
  "Codetics Supervision",
  "Stripe",
  "Payload Too Large",
  "Bad Gateway",
  "Forbidden",
  "American Express",
  "Cartes Bancaires",
  '"Source Sans 3", system-ui, sans-serif',
]);

function looksLikeText(text) {
  const value = text.trim();
  if (untranslatable.has(value) || /^use[A-Z]\w+ must be used within/.test(value)) {
    return false;
  }
  if (value.length < 2 || !/\p{L}/u.test(value)) {
    return false;
  }
  if (/^(https?:|mailto:|\/|\.{0,2}\/|@\/|#|data:|[\w-]+\.(tsx?|css|png|svg|json)$)/.test(value)) {
    return false;
  }
  if (/[À-ÿ’«»…]/.test(value)) {
    return true;
  }
  if (/^[a-z0-9:_/.[\]()!%#&>*=,+-]+( [a-z0-9:_/.[\]()!%#&>*=,+-]+)*$/.test(value)) {
    return false;
  }
  return /\s/.test(value) && /\p{Lu}|[.?!:]$/u.test(value) && /\p{L}{3,}/u.test(value);
}

const uiProperties = new Set([
  "label",
  "title",
  "description",
  "placeholder",
  "header",
  "heading",
  "empty",
  "emptyTitle",
  "emptyDescription",
  "text",
  "message",
  "detail",
  "hint",
  "help",
  "tooltip",
  "confirm",
  "confirmLabel",
  "cancelLabel",
  "actionLabel",
  "submitLabel",
  "success",
  "error",
  "caption",
  "subtitle",
  "alt",
  "aria-label",
]);

function looksLikeWord(text) {
  return !untranslatable.has(text.trim()) && /^\p{Lu}\p{Ll}{2,}$/u.test(text.trim());
}

function uiContext(node) {
  let current = node.parent;
  while (
    current &&
    (ts.isConditionalExpression(current) ||
      ts.isParenthesizedExpression(current) ||
      ts.isBinaryExpression(current) ||
      ts.isJsxExpression(current))
  ) {
    current = current.parent;
  }
  if (!current) {
    return false;
  }
  if (ts.isJsxAttribute(current)) {
    return !ignoredAttributes.has(current.name.getText());
  }
  if (
    ts.isPropertyAssignment(current) &&
    (ts.isIdentifier(current.name) || ts.isStringLiteral(current.name)) &&
    uiProperties.has(current.name.text)
  ) {
    return true;
  }
  let ancestor = node.parent;
  while (ancestor) {
    if (
      ts.isCallExpression(ancestor) &&
      ts.isIdentifier(ancestor.expression) &&
      ancestor.expression.text === "translated"
    ) {
      return true;
    }
    ancestor = ancestor.parent;
  }
  return false;
}

function attributeName(node) {
  let current = node.parent;
  while (current && !ts.isJsxAttribute(current)) {
    if (ts.isJsxElement(current) || ts.isJsxSelfClosingElement(current) || ts.isBlock(current)) {
      return null;
    }
    current = current.parent;
  }
  return current ? current.name.getText() : null;
}

function transparentParent(node) {
  const parent = node.parent;
  if (!parent) {
    return false;
  }
  if (ts.isParenthesizedExpression(parent)) {
    return true;
  }
  if (ts.isConditionalExpression(parent)) {
    return parent.condition !== node;
  }
  if (ts.isBinaryExpression(parent)) {
    return [ts.SyntaxKind.QuestionQuestionToken, ts.SyntaxKind.BarBarToken].includes(
      parent.operatorToken.kind,
    );
  }
  return false;
}

function translatorCall(node) {
  let current = node;
  while (transparentParent(current)) {
    current = current.parent;
  }
  const call = current.parent;
  if (call && ts.isCallExpression(call) && call.arguments.includes(current)) {
    const callee = call.expression;
    const name = ts.isIdentifier(callee)
      ? callee.text
      : ts.isPropertyAccessExpression(callee)
        ? callee.name.text
        : null;
    return name && translators.has(name) ? name : null;
  }
  return null;
}

function insideFunction(node) {
  let current = node.parent;
  while (current) {
    if (ts.isFunctionLike(current)) {
      return true;
    }
    current = current.parent;
  }
  return false;
}

function directlyRendered(node) {
  let current = node;
  while (
    transparentParent(current) ||
    (current.parent &&
      ts.isJsxExpression(current.parent) &&
      ts.isJsxAttribute(current.parent.parent))
  ) {
    current = current.parent;
  }
  const parent = current.parent;
  if (!parent) {
    return false;
  }
  if (ts.isJsxAttribute(parent)) {
    return !ignoredAttributes.has(parent.name.getText());
  }
  if (ts.isJsxExpression(parent)) {
    return ts.isJsxElement(parent.parent) || ts.isJsxFragment(parent.parent);
  }
  if (
    ts.isPropertyAssignment(parent) &&
    parent.initializer === current &&
    (ts.isIdentifier(parent.name) || ts.isStringLiteral(parent.name)) &&
    uiProperties.has(parent.name.text)
  ) {
    return insideFunction(parent);
  }
  return false;
}

function inImportOrType(node) {
  let current = node.parent;
  while (current) {
    if (
      ts.isImportDeclaration(current) ||
      ts.isExportDeclaration(current) ||
      ts.isLiteralTypeNode(current) ||
      ts.isTypeNode(current)
    ) {
      return true;
    }
    current = current.parent;
  }
  return false;
}

const { catalog, problems } = loadCatalog();
const missing = [];
const untranslated = [];
const used = new Set();

for (const path of sourceDirs.flatMap((dir) => walk(join(root, dir)))) {
  const file = parse(path);
  const where = (node) =>
    `${relative(root, path)}:${file.getLineAndCharacterOfPosition(node.getStart()).line + 1}`;
  const visit = (node) => {
    const text = literalText(node);
    if (text !== null && !inImportOrType(node)) {
      if (translatorCall(node)) {
        used.add(text);
        if (!catalog.has(text)) {
          missing.push(`${where(node)}  "${text}"`);
        }
      } else if (
        (looksLikeText(text) ||
          (looksLikeWord(text) && (uiContext(node) || directlyRendered(node)))) &&
        (!catalog.has(text) || directlyRendered(node)) &&
        !ignoredAttributes.has(attributeName(node) ?? "")
      ) {
        untranslated.push(`${where(node)}  "${text}"`);
      }
    } else if (ts.isTemplateExpression(node)) {
      const parts = [node.head.text, ...node.templateSpans.map((span) => span.literal.text)];
      if (
        parts.some(looksLikeText) &&
        !node.getText().includes("function(){") &&
        !/^`\s*</.test(node.getText())
      ) {
        untranslated.push(`${where(node)}  \`${node.getText().slice(1, 80)}\``);
      }
    } else if (ts.isJsxText(node)) {
      const value = node.text.replace(/\s+/g, " ").trim();
      if (value && /\p{L}{2,}/u.test(value) && !untranslatable.has(value)) {
        untranslated.push(`${where(node)}  ${value}`);
      }
    }
    ts.forEachChild(node, visit);
  };
  visit(file);
}

const filter = process.argv[2];
const scoped = (lines) => (filter ? lines.filter((line) => line.startsWith(filter)) : lines);
const report = [
  ["Catalog problems", filter ? [] : problems],
  ["Translator keys missing from the catalog", scoped(missing)],
  ["Hard-coded text not going through the catalog", scoped(untranslated)],
];
let failed = false;
for (const [title, lines] of report) {
  if (lines.length) {
    failed = true;
    console.log(`\n${title} (${lines.length})`);
    for (const line of lines) {
      console.log(`  ${line}`);
    }
  }
}
console.log(`\nCatalog entries: ${catalog.size}`);
process.exit(failed ? 1 : 0);
