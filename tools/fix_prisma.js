const fs = require('fs');
let f = 'prisma/schema.prisma';
let c = fs.readFileSync(f, 'utf8');

// Find the end of AnalyticsEvent model and insert DeviceToken after it
const marker = 'model AnalyticsEvent {\n';
let idx = c.lastIndexOf(marker);
// Find closing brace of AnalyticsEvent
idx = c.indexOf('\n}', idx) + 2;

const insert = `

// Device tokens for FCM push (Step 8.18)

model DeviceToken {
  id         String   @id @default(cuid())
  token      String   @unique
  platform   String?
  lastSeenAt DateTime @default(now())
  createdAt  DateTime @default(now())
}
`;

c = c.slice(0, idx + 1) + insert + c.slice(idx + 1);
fs.writeFileSync(f, c);
console.log('schema.prisma updated');
