/**
 * Phase 8 \u00b7 Step 8.13 \u2014 admin login (dev-mode stub).
 *
 * Accepts any non-empty email + password and signs a mocked JWT. The real
 * bcrypt verification + Admin lookup + refresh-token pair lands in Step 8.3.
 */
const express = require('express');
const jwt = require('jsonwebtoken');
const { JWT_SECRET } = require('../middleware/auth');

const router = express.Router();

const JWT_ACCESS_EXP = process.env.JWT_ACCESS_EXP || '15m';

router.post('/login', (req, res) => {
  const { email, password } = req.body || {};
  if (!email || !password) {
    return res.status(400).json({
      error: 'email and password are required'
    });
  }
  if (typeof email !== 'string' || typeof password !== 'string') {
    return res.status(400).json({ error: 'email + password must be strings' });
  }
  // \u26a0\ufe0f DEV-MODE: any (non-empty) credentials succeed. Pivot to bcrypt + DB
  // lookup once Step 8.3 lands.
  const token = jwt.sign(
    { sub: email, role: 'EDITOR' },
    JWT_SECRET,
    { expiresIn: JWT_ACCESS_EXP }
  );
  return res.json({
    token,
    refreshToken: null, // \u2192 lands Step 8.3
    role: 'EDITOR',
    email,
    expiresIn: JWT_ACCESS_EXP
  });
});

router.post('/logout', (_req, res) => {
  // Clients simply discard tokens; server-side blacklist lands Step 8.3.
  res.json({ ok: true });
});

module.exports = router;
