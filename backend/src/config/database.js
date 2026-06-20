/**
 * Phase 8 · Step 8.3 — Prisma client singleton.
 *
 * Creates one PrismaClient instance and reuses it across the app.
 * Gracefully handles the case where DATABASE_URL is not set (logs a
 * warning and returns null — useful for dev when the DB isn't wired yet).
 */
const { PrismaClient } = require('@prisma/client');

let prisma = null;

function getPrisma() {
  if (prisma) return prisma;

  if (!process.env.DATABASE_URL) {
    console.warn('[prisma] ⚠️  DATABASE_URL not set — database operations will fail.');
    console.warn('[prisma]     Set DATABASE_URL in .env and run: npx prisma migrate dev');
    prisma = new PrismaClient();
    return prisma;
  }

  prisma = new PrismaClient({
    log: process.env.NODE_ENV === 'development'
      ? ['query', 'info', 'warn', 'error']
      : ['error']
  });

  return prisma;
}

module.exports = { getPrisma };
