const net = require('net');

function testTCP(label, host, port) {
  return new Promise((resolve) => {
    const socket = new net.Socket();
    socket.setTimeout(10000);
    socket.on('connect', () => {
      console.log(`${label} (${host}:${port}) => CONNECTED ✅`);
      socket.destroy();
      resolve(true);
    });
    socket.on('timeout', () => {
      console.log(`${label} (${host}:${port}) => TIMEOUT ❌ (port blocked/firewall)`);
      socket.destroy();
      resolve(false);
    });
    socket.on('error', (err) => {
      console.log(`${label} (${host}:${port}) => ERROR: ${err.code} - ${err.message}`);
      resolve(false);
    });
    socket.connect(port, host);
  });
}

async function main() {
  console.log("Testing Supabase connectivity...\n");

  const directIPs = [
    { host: 'db.izpvcoikstplxcqzgsfh.supabase.co', port: 5432 },
    // Try forcing IPv4
    { host: 'db.izpvcoikstplxcqzgsfh.supabase.co', port: 5432 },
  ];

  const poolerIPs = [
    { host: 'aws-1-ap-south-1.pooler.supabase.com', port: 6543 },
    { host: '13.200.110.68', port: 6543 },
    { host: '3.111.225.200', port: 6543 },
  ];

  // Test pooler first (IPv4)
  for (const { host, port } of poolerIPs) {
    await testTCP('Pooler', host, port);
  }

  // Test direct with IPv4 force via family:4
  console.log("\n--- Testing direct with IPv4 force ---");
  const net2 = require('net');
  const socket = new net2.Socket();
  socket.setTimeout(10000);
  await new Promise((resolve) => {
    socket.on('connect', () => {
      console.log(`Direct IPv4 (db...supabase.co:5432) => CONNECTED ✅`);
      socket.destroy();
      resolve(true);
    });
    socket.on('timeout', () => {
      console.log(`Direct IPv4 (db...supabase.co:5432) => TIMEOUT ❌`);
      socket.destroy();
      resolve(false);
    });
    socket.on('error', (err) => {
      console.log(`Direct IPv4 (db...supabase.co:5432) => ERROR: ${err.code} - ${err.message}`);
      resolve(false);
    });
    socket.connect({ host: 'db.izpvcoikstplxcqzgsfh.supabase.co', port: 5432, family: 4 });
  });
}

main();
