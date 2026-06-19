/**
 * Phase 8 \u00b7 Step 8.2-8.3 \u2014 SportStream admin backend entry point.
 *
 * Minimal Express server that boots clean and serves the 3 endpoints the
 * admin app needs in Step 8.2: /api/health, /api/admin/auth/login,
 * /api/events. Full CRUD + RBAC lands in Step 8.3+.
 *
 * Middleware order matters:
 *   helmet \u2192 cors \u2192 json \u2192 morgan \u2192 routes \u2192 404 \u2192 error handler
 */
const express = require('express');
const helmet = require('helmet');
const cors = require('cors');
const morgan = require('morgan');
require('dotenv').config();

const { authOptional, JWT_SECRET } = require('./middleware/auth');
const healthRoute = require('./routes/health');
const authRoute = require('./routes/auth');
const eventsRoute = require('./routes/events');

// \u2500\u2500\u2500 Production guard \u2500\u2500\u2500
// Refuse to boot in production if JWT_SECRET is the dev default. This prevents
// an accidental `npm start` against a misconfigured .env from forging admin
// JWTs (anyone could mint them).
if (process.env.NODE_ENV === 'production' && JWT_SECRET === 'dev-not-secret-change-me') {
  throw new Error('JWT_SECRET must be set in production');
}

const app = express();
const PORT = parseInt(process.env.PORT || '3000', 10);

// \u2500\u2500\u2500 CORS origin \u2500\u2500\u2500
// When at least one origin is configured we use an explicit allow-list (CI-safe).
// Otherwise:
//   \u2022 dev  \u2192 allow all (so the admin web hot-reload works)
//   \u2022 prod \u2192 deny (no CORS = no remote JS can call us). FAIL loud.
function resolveCorsOrigin() {
  const configured = [
    process.env.ADMIN_WEB_ORIGIN,
    process.env.ADMIN_ANDROID_ORIGIN
  ].filter(Boolean);
  if (configured.length > 0) return configured;
  if (process.env.NODE_ENV === 'development') return true; // allow * in dev only
  return false; // deny in production
}

// \u2500\u2500 Global middleware \u2500\u2500
app.use(helmet());
app.use(cors({
  origin: resolveCorsOrigin(),
  credentials: true
}));
app.use(express.json({ limit: '5mb' }));
app.use(morgan(process.env.NODE_ENV === 'production' ? 'combined' : 'dev'));
app.use(authOptional);

// \u2500\u2500 Routes \u2500\u2500
app.use('/api/health', healthRoute);
app.use('/api/admin/auth', authRoute);
app.use('/api/events', eventsRoute);

// \u2500\u2500 Root sanity check \u2500\u2500
app.get('/', (_req, res) => {
  res.json({
    name: 'sportstream-admin-backend',
    docs: 'see /api/health for liveness',
    phase: 'Phase 8.2 skeleton'
  });
});

// \u2500\u2500 404 \u2500\u2500
app.use((req, res) => {
  res.status(404).json({ error: 'Not found', path: req.path, method: req.method });
});

// \u2500\u2500 Error handler \u2500\u2500
// eslint-disable-next-line no-unused-vars
app.use((err, req, res, _next) => {
  console.error('[error]', err);
  res.status(err.status || 500).json({
    error: err.message || 'Internal server error',
    code: err.code
  });
});

if (require.main === module) {
  if (process.env.NODE_ENV !== 'production') {
    console.warn('[sportstream-backend] \u26a0\ufe0f  DEV-MODE auth: any email/password on POST /api/admin/auth/login returns a mocked JWT with role=EDITOR. Replace with bcrypt + Admin lookup in Step 8.3.');
  }
  app.listen(PORT, () => {
    console.log(`[sportstream-backend] listening on :${PORT}`);
    console.log(`[sportstream-backend] try:  curl http://localhost:${PORT}/api/health`);
  });
}

module.exports = app;
