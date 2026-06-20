const { Client } = require('pg');

async function main() {
  console.log('Connecting to Supabase via pooler...\n');
  
  const client = new Client({
    host: 'aws-1-ap-south-1.pooler.supabase.com',
    port: 6543,
    user: 'postgres.izpvcoikstplxcqzgsfh',
    password: 'lmtUaw5sn6hQeADR',
    database: 'postgres',
    ssl: { rejectUnauthorized: false },
    connectionTimeoutMillis: 15000,
  });

  try {
    await client.connect();
    console.log('✅ Connected!\n');

    // Check what tables exist
    const tables = await client.query(`
      SELECT table_name FROM information_schema.tables 
      WHERE table_schema = 'public' AND table_type = 'BASE TABLE'
      ORDER BY table_name
    `);
    
    console.log(`Tables found: ${tables.rows.length}`);
    for (const row of tables.rows) {
      console.log('  - ' + row.table_name);
    }

    if (tables.rows.length === 0) {
      console.log('\n⚠️  No tables found — SQL migration was NOT run yet.');
    } else {
      // Check if Admin table has the seed user
      const adminCheck = await client.query('SELECT email FROM "Admin" LIMIT 1');
      if (adminCheck.rows.length > 0) {
        console.log('\n✅ Admin seed exists: ' + adminCheck.rows[0].email);
      }
    }

    await client.end();
  } catch (err) {
    console.log('❌ Connection failed:', err.message);
    process.exit(1);
  }
}

main();
