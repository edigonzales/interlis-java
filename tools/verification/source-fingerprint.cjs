'use strict';

const fs = require('node:fs');
const path = require('node:path');
const crypto = require('node:crypto');
const cp = require('node:child_process');
const root = fs.realpathSync(path.resolve(__dirname, '../..'));
const hash = value => crypto.createHash('sha256').update(value).digest('hex');

function fingerprint() {
    const inputs = JSON.parse(fs.readFileSync(path.join(root, 'testdata/verification/source-inputs.json'), 'utf8'));
    const candidates = cp.execFileSync('git', ['ls-files', '--cached', '--others', '--exclude-standard', '-z'],
        {cwd: root, encoding: 'utf8', maxBuffer: 16 * 1024 * 1024}).split('\0');
    const files = [...new Set(candidates.filter(file => inputs.paths.includes(file)
        || inputs.prefixes.some(prefix => file.startsWith(prefix))
        || inputs.suffixes.some(suffix => file.endsWith(suffix))))].sort();
    if (files.length < 500) throw Error('Incomplete verification source binding');
    return hash(files.map(file => {
        const absolute = path.join(root, file);
        const stat = fs.lstatSync(absolute, {throwIfNoEntry: false});
        if (stat && (!stat.isFile() || fs.realpathSync(absolute) !== absolute)) {
            throw Error('Unsafe source input: ' + file);
        }
        return file + '\0' + (stat ? hash(fs.readFileSync(absolute)) : '<deleted>') + '\n';
    }).join(''));
}

function commitInfo() {
    return {
        gitCommit: cp.execFileSync('git', ['rev-parse', 'HEAD'], {cwd: root, encoding: 'utf8'}).trim(),
        dirty: cp.execFileSync('git', ['status', '--porcelain'], {cwd: root, encoding: 'utf8'}).trim() !== ''
    };
}

module.exports = {root, hash, fingerprint, commitInfo};
