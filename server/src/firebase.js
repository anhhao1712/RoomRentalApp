const admin = require('firebase-admin');
const fs = require('fs');
const path = require('path');

function initializeFirebase() {
  if (admin.apps.length) return admin.firestore();
  const configuredPath = process.env.FIREBASE_SERVICE_ACCOUNT_PATH || './secrets/firebase-service-account.json';
  const serviceAccountPath = path.resolve(process.cwd(), configuredPath);
  if (!fs.existsSync(serviceAccountPath)) {
    throw new Error(`Không tìm thấy Firebase Service Account: ${serviceAccountPath}. Xem docs/FIREBASE_NODEJS.md.`);
  }
  const serviceAccount = JSON.parse(fs.readFileSync(serviceAccountPath, 'utf8'));
  admin.initializeApp({ credential: admin.credential.cert(serviceAccount) });
  return admin.firestore();
}

module.exports = { admin, db: initializeFirebase() };
