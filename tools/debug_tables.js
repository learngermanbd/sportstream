const { createClient } = require('@supabase/supabase-js');
const sb = createClient('https://izpvcoikstplxcqzgsfh.supabase.co', 'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Iml6cHZjb2lrc3RwbHhjcXpnc2ZoIiwicm9zZSI6InNlcnZpY2Vfcm9sZSIsImlhdCI6MTc4MTk3NTI2OSwiZXhwIjoyMDk3NTUxMjY5fQ.jznjSCKOYj9X0DCNa76c7D9kwgt88IMPOws_eY2ooMc', { auth: { autoRefreshToken: false, persistSession: false } });

async function main() {
  // Try listing all schemas/tables via a raw request
  const tables = ['Admin','admin','Event','event','Category','category'];
  for (const t of tables) {
    const result = await sb.from(t).select('*', { count: 'exact', head: true });
    if (result.error) {
      console.log(t + ': ERROR code=' + result.error.code + ' msg=' + result.error.message + ' details=' + JSON.stringify(result.error.details));
    } else {
      console.log(t + ': EXISTS (count=' + result.count + ')');
    }
  }
  
  // Also try: list ALL tables the API can see
  console.log('\n--- Trying raw API call ---');
  const resp = await fetch('https://izpvcoikstplxcqzgsfh.supabase.co/rest/v1/', {
    headers: {
      'apikey': 'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Iml6cHZjb2lrc3RwbHhjcXpnc2ZoIiwicm9zZSI6InNlcnZpY2Vfcm9sZSIsImlhdCI6MTc4MTk3NTI2OSwiZXhwIjoyMDk3NTUxMjY5fQ.jznjSCKOYj9X0DCNa76c7D9kwgt88IMPOws_eY2ooMc',
      'Authorization': 'Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Iml6cHZjb2lrc3RwbHhjcXpnc2ZoIiwicm9zZSI6InNlcnZpY2Vfcm9sZSIsImlhdCI6MTc4MTk3NTI2OSwiZXhwIjoyMDk3NTUxMjY5fQ.jznjSCKOYj9X0DCNa76c7D9kwgt88IMPOws_eY2ooMc'
    }
  });
  const text = await resp.text();
  console.log('Root API response:', text.substring(0, 500));
}
main();
