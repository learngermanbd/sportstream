const { Client } = require('pg');

async function test(host, port) {
  console.log(`\nTesting pg://${host}:${port}...`);
  const client = new Client({
    host,
    port,
    user: 'postgres.izpvcoikstplxcqzgsfh',
    password: 'lmtUaw5sn6hQeADR',
    database: 'postgres',
    ssl: { rejectUnauthorized: false },
    connectionTimeoutMillis: 15000,
  });
  try {
    await client.connect();
    console.log(`✅ CONNECTED to ${host}:${port}`);
    const res = await client.query('SELECT current_database(), version()');
    console.log(`   DB: ${res.rows[0].current_database}`);
    console.log(`   Version: ${res.rows[0].version.substring(0, 50)}`);
    await client.end();
    return true;
  } catch (err) {
    console.log(`❌ FAILED: ${err.message}`);
    return false;
  }
}

async function main() {
  // Test pooler transaction mode (port 6543)
  await test('aws-1-ap-south-1.pooler.supabase.com', 6543);
  // Test pooler session mode (port 5432)
  await test('aws-1-ap-south-1.pooler.supabase.com', 5432);
}

main();
