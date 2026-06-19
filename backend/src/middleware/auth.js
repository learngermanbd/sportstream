/**
 * Phase 8 \u00b7 Step 8.2-8.16 \u2014 JWT verify middleware (stub).
 *
 * Real RBAC + refresh-token rotation lands in Step 8.3.
 * Biometric / EncryptedSharedPreferences in the Android admin app
 * lands in Step 8.16.
 */
const jwt = require('jsonwebtoken');

const JWT_SECRET = process.env.JWT_SECRET || 'dev-not-secret-change-me';

/** Best-effort JWT decode; never rejects (just leaves req.user null). */
function authOptional(req, _res, next) {
  const header = req.header('authorization') || '';
  const token = header.startsWith('Bearer ') ? header.slice(7) : null;
  if (token) {
    try {
      req.user = jwt.verify(token, JWT_SECRET);
    } catch (_e) {
      req.user = null;
    }
  } else {
    req.user = null;
  }
  next();
}

/** Reject with 401 if no verified user. */
function authRequired(req, res, next) {
  if (!req.user) {
    return res.status(401).json({ error: 'authentication required' });
  }
  next();
}

module.exports = { authOptional, authRequired, JWT_SECRET };
