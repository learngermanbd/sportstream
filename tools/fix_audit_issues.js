const fs = require('fs');

// ── Fix TODO.html ──
let html = fs.readFileSync('TODO.html', 'utf8');

// Fix Step 6.1, 6.5, 7.13: step done → step todo
const fixSteps = ['6.1', '6.5', '7.13'];
for (const step of fixSteps) {
  // Replace the step div class from "done" to "todo"
  const pattern = new RegExp(`(<div class="step) done(">\\s*<div class="step-header">\\s*<span class="step-num [^"]*">STEP ${step.replace('.','\\.')}</span>)`);
  html = html.replace(pattern, '$1 todo$2');
}

// Update stats bar numbers
const doneCount = (html.match(/class="step done"/g) || []).length;
const todoCount = (html.match(/class="step todo"/g) || []).length;

// Update the "Steps Done" stat
html = html.replace(
  /<div class="stat"><div class="num">\d+<\/div><div class="label">Steps Done/,
  `<div class="stat"><div class="num">${doneCount}</div><div class="label">Steps Done`
);

// Update the "Build Left" percentage
const total = doneCount + todoCount;
const pctLeft = total > 0 ? Math.round((todoCount / total) * 100) : 0;
html = html.replace(
  /<div class="stat"><div class="num">\d+%<\/div><div class="label">Build Left/,
  `<div class="stat"><div class="num">${pctLeft}%</div><div class="label">Build Left`
);

fs.writeFileSync('TODO.html', html);
console.log('TODO.html fixed:', doneCount, 'done,', todoCount, 'todo,', pctLeft + '% left');

// ── Fix code TODOs referencing step 7.13 ──
const fixes = [
  { file: 'app/src/main/java/com/streamify/app/security/IntegrityChecker.kt', old: 'TODO(step 7.13): wire from EncryptedConstants or BuildConfig.', new: 'TODO(Phase 7): wire from EncryptedConstants or BuildConfig.' },
  { file: 'app/src/main/java/com/streamify/app/security/RequestSigner.kt', old: 'TODO(step 7.13): wire from EncryptedConstants.', new: 'TODO(Phase 7): wire from EncryptedConstants.' },
  { file: 'app/src/main/java/com/streamify/app/security/SSLPinner.kt', old: 'TODO(step 7.13): replace placeholder pins with real values', new: 'TODO(Phase 7): replace placeholder pins with real production cert pins' },
  { file: 'app/src/main/res/xml/network_security_config.xml', old: 'TODO(step 7.13): replace with real pin from production cert.', new: 'TODO(Phase 7): replace with real pin from production cert.' },
];

for (const f of fixes) {
  try {
    let content = fs.readFileSync(f.file, 'utf8');
    if (content.includes(f.old)) {
      content = content.replace(f.old, f.new);
      fs.writeFileSync(f.file, content);
      console.log('Fixed:', f.file);
    } else {
      console.log('Skipped (not found):', f.file);
    }
  } catch (e) {
    console.log('Error:', f.file, e.message);
  }
}

console.log('\nAudit fixes complete.');
