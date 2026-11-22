// Quy tắc email / số điện thoại khi đăng ký và cập nhật hồ sơ.
function isEmail(value) {
  const text = String(value || '').trim();
  return text.length <= 254 && /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(text);
}

/** Chuẩn hóa số điện thoại Việt Nam về dạng 0xxxxxxxxx (10 số). Trả về null nếu không hợp lệ. */
function normalizePhone(value) {
  let digits = String(value || '').replace(/[\s.\-()]/g, '');
  if (digits.startsWith('+84')) digits = '0' + digits.slice(3);
  else if (digits.startsWith('84') && digits.length === 11) digits = '0' + digits.slice(2);
  return /^0\d{9}$/.test(digits) ? digits : null;
}

module.exports = { isEmail, normalizePhone };
