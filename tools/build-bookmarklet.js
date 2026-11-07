import { readFile, writeFile } from 'node:fs/promises';

// Encode the complete source to preserve whitespace, Unicode and URL characters.
const source = await readFile(new URL('./token-bookmarklet.js', import.meta.url), 'utf8');
const url = 'javascript:' + encodeURIComponent(source.trim());
const output = new URL('../docs/mobile-refresh.md', import.meta.url);
const doc = await readFile(output, 'utf8');
const start = '<!-- bookmarklet:start -->';
const end = '<!-- bookmarklet:end -->';
const begin = doc.indexOf(start);
const finish = doc.indexOf(end, begin + start.length);
if (begin < 0 || finish < 0) throw new Error('Missing bookmarklet markers in docs/mobile-refresh.md');
await writeFile(output, doc.slice(0, begin) + `${start}\n\n\`\`\`text\n${url}\n\`\`\`\n\n` + doc.slice(finish));
console.log('Updated docs/mobile-refresh.md bookmarklet.');
