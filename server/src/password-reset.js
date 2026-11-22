// Logic thuần (không đụng database) cho tính năng quên mật khẩu bằng mã OTP 6 số.
const crypto = require('crypto');
const { isEmail } = require('./contact-rules');

const CODE_TTL_MS = 10 * 60 * 1000;   // mã có hiệu lực 10 phút
const RESEND_AFTER_MS = 60 * 1000;    // gửi lại sau tối thiểu 60 giây
const MAX_ATTEMPTS = 5;               // nhập sai tối đa 5 lần

function generateCode() {
  return String(crypto.randomInt(0, 1000000)).padStart(6, '0');
}

function createRecord(codeHash, nowMs) {
  return { codeHash, sentAt: nowMs, expiresAt: nowMs + CODE_TTL_MS, attempts: 0 };
}

/** Số giây còn phải chờ trước khi được gửi lại mã (0 nếu được phép gửi). */
function secondsUntilResend(record, nowMs) {
  if (!record || typeof record.sentAt !== 'number') return 0;
  return Math.max(0, Math.ceil((record.sentAt + RESEND_AFTER_MS - nowMs) / 1000));
}

/** Kiểm tra bản ghi còn dùng được không: { ok:true, attemptsLeft } hoặc { ok:false, reason }. */
function checkRecord(record, nowMs) {
  if (!record || !record.codeHash) return { ok: false, reason: 'MISSING' };
  if (nowMs > record.expiresAt) return { ok: false, reason: 'EXPIRED' };
  const attempts = Number(record.attempts) || 0;
  if (attempts >= MAX_ATTEMPTS) return { ok: false, reason: 'LOCKED' };
  return { ok: true, attemptsLeft: MAX_ATTEMPTS - attempts };
}

/** duy@gmail.com -> d**@gmail.com */
function maskEmail(email) {
  const [name, domain] = String(email).split('@');
  if (!domain) return '***';
  return name.slice(0, 1) + '*'.repeat(Math.max(2, name.length - 1)) + '@' + domain;
}

module.exports = { CODE_TTL_MS, RESEND_AFTER_MS, MAX_ATTEMPTS, generateCode, createRecord, secondsUntilResend, checkRecord, isEmail, maskEmail };
