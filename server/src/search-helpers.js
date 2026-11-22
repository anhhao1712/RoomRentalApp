/**
 * Chuẩn hóa chuỗi tiếng Việt không dấu, chữ thường, gọn khoảng trắng để tìm kiếm không phân biệt dấu.
 * @param {unknown} value
 * @returns {string}
 */
function normalizeText(value) {
  return String(value ?? '')
    .replace(/[đĐ]/g, 'd')
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
    .toLowerCase()
    .replace(/\s+/g, ' ')
    .trim();
}

/**
 * Giữ lại các ký tự chữ số để đối chiếu số điện thoại khi tìm kiếm.
 * @param {unknown} value
 * @returns {string}
 */
function digitsOnly(value) {
  return String(value ?? '').replace(/\D/g, '');
}

/**
 * Kiểm tra từ khóa có khớp với danh sách văn bản (không dấu) hoặc số điện thoại (>= 3 chữ số).
 * @param {unknown} query
 * @param {Array<unknown>} [texts]
 * @param {Array<unknown>} [phones]
 * @returns {boolean}
 */
function matchesQuery(query, texts = [], phones = []) {
  const raw = String(query ?? '').trim();
  if (!raw) return true;

  const qNorm = normalizeText(raw);
  if (qNorm && texts.some((item) => item != null && normalizeText(item).includes(qNorm))) {
    return true;
  }

  const qDigits = digitsOnly(raw);
  if (qDigits.length >= 3) {
    const localDigits = qDigits.startsWith('84') && qDigits.length >= 4 ? `0${qDigits.slice(2)}` : null;
    return phones.some((phone) => {
      const pDigits = digitsOnly(phone);
      if (!pDigits) return false;
      return pDigits.includes(qDigits) || (localDigits ? pDigits.includes(localDigits) : false);
    });
  }

  return false;
}

/**
 * Tạo ID hội thoại ổn định giữa hai người dùng khác nhau.
 * @param {number|string} userIdA
 * @param {number|string} userIdB
 * @returns {string}
 */
function conversationIdFor(userIdA, userIdB) {
  const a = Number(userIdA);
  const b = Number(userIdB);
  if (!Number.isInteger(a) || a <= 0 || !Number.isInteger(b) || b <= 0) {
    throw new Error('Mã người dùng không hợp lệ.');
  }
  if (a === b) {
    throw new Error('Không thể tự tạo cuộc trò chuyện với chính mình.');
  }
  return `${Math.min(a, b)}_${Math.max(a, b)}`;
}

/**
 * Kiểm tra chuỗi thời gian xem phòng có giờ nằm trong khung 07:00–21:00 hay không.
 * @param {unknown} value
 * @returns {string|null} Thông báo lỗi hoặc null nếu hợp lệ/trống.
 */
function validateViewingTime(value) {
  const text = String(value ?? '').trim();
  if (!text) return null;
  const match = text.match(/\b(\d{1,2}):(\d{2})\b/);
  if (!match) return null;
  const hour = Number(match[1]);
  const minute = Number(match[2]);
  const validRange =
    Number.isInteger(hour) &&
    Number.isInteger(minute) &&
    minute >= 0 &&
    minute <= 59 &&
    hour >= 7 &&
    (hour < 21 || (hour === 21 && minute === 0));
  return validRange ? null : 'Thời gian xem phòng chỉ hỗ trợ từ 07:00 đến 21:00.';
}

/**
 * Chuyển chuỗi danh sách ID tiện ích (vd: "1, 2,3") hoặc mảng thành mảng số nguyên dương duy nhất.
 * @param {unknown} raw
 * @returns {number[]}
 */
function parseAmenityIds(raw) {
  if (raw == null) return [];
  const parts = Array.isArray(raw) ? raw : String(raw).split(',');
  const ids = parts
    .map((item) => Number(String(item).trim()))
    .filter((n) => Number.isInteger(n) && n > 0);
  return [...new Set(ids)];
}

/**
 * Kiểm tra danh sách tiện ích của phòng có chứa đủ tất cả tiện ích đang lọc hay không.
 * @param {unknown} roomAmenityIds
 * @param {number[]} requiredIds
 * @returns {boolean}
 */
function matchesAmenities(roomAmenityIds, requiredIds) {
  if (!Array.isArray(requiredIds) || requiredIds.length === 0) return true;
  if (!Array.isArray(roomAmenityIds)) return false;
  return requiredIds.every((id) => roomAmenityIds.includes(id));
}

module.exports = {
  normalizeText,
  digitsOnly,
  matchesQuery,
  conversationIdFor,
  validateViewingTime,
  parseAmenityIds,
  matchesAmenities
};
