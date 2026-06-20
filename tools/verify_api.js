/**
 * SportStream API verification script.
 * Tests all major endpoints against the running backend.
 */
const http = require('http');

const BASE = 'http://localhost:4000';
let accessToken = '';

function req(method, path, body = null) {
  return new Promise((resolve, reject) => {
    const url = new URL(path, BASE);
    const options = {
      hostname: url.hostname,
      port: url.port,
      path: url.pathname + url.search,
      method,
      headers: {
        'Content-Type': 'application/json',
        ...(accessToken && { Authorization: `Bearer ${accessToken}` })
      },
      timeout: 10000
    };

    const client = http.request(options, (res) => {
      let data = '';
      res.on('data', (chunk) => { data += chunk; });
      res.on('end', () => {
        try {
          resolve({ status: res.statusCode, body: JSON.parse(data) });
        } catch {
          resolve({ status: res.statusCode, body: data.substring(0, 200) });
        }
      });
    });

    client.on('error', (err) => { reject(err); });
    client.on('timeout', () => { client.destroy(); reject(new Error('timeout')); });

    if (body) client.write(JSON.stringify(body));
    client.end();
  });
}

async function main() {
  console.log('═══════════════════════════════════════════');
  console.log('  SportStream API Verification Suite');
  console.log('═══════════════════════════════════════════\n');

  let passed = 0;
  let failed = 0;

  const tests = [
    // ─── HEALTH ───
    { name: 'GET /api/health', fn: async () => {
      const r = await req('GET', '/api/health');
      if (r.status === 200 && r.body.name === 'sportstream-admin-backend') return '✅ 200 OK';
      return `❌ ${r.status} | ${JSON.stringify(r.body).substring(0,100)}`;
    }},
    // ─── AUTH: LOGIN ───
    { name: 'POST /api/admin/auth/login', fn: async () => {
      const r = await req('POST', '/api/admin/auth/login', { email: 'admin@sportstream.app', password: 'admin123' });
      if (r.status === 200 && r.body.accessToken) {
        accessToken = r.body.accessToken;
        const u = r.body.user;
        return `✅ 200 | user=${u.name} role=${u.role}`;
      }
      if (r.status === 401) return `❌ 401 — wrong password or admin not seeded`;
      return `❌ ${r.status} | ${JSON.stringify(r.body).substring(0,100)}`;
    }},
    // ─── AUTH: ME ───
    { name: 'GET /api/admin/auth/me', fn: async () => {
      const r = await req('GET', '/api/admin/auth/me');
      if (r.status === 200 && r.body.user) return `✅ 200 | ${r.body.user.email}`;
      return `❌ ${r.status} | ${JSON.stringify(r.body).substring(0,80)}`;
    }},
    // ─── CONFIG ───
    { name: 'GET /api/config', fn: async () => {
      const r = await req('GET', '/api/config');
      if (r.status === 200) return `✅ 200 | maintenanceMode=${r.body.maintenanceMode}`;
      return `❌ ${r.status}`;
    }},
    // ─── EVENTS ───
    { name: 'GET /api/events', fn: async () => {
      const r = await req('GET', '/api/events');
      if (r.status === 200) return `✅ 200 | ${r.body.events?.length || 0} events`;
      return `❌ ${r.status} | ${JSON.stringify(r.body).substring(0,80)}`;
    }},
    // ─── CATEGORIES ───
    { name: 'GET /api/categories', fn: async () => {
      const r = await req('GET', '/api/categories');
      if (r.status === 200) return `✅ 200 | ${r.body.categories?.length || 0} categories`;
      return `❌ ${r.status} | ${JSON.stringify(r.body).substring(0,80)}`;
    }},
    // ─── CHANNELS ───
    { name: 'GET /api/channels', fn: async () => {
      const r = await req('GET', '/api/channels');
      if (r.status === 200) return `✅ 200 | ${r.body.channels?.length || 0} channels`;
      return `❌ ${r.status} | ${JSON.stringify(r.body).substring(0,80)}`;
    }},
    // ─── HIGHLIGHTS ───
    { name: 'GET /api/highlights', fn: async () => {
      const r = await req('GET', '/api/highlights');
      if (r.status === 200) return `✅ 200 | ${r.body.highlights?.length || 0} highlights`;
      return `❌ ${r.status} | ${JSON.stringify(r.body).substring(0,80)}`;
    }},
    // ─── BANNERS ───
    { name: 'GET /api/banners', fn: async () => {
      const r = await req('GET', '/api/banners');
      if (r.status === 200) return `✅ 200 | ${r.body.banners?.length || 0} banners`;
      return `❌ ${r.status} | ${JSON.stringify(r.body).substring(0,80)}`;
    }},
    // ─── NOTIFICATIONS ───
    { name: 'GET /api/notifications', fn: async () => {
      const r = await req('GET', '/api/notifications');
      if (r.status === 200) return `✅ 200 | ${r.body.notifications?.length || 0} notifications`;
      return `❌ ${r.status} | ${JSON.stringify(r.body).substring(0,80)}`;
    }},
    // ─── ANALYTICS ───
    { name: 'GET /api/analytics/overview', fn: async () => {
      const r = await req('GET', '/api/analytics/overview');
      if (r.status === 200) return `✅ 200 | totalEvents=${r.body.overview?.totalEvents || 0}`;
      return `❌ ${r.status} | ${JSON.stringify(r.body).substring(0,80)}`;
    }},
    // ─── ADMIN USERS ───
    { name: 'GET /api/admin/users', fn: async () => {
      const r = await req('GET', '/api/admin/users');
      if (r.status === 200) return `✅ 200 | ${r.body.users?.length || 0} admins`;
      return `❌ ${r.status} | ${JSON.stringify(r.body).substring(0,80)}`;
    }},
    // ─── DEVICES ───
    { name: 'GET /api/devices', fn: async () => {
      const r = await req('GET', '/api/devices');
      if (r.status === 200) return `✅ 200 | ${r.body.devices?.length || 0} devices`;
      return `❌ ${r.status} | ${JSON.stringify(r.body).substring(0,80)}`;
    }},
    // ─── ROOT ───
    { name: 'GET / (root)', fn: async () => {
      const r = await req('GET', '/');
      if (r.status === 200 && r.body.name) return `✅ 200 | ${r.body.name}`;
      return `❌ ${r.status}`;
    }},
  ];

  for (const test of tests) {
    try {
      const result = await test.fn();
      console.log(`  ${result}`);
      if (result.startsWith('✅')) passed++;
      else failed++;
    } catch (err) {
      console.log(`  ❌ ERROR: ${err.message}`);
      failed++;
    }
  }

  console.log(`\n═══════════════════════════════════════════`);
  console.log(`  ${passed} passed, ${failed} failed (${tests.length} total)`);
  console.log(`═══════════════════════════════════════════`);

  process.exit(failed > 0 ? 1 : 0);
}

main();
