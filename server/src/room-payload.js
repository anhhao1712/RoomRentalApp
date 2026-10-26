const { AMENITIES, ROOM_STATUSES, AREAS } = require('./constants');

function numeric(body, field, fallback, positive = false, integer = false) {
  const raw = body[field];
  if (raw == null || (typeof raw === 'string' && !raw.trim())) {
    if (fallback !== undefined) return fallback;
    throw new Error('Vui lòng nhập ' + field + '.');
  }
  const value = typeof raw === 'number' || typeof raw === 'string' ? Number(raw) : NaN;
  if (!Number.isFinite(value) || (positive ? value <= 0 : value < 0) || (integer && !Number.isSafeInteger(value))) {
    throw new Error('Giá trị ' + field + ' không hợp lệ.');
  }
  return value;
}

function parseCoordinates(body) {
  const { latitude, longitude } = body;
  // Explicit null clears a previously selected location during an update.
  if (latitude === null && longitude === null) return { latitude: null, longitude: null };
  if (latitude === undefined && longitude === undefined) return {};
  if (typeof latitude !== 'number' || typeof longitude !== 'number' || !Number.isFinite(latitude) || !Number.isFinite(longitude)) {
    throw new Error('Tọa độ phải gồm cả vĩ độ và kinh độ dạng số.');
  }
  if (Math.abs(latitude) > 90 || Math.abs(longitude) > 180) throw new Error('Tọa độ không hợp lệ.');
  return { latitude, longitude };
}

function roomPayload(body, ownerId) {
  if (!body || typeof body.title !== 'string' || !body.title.trim() || typeof body.address !== 'string' || !body.address.trim()) {
    throw new Error('Nhập đủ tiêu đề và địa chỉ.');
  }
  const selectedArea = AREAS.find((item) => item.district === body.district);
  if (!selectedArea) throw new Error('Vui lòng chọn khu vực hợp lệ trong danh sách.');
  return {
    ownerId, title: body.title.trim(), description: body.description || null, address: body.address.trim(), ward: body.ward || null,
    district: selectedArea.district, city: selectedArea.city,
    price: numeric(body, 'price', undefined, true), area: numeric(body, 'area', undefined, true),
    maxTenants: numeric(body, 'maxTenants', 2, true, true), deposit: numeric(body, 'deposit', 0),
    electricityPrice: numeric(body, 'electricityPrice', 3500), waterPrice: numeric(body, 'waterPrice', 15000),
    internetFee: numeric(body, 'internetFee', 0),
    status: ROOM_STATUSES.includes(body.status) ? body.status : 'AVAILABLE',
    images: Array.isArray(body.imageUrls) ? body.imageUrls.filter((url) => typeof url === 'string' && /^https?:\/\//i.test(url)).slice(0, 8) : [],
    amenityIds: Array.isArray(body.amenityIds) ? [...new Set(body.amenityIds.map(Number).filter((id) => AMENITIES.some((item) => item.id === id)))] : [],
    ...parseCoordinates(body), updatedAt: new Date().toISOString()
  };
}
module.exports = { roomPayload };
