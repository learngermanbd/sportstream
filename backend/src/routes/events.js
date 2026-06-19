/**
 * Phase 8 \u00b7 Step 8.10 \u2014 events endpoint (stub).
 *
 * Returns an empty list until Step 8.6 ships the full admin CRUD. The user
 * Android app reads the SAME shape via the AdminAPI URL, so the contract is
 * stable once data starts flowing.
 */
const express = require('express');

const router = express.Router();

router.get('/', (_req, res) => {
  res.json({ events: [] });
});

module.exports = router;
