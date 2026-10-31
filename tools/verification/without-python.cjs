// macOS phase runner: no interpreter in PATH, and an inherited OS execution denial.
// This is the sole intentional negative interpreter-probe exception to the source scan.
const fs = require('node:fs');
const path = require('node:path');
const os = require('node:os');
const cp = require('node:child_process');
const crypto = require('node:crypto');
if (process.platform !== 'darwin') throw new Error('This Safari/Web Image phase environment requires macOS sandbox-exec');
const root = path.resolve(__dirname, '../..');
const out = path.join(root, 'build/reports/python-free');
fs.mkdirSync(out, {recursive: true});
const temporary = fs.mkdtempSync(path.join(os.tmpdir(), 'interlis-without-python-'));
const bin = path.join(temporary, 'bin'); fs.mkdirSync(bin);
const forbidden = /^(python[^/]*|pypy[^/]*|pip(?:[0-9.]*)|uvx?)$/i;
const folders = [path.dirname(process.execPath), path.join(process.env.JAVA_HOME || '', 'bin'), '/usr/bin', '/bin', '/usr/sbin', '/sbin'];
const originals = new Set(['/usr/bin/python3', '/usr/bin/pip3']);
for (const folder of folders) {
  if (!fs.existsSync(folder)) continue;
  for (const name of fs.readdirSync(folder)) {
    const source = path.join(folder, name), target = path.join(bin, name);
    try {
      if (forbidden.test(name)) { if (fs.statSync(source).isFile()) originals.add(source); continue; }
      const actual = fs.realpathSync(source);
      if (actual.split(path.sep).some(part => forbidden.test(part))) continue;
      if (fs.statSync(source).isFile() && !fs.existsSync(target)) fs.symlinkSync(source, target);
    } catch (error) { if (!['ENOENT', 'EACCES'].includes(error.code)) throw error; }
  }
}
const profile = '(version 1)(allow default)(deny process-exec (regex #".*/([Pp]ython[^/]*|[Pp]y[Pp]y[^/]*|[Pp][Ii][Pp][0-9.]*|[Uu][Vv][Xx]?)$"))';
const profileFile = path.join(temporary, 'deny-interpreters.sb'); fs.writeFileSync(profileFile, profile);
// Firefox cannot install its content sandbox inside an inherited macOS profile.
// The outer OS interpreter execution denial still covers every descendant.
const env = {...process.env, PATH: bin, MOZ_DISABLE_CONTENT_SANDBOX: "1"};
const commands = ['python', 'python3', 'pip', 'pip3', 'pypy', 'uv', ...[...originals].filter(p => fs.existsSync(p))];
const probe = `const c=require('node:child_process');const rows=${JSON.stringify(commands)}.map(command=>{const r=c.spawnSync(command,['--version'],{encoding:'utf8'});return {command,status:r.status,error:r.error?.code,stderr:r.stderr}});console.log(JSON.stringify(rows));if(rows.some(r=>r.status===0||(!r.error&&r.status!==126)))process.exit(1);`;
try {
  const tested = cp.spawnSync('/usr/bin/sandbox-exec', ['-f', profileFile, process.execPath, '-e', probe], {env, encoding: 'utf8'});
  if (tested.status !== 0) throw new Error('Interpreter absence probes failed: ' + tested.stdout + tested.stderr);
  const probes = JSON.parse(tested.stdout);
  if (!probes.some(r => path.isAbsolute(r.command) && r.error === 'EPERM')) throw new Error('No absolute interpreter execution denial was proved');
  const args = process.argv.slice(2);
  if (!args.length) throw new Error('Supply the command to execute inside the environment');
  if (path.basename(args[0]).includes('gradlew') && !args.includes('--no-daemon')) args.push('--no-daemon');
  const gitHead = cp.execFileSync('git', ['rev-parse', 'HEAD'], {cwd: root, encoding: 'utf8'}).trim();
  const dirty = cp.execFileSync('git', ['status', '--porcelain'], {cwd: root, encoding: 'utf8'});
  const report = {guardPid:process.pid,gitHead, dirty, status: 'running', startedAt: new Date().toISOString(), command: args, path: bin,
    firefoxContentSandbox: 'disabled only in this confined test environment to avoid sandbox_init EPERM; outer OS execution filter remains inherited',
    profile, profileSha256: crypto.createHash('sha256').update(profile).digest('hex'), probes,
    scope: 'No reachable interpreter: private PATH plus inherited OS process-exec denial, including absolute paths. Host installations are untouched.'};
  fs.writeFileSync(path.join(out, 'environment.json'), JSON.stringify(report, null, 2) + '\n');
  const child = cp.spawnSync('/usr/bin/sandbox-exec', ['-f', profileFile, ...args], {cwd: root, env, stdio: 'inherit'});
  report.exitCode = child.status; report.finishedAt = new Date().toISOString(); report.status = child.status === 0 ? 'passed' : 'failed';
  if (child.error) report.error = String(child.error);
  fs.writeFileSync(path.join(out, 'environment.json'), JSON.stringify(report, null, 2) + '\n');
  process.exitCode = child.status === 0 ? 0 : 1;
} finally { fs.rmSync(temporary, {recursive: true, force: true}); }
