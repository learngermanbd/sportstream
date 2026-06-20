const fs = require('fs');
let html = fs.readFileSync('TODO.html', 'utf8');

// Fix Step 3.7: revert from done back to todo (needs emulator)
const step37 = html.match(/<div class="step done">[\s\S]*?STEP 3\.7[\s\S]*?<\/div>\s*<\/div>/);
if (step37) {
  const orig = step37[0];
  const fixed = orig
    .replace(/class="step done"/g, 'class="step todo"')
    .replace(/class="check done"/g, 'class="check todo"')
    .replace(/>Done</g, '>Todo<');
  html = html.replace(orig, fixed);
  console.log('Step 3.7: reverted to todo');
}

// Mark Step 5.7 as done (Phase 5 features exist, compiled, reviewed)
const step57 = html.match(/<div class="step todo">[\s\S]*?STEP 5\.7[\s\S]*?<\/div>\s*<\/div>/);
if (step57) {
  const orig = step57[0];
  const fixed = orig
    .replace(/class="step todo"/g, 'class="step done"')
    .replace(/class="check todo"/g, 'class="check done"')
    .replace(/>Todo</g, '>Done<');
  html = html.replace(orig, fixed);
  console.log('Step 5.7: marked done');
}

// Fix Step 6.1 class (class="step todo" but that's correct for now - ad integration not done)
// But verify it's still todo

// Count steps
const doneCount = (html.match(/class="step done"/g) || []).length;
const todoCount = (html.match(/class="step todo"/g) || []).length;
const pctLeft = Math.round((todoCount / (doneCount + todoCount)) * 100);

// Update stats bar
html = html.replace(
  /<div class="stat"><div class="num">\d+<\/div><div class="label">Steps Done<\/div><\/div>/,
  `<div class="stat"><div class="num">${doneCount}</div><div class="label">Steps Done</div></div>`
);
html = html.replace(
  /<div class="stat"><div class="num">\d+%<\/div><div class="label">Build Left<\/div><\/div>/,
  `<div class="stat"><div class="num">${pctLeft}%</div><div class="label">Build Left</div></div>`
);

fs.writeFileSync('TODO.html', html);
console.log(`Done: ${doneCount}, Todo: ${todoCount}, Build Left: ${pctLeft}%`);
