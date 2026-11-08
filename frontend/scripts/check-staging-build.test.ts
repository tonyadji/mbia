// @vitest-environment node
import { execFileSync } from 'node:child_process';
import { mkdirSync, mkdtempSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { dirname, join } from 'node:path';
import { checkStagingBuild } from './check-staging-build.js';

const script = join(import.meta.dirname, 'check-staging-build.js');
const ROBOTS = 'User-agent: *\nDisallow: /\n';
let dir: string;

function write(path: string, content: string) {
  mkdirSync(dirname(join(dir, path)), { recursive: true });
  writeFileSync(join(dir, path), content);
}

function writeBuild(js: string) {
  write('index.html', '<script type="module" src="/assets/index-a1.js"></script>');
  write('assets/index-a1.js', js);
  write('robots.txt', ROBOTS);
}

function runCli(): { status: number; output: string } {
  try {
    const output = execFileSync(process.execPath, [script, dir], {
      encoding: 'utf8',
      stdio: 'pipe',
    });
    return { status: 0, output };
  } catch (error) {
    const failure = error as { status: number; stderr: string };
    return { status: failure.status, output: failure.stderr };
  }
}

beforeEach(() => {
  dir = mkdtempSync(join(tmpdir(), 'mbia-staging-build-'));
});

afterEach(() => {
  rmSync(dir, { recursive: true, force: true });
});

describe('checkStagingBuild', () => {
  it('accepts a build with staging addresses and the robots.txt', () => {
    writeBuild('var a=`https://api.example.test/api/v1`;');
    expect(checkStagingBuild(dir)).toEqual([]);
  });

  it.each([
    'var a=`http://localhost:8080/api/v1`;',
    'var a="http://localhost/realms/mbia";',
    "var a='https://127.0.0.1/x';",
    'var a=`http://[::1]:8081`;',
    'var a=`http://0.0.0.0:5173`;',
    'var a=`http://api.localhost/api/v1`;',
  ])('refuses a local address: %s', (js) => {
    writeBuild(js);
    const problems = checkStagingBuild(dir);
    expect(problems).toHaveLength(1);
    expect(problems[0]).toMatch(/^assets\/index-a1\.js: local address/);
  });

  it('tolerates only the exact URL-parsing bases of react-router and oidc-client-ts', () => {
    writeBuild(
      'let r=`http://localhost`;tr=new URL("http://localhost");new URL(e,`http://127.0.0.1`);',
    );
    expect(checkStagingBuild(dir)).toEqual([]);
  });

  it('checks every text file, the stylesheet and the HTML page included', () => {
    writeBuild('');
    write('assets/index-b2.css', '@import url(http://localhost:5173/x.css);');
    write('index.html', '<link href="http://localhost:5173/">');
    expect(checkStagingBuild(dir)).toHaveLength(2);
  });

  it('refuses a build without robots.txt', () => {
    write('index.html', '<div id="root"></div>');
    expect(checkStagingBuild(dir)).toEqual(['robots.txt: missing']);
  });

  it('refuses a robots.txt that allows indexing', () => {
    writeBuild('');
    write('robots.txt', 'User-agent: *\nDisallow:\n');
    expect(checkStagingBuild(dir)).toEqual([
      'robots.txt: must contain "User-agent: *" and "Disallow: /"',
    ]);
  });

  it('refuses a missing build directory', () => {
    expect(checkStagingBuild(join(dir, 'dist'))).toEqual([`${join(dir, 'dist')}: no build found`]);
  });
});

describe('the command', () => {
  it('exits with 0 when the build can be deployed', () => {
    writeBuild('');
    expect(runCli()).toMatchObject({ status: 0 });
  });

  it('exits with 1 and lists the problems otherwise', () => {
    writeBuild('var a=`http://localhost:8080/api/v1`;');
    const { status, output } = runCli();
    expect(status).toBe(1);
    expect(output).toContain('local address "localhost"');
  });
});
