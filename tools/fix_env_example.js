const fs = require('fs');
let f = '.env.example';
let c = fs.readFileSync(f, 'utf8');

// Replace FCM section
const oldSection = /# --- FCM.*\n# Path to firebase-service-account.*\nFCM_SERVICE_ACCOUNT_JSON=".*"\nFCM_DEFAULT_TOPIC=".*"/s;
const newSection = `# --- FCM (Step 8.18 -- firebase-admin push notifications) ---
# Option A: Set these three vars (recommended for local dev)
FIREBASE_PROJECT_ID="streamify-app"
FIREBASE_CLIENT_EMAIL="firebase-adminsdk-xxxxx@streamify-app.iam.gserviceaccount.com"
FIREBASE_PRIVATE_KEY="-----BEGIN PRIVATE KEY-----\\nYOUR_KEY_HERE\\n-----END PRIVATE KEY-----\\n"
# Option B: Or point to a service-account JSON file
# GOOGLE_APPLICATION_CREDENTIALS="/path/to/service-account-key.json"
FCM_SERVICE_ACCOUNT_JSON=""
FCM_DEFAULT_TOPIC="all_users"`;

c = c.replace(oldSection, newSection);
fs.writeFileSync(f, c);
console.log('.env.example updated');
