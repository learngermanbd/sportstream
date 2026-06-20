/**
 * Comprehensive rename: SportStream → Streamify
 * Renames packages, classes, themes, strings, build config, docs
 * across both streamify/ and streamify-clean/
 */
const fs = require('fs');
const path = require('path');

const dirs = ['streamify', 'streamify-clean'];
const exts = ['.kt', '.xml', '.kts', '.js', '.html', '.txt', '.md', '.pro', '.toml', '.properties', '.json', '.env'];
const skipDirs = ['node_modules', '.git', '.gradle', 'build', '.kotlin'];

function shouldSkip(fp) {
  for (const d of skipDirs) {
    if (fp.includes('/' + d + '/') || fp.includes('\\' + d + '\\')) return true;
  }
  return false;
}

function shouldProcess(fp) {
  for (const e of exts) { if (fp.endsWith(e)) return true; }
  return false;
}

let totalFiles = 0, changedFiles = 0;

for (const dir of dirs) {
  const root = path.resolve(__dirname, '..', '..', dir);
  if (!fs.existsSync(root)) { console.log(dir + ': NOT FOUND, skipping'); continue; }
  
  function walk(current) {
    let entries;
    try { entries = fs.readdirSync(current, { withFileTypes: true }); }
    catch (e) { return; }
    
    for (const entry of entries) {
      const full = path.join(current, entry.name);
      if (shouldSkip(full)) continue;
      if (entry.isDirectory()) { walk(full); continue; }
      if (!shouldProcess(full)) continue;
      
      totalFiles++;
      let content = fs.readFileSync(full, 'utf8');
      let changed = false;
      
      const replacements = [
        ['com.streamify.app', 'com.streamify.app'],
        ['com.streamify.admin', 'com.streamify.admin'],
        ['StreamifyMessagingService', 'StreamifyMessagingService'],
        ['StreamifyAdminApp', 'StreamifyAdminApp'],
        ['StreamifyApp', 'StreamifyApp'],
        ['Theme.StreamifyAdmin', 'Theme.StreamifyAdmin'],
        ['Theme.Streamify', 'Theme.Streamify'],
        ['Streamify.BottomNav', 'Streamify.BottomNav'],
        ['Streamify.PlayerOverlay', 'Streamify.PlayerOverlay'],
        ['Streamify.GlassCard', 'Streamify.GlassCard'],
        ['Streamify.Text', 'Streamify.Text'],
        ['Streamify.BottomNavIndicator', 'Streamify.BottomNavIndicator'],
        ['Streamify.Button', 'Streamify.Button'],
        ['STREAMIFY', 'STREAMIFY'],
        ['streamify', 'streamify'],
      ];
      
      for (const [oldStr, newStr] of replacements) {
        if (content.includes(oldStr)) {
          content = content.split(oldStr).join(newStr);
          changed = true;
        }
      }
      
      if (changed) {
        fs.writeFileSync(full, content, 'utf8');
        changedFiles++;
      }
    }
  }
  
  const before = changedFiles;
  walk(root);
  console.log(dir + ': ' + (changedFiles - before) + ' files changed');
}

console.log('TOTAL: ' + changedFiles + ' files changed out of ' + totalFiles + ' scanned');
