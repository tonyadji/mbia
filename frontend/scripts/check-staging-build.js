// Checks a staging build (Phase 7 plan PR-66): no address of a developer machine, and a robots.txt that disallows
// everything (OQ-070).
// Usage: node scripts/check-staging-build.js [build directory, default dist]
import { existsSync, readdirSync, readFileSync, statSync } from 'node:fs';
import { join, relative, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const LOCAL_HOST = /localhost|127\.0\.0\.1|\[::1\]|0\.0\.0\.0/gi;

// Dummy bases against which dependencies parse relative URLs; no request ever goes to them: `http://localhost`
// (react-router, when there is no window) and `http://127.0.0.1` (oidc-client-ts, reading the sign-in response).
// Only these exact literals are tolerated: with a port, a path or anything else, the check fails.
const URL_PARSING_BASE = /(["'`])http:\/\/(localhost|127\.0\.0\.1)\1/g;

const TEXT_FILE = /\.(html|js|mjs|css|txt|json|map|webmanifest|svg|xml)$/i;

function filesOf(dir) {
  return readdirSync(dir).flatMap((name) => {
    const path = join(dir, name);
    return statSync(path).isDirectory() ? filesOf(path) : [path];
  });
}

/** Returns the problems found in the build directory `dir`; an empty list means it can be deployed to staging. */
export function checkStagingBuild(dir) {
  if (!existsSync(dir)) return [`${dir}: no build found`];
  const problems = [];

  for (const file of filesOf(dir).filter((path) => TEXT_FILE.test(path))) {
    const content = readFileSync(file, 'utf8').replace(URL_PARSING_BASE, '');
    for (const match of content.matchAll(LOCAL_HOST)) {
      const start = Math.max(0, match.index - 30);
      const excerpt = content.slice(start, match.index + match[0].length + 30).replace(/\s+/g, ' ');
      problems.push(`${relative(dir, file)}: local address "${match[0]}" in …${excerpt}…`);
    }
  }

  const robots = join(dir, 'robots.txt');
  if (!existsSync(robots)) {
    problems.push('robots.txt: missing');
  } else {
    const content = readFileSync(robots, 'utf8');
    if (!/^User-agent: \*$/m.test(content) || !/^Disallow: \/$/m.test(content)) {
      problems.push('robots.txt: must contain "User-agent: *" and "Disallow: /"');
    }
  }

  return problems;
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  const dir = resolve(process.argv[2] ?? 'dist');
  const problems = checkStagingBuild(dir);
  if (problems.length > 0) {
    console.error(`Staging build check failed (${String(problems.length)} problem(s)):`);
    for (const problem of problems) console.error(`  - ${problem}`);
    process.exit(1);
  }
  console.log(`Staging build check passed: ${dir}`);
}
