const fs = require('fs');
let html = fs.readFileSync('TODO.html', 'utf8');
let changes = 0;

// Helper: replace a step's class and check badge from todo to done
function markStepDone(html, stepPattern) {
  const regex = new RegExp(stepPattern, 'g');
  let count = 0;
  const result = html.replace(regex, (match) => {
    count++;
    return match
      .replace(/class="step todo"/g, 'class="step done"')
      .replace(/class="check todo"/g, 'class="check done"')
      .replace(/>Todo</g, '>Done<');
  });
  return { html: result, count };
}

// 1. Mark Step 4.6 as done (FloatingPlayerService is complete)
const step46Section = html.match(/<div class="step todo">[\s\S]*?STEP 4\.6[\s\S]*?<\/div>\s*<\/div>/);
if (step46Section) {
  const original = step46Section[0];
  const fixed = original
    .replace(/class="step todo"/g, 'class="step done"')
    .replace(/class="check todo"/g, 'class="check done"')
    .replace(/>Todo</g, '>Done<');
  html = html.replace(original, fixed);
  changes++;
  console.log('Step 4.6: marked done');
}

// 2. Fix Step 6.2: class="step todo" but check="Done" -> both done
const step62Section = html.match(/<div class="step todo">[\s\S]*?STEP 6\.2[\s\S]*?<\/div>\s*<\/div>/);
if (step62Section) {
  const original = step62Section[0];
  const fixed = original.replace(/class="step todo"/g, 'class="step done"');
  html = html.replace(original, fixed);
  changes++;
  console.log('Step 6.2: class fixed to done');
}

// 3. Mark Step 4.7 as inprogress (we're about to review it)
const step47Section = html.match(/<div class="step todo">[\s\S]*?STEP 4\.7[\s\S]*?<\/div>\s*<\/div>/);
if (step47Section) {
  const original = step47Section[0];
  const fixed = original
    .replace(/class="step todo"/g, 'class="step inprogress"')
    .replace(/class="check todo"/g, 'class="check inprogress"')
    .replace(/>Todo</g, '>In Progress<');
  html = html.replace(original, fixed);
  changes++;
  console.log('Step 4.7: marked inprogress');
}

// 4. Phase 8: Mark steps 8.1-8.16 as done
const phase8Start = html.indexOf('id="phase8"');
const phase9Start = html.indexOf('id="phase9"');
if (phase8Start > 0 && phase9Start > phase8Start) {
  const before = html.substring(0, phase8Start);
  let phase8Section = html.substring(phase8Start, phase9Start);
  const after = html.substring(phase9Start);
  
  // Count how many step todo in Phase 8
  const todoCount = (phase8Section.match(/class="step todo"/g) || []).length;
  
  // Replace all "step todo" with "step done" and "check todo" with "check done"
  // But only for steps 8.1 through 8.16 (steps 8.17-8.18 stay todo)
  // Strategy: replace all, then revert 8.17 and 8.18
  phase8Section = phase8Section
    .replace(/class="step todo"/g, 'class="step done"')
    .replace(/class="check todo"/g, 'class="check done"')
    .replace(/>Todo</g, '>Done<');
  
  // Revert 8.17 and 8.18 back to todo
  ['8.17', '8.18'].forEach(stepNum => {
    const stepRegex = new RegExp(`(<div class="step done">[\\s\\S]*?STEP ${stepNum}[\\s\\S]*?</div>\\s*</div>)`);
    const match = phase8Section.match(stepRegex);
    if (match) {
      const original = match[0];
      const reverted = original
        .replace(/class="step done"/g, 'class="step todo"')
        .replace(/class="check done"/g, 'class="check todo"')
        .replace(/>Done</g, '>Todo<');
      phase8Section = phase8Section.replace(original, reverted);
      console.log(`Step ${stepNum}: reverted to todo`);
    }
  });
  
  html = before + phase8Section + after;
  console.log(`Phase 8: ${todoCount} steps updated, 8.17-8.18 kept todo`);
  changes += todoCount;
}

// 5. Update stats bar - count all done steps
const totalSteps = (html.match(/class="step/g) || []).length;
const doneSteps = (html.match(/class="step done"/g) || []).length;
const todoSteps = (html.match(/class="step todo"/g) || []).length;
const inProgressSteps = (html.match(/class="step inprogress"/g) || []).length;
const blockedSteps = (html.match(/class="step blocked"/g) || []).length;

const pctLeft = Math.round((todoSteps / totalSteps) * 100);
const pctDone = Math.round((doneSteps / totalSteps) * 100);

console.log(`\nStats: ${totalSteps} total | ${doneSteps} done | ${inProgressSteps} in-progress | ${todoSteps} todo | ${blockedSteps} blocked`);
console.log(`Progress: ${pctDone}% done, ${pctLeft}% left`);

// Update the stats bar numbers
html = html.replace(
  /<div class="stat"><div class="num">\d+<\/div><div class="label">Step Done<\/div><\/div>/,
  `<div class="stat"><div class="num">${doneSteps}</div><div class="label">Steps Done</div></div>`
);
html = html.replace(
  /<div class="stat"><div class="num">\d+%<\/div><div class="label">Build Left<\/div><\/div>/,
  `<div class="stat"><div class="num">${pctLeft}%</div><div class="label">Build Left</div></div>`
);

// Update TOC Phase links with checkmarks for completed phases
// Phase 0-2 are fully done, Phase 7 is fully done
['phase0', 'phase7'].forEach(phaseId => {
  html = html.replace(
    new RegExp(`<a href="#${phaseId}">Phase \\d+</a>`),
    (match) => match.replace('</a>', ' ✓</a>')
  );
});

fs.writeFileSync('TODO.html', html);
console.log(`\nTODO.html updated (${changes} sections changed)`);
