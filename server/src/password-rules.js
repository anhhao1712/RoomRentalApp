// Quy tắc mật khẩu dùng khi ĐĂNG KÝ và ĐẶT LẠI mật khẩu.
// Không áp dụng khi đăng nhập để tài khoản cũ (ví dụ admin / 123) vẫn vào được.
const MIN_LENGTH = 8;
const MAX_LENGTH = 64;

function validatePassword(password) {
  const value = String(password ?? '');
  if (value.length < MIN_LENGTH) return `Mật khẩu cần tối thiểu ${MIN_LENGTH} ký tự.`;
  if (value.length > MAX_LENGTH) return `Mật khẩu tối đa ${MAX_LENGTH} ký tự.`;
  if (/\s/.test(value)) return 'Mật khẩu không được chứa khoảng trắng.';
  if (!/[a-z]/.test(value)) return 'Mật khẩu cần có ít nhất 1 chữ thường.';
  if (!/[A-Z]/.test(value)) return 'Mật khẩu cần có ít nhất 1 chữ hoa.';
  if (!/[0-9]/.test(value)) return 'Mật khẩu cần có ít nhất 1 chữ số.';
  return null;
}

module.exports = { validatePassword, MIN_LENGTH, MAX_LENGTH };
