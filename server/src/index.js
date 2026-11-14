require('dotenv').config();
const express = require('express');
const cors = require('cors');
const bcrypt = require('bcryptjs');
const jwt = require('jsonwebtoken');
const multer = require('multer');
const { db, admin } = require('./firebase');
const { uploadImageBuffer } = require('./cloudinary');
const { AMENITIES, ROOM_STATUSES, AREAS } = require('./constants');
const { roomPayload } = require('./room-payload');
const { createAuth } = require('./auth');

const app = express();
const port = Number(process.env.PORT || 8080);
const jwtSecret = process.env.JWT_SECRET || 'chi-dung-cho-phat-trien-local';
const upload = multer({ storage: multer.memoryStorage(), limits: { fileSize: 5 * 1024 * 1024 } });
app.use(cors());
app.use(express.json({ limit: '1mb' }));

const ok = (res, message = '', data = null, status = 200) => res.status(status).json({ success: true, message, data });
const fail = (res, message, status = 400) => res.status(status).json({ success: false, message, data: null });
const now = () => new Date().toISOString();
const numberId = () => Date.now() + Math.floor(Math.random() * 1000);
const normalizeUsername = (username) => String(username || '').trim().toLowerCase();

function signToken(user) {
  return jwt.sign({ id: user.id, role: user.role, username: user.username }, jwtSecret, { expiresIn: '7d' });
}
const auth = createAuth({ verifyToken: (token) => jwt.verify(token, jwtSecret), getUser, fail });
function requireRole(...roles) {
  return (req, res, next) => roles.includes(req.user.role) ? next() : fail(res, 'Bạn không có quyền thực hiện chức năng này.', 403);
}
function publicUser(data) {
  return { id: data.id, username: data.username, fullName: data.fullName, phone: data.phone || null, email: data.email || null, role: data.role, acceptedLandlordTerms: Boolean(data.acceptedLandlordTerms), avatarUrl: data.avatarUrl || null };
}
async function getUser(id) {
  const snap = await db.collection('users').doc(String(id)).get();
  return snap.exists ? snap.data() : null;
}
async function getUserByUsername(username) {
  const userNameSnap = await db.collection('usernames').doc(normalizeUsername(username)).get();
  if (!userNameSnap.exists) return null;
  return getUser(userNameSnap.data().userId);
}
async function roomDto(room, viewerId = null) {
  const owner = await getUser(room.ownerId);
  const isFavorite = viewerId ? (await db.collection('users').doc(String(viewerId)).collection('favorites').doc(String(room.id)).get()).exists : false;
  return {
    id: room.id, landlordId: room.ownerId, landlordName: owner?.fullName || 'Chủ trọ', landlordPhone: owner?.phone || null,
    title: room.title, description: room.description || null, address: room.address, ward: room.ward || null,
    district: room.district || null, city: room.city || 'Hồ Chí Minh', price: room.price, area: room.area,
    maxTenants: room.maxTenants || 2, deposit: room.deposit || 0, electricityPrice: room.electricityPrice ?? 3500,
    waterPrice: room.waterPrice ?? 15000, internetFee: room.internetFee || 0, status: room.status,
    images: room.images || [], amenities: AMENITIES.filter((item) => (room.amenityIds || []).includes(item.id)),
    isFavorite, createdAt: room.createdAt || null,
    latitude: typeof room.latitude === 'number' ? room.latitude : null,
    longitude: typeof room.longitude === 'number' ? room.longitude : null
  };
}
async function roomById(id) {
  const snap = await db.collection('rooms').doc(String(id)).get();
  return snap.exists && !snap.data().deletedAt ? snap.data() : null;
}

app.get('/health', (_, res) => ok(res, 'API Node.js + Firebase đang hoạt động.', { status: 'ok' }));

app.post('/api/auth/login', async (req, res) => {
  try {
    const user = await getUserByUsername(req.body.username);
    if (!user || !(await bcrypt.compare(String(req.body.password || ''), user.passwordHash))) return fail(res, 'Tài khoản hoặc mật khẩu không đúng.', 401);
    return ok(res, 'Đăng nhập thành công.', { token: signToken(user), user: publicUser(user) });
  } catch (error) { return fail(res, error.message, 500); }
});

app.post('/api/auth/register', async (req, res) => {
  try {
    const username = normalizeUsername(req.body.username);
    if (!/^[a-z0-9_.-]{4,64}$/.test(username) || String(req.body.password || '').length < 3 || !req.body.fullName?.trim()) return fail(res, 'Tên đăng nhập gồm 4–64 ký tự chữ không dấu, số, dấu chấm, gạch dưới hoặc gạch ngang; mật khẩu tối thiểu 3 ký tự.');
    const usernameRef = db.collection('usernames').doc(username);
    const existing = await usernameRef.get();
    if (existing.exists) return fail(res, 'Tên đăng nhập đã tồn tại.', 409);
    const id = numberId();
    const isLandlord = req.body.role === 'LANDLORD';
    if (isLandlord && req.body.acceptedTerms !== true) return fail(res, 'Bạn cần đồng ý điều khoản trước khi đăng ký Chủ trọ.');
    const user = { id, username, passwordHash: await bcrypt.hash(String(req.body.password), 10), fullName: req.body.fullName.trim(), phone: req.body.phone || null, email: req.body.email || null, role: isLandlord ? 'LANDLORD' : 'TENANT', acceptedLandlordTerms: isLandlord, avatarUrl: null, createdAt: now(), updatedAt: now() };
    const created = await db.runTransaction(async (transaction) => {
      if ((await transaction.get(usernameRef)).exists) return false;
      transaction.create(db.collection('users').doc(String(id)), user);
      transaction.create(usernameRef, { userId: id });
      return true;
    });
    if (!created) return fail(res, 'Tên đăng nhập đã tồn tại.', 409);
    return ok(res, 'Tạo tài khoản thành công.', { token: signToken(user), user: publicUser(user) }, 201);
  } catch (error) { return fail(res, error.message, 500); }
});

app.get('/api/amenities', (_, res) => ok(res, '', AMENITIES));
app.get('/api/areas', (_, res) => ok(res, '', AREAS));
app.get('/api/rooms', async (req, res) => {
  try {
    const q = String(req.query.q || '').trim().toLowerCase(); const min = Number(req.query.minPrice || 0); const max = Number(req.query.maxPrice || Number.MAX_SAFE_INTEGER); const district = String(req.query.district || '').toLowerCase();
    const viewerId = (() => { try { const token = req.headers.authorization?.replace(/^Bearer\s+/i, ''); return token ? jwt.verify(token, jwtSecret).id : null; } catch { return null; } })();
    let rooms = (await db.collection('rooms').where('status', '==', 'AVAILABLE').get()).docs.map((doc) => doc.data()).filter((room) => !room.deletedAt);
    rooms = rooms.filter((room) => room.price >= min && room.price <= max && (!district || String(room.district || '').toLowerCase().includes(district)) && (!q || [room.title, room.address, room.district, room.city].filter(Boolean).join(' ').toLowerCase().includes(q)));
    const sort = String(req.query.sort || 'newest'); rooms.sort((a, b) => sort === 'price_asc' ? a.price - b.price : sort === 'price_desc' ? b.price - a.price : sort === 'area' ? b.area - a.area : String(b.createdAt).localeCompare(String(a.createdAt)));
    return ok(res, '', await Promise.all(rooms.map((room) => roomDto(room, viewerId))));
  } catch (error) { return fail(res, error.message, 500); }
});
app.get('/api/rooms/:id', async (req, res) => { try { const room = await roomById(req.params.id); if (!room) return fail(res, 'Không tìm thấy phòng.', 404); let viewerId = null; try { viewerId = jwt.verify(req.headers.authorization?.replace(/^Bearer\s+/i, ''), jwtSecret).id; } catch {} return ok(res, '', await roomDto(room, viewerId)); } catch (error) { return fail(res, error.message, 500); } });

app.use('/api', auth);
app.get('/api/me', async (req, res) => { const user = await getUser(req.user.id); return user ? ok(res, '', publicUser(user)) : fail(res, 'Không tìm thấy tài khoản.', 404); });
app.put('/api/me', async (req, res) => { try { const user = await getUser(req.user.id); if (!user) return fail(res, 'Không tìm thấy tài khoản.', 404); const update = { fullName: req.body.fullName?.trim() || user.fullName, phone: req.body.phone || null, email: req.body.email || null, avatarUrl: req.body.avatarUrl || null, updatedAt: now() }; await db.collection('users').doc(String(req.user.id)).update(update); return ok(res, 'Đã cập nhật hồ sơ.', true); } catch (error) { return fail(res, error.message, 500); } });
app.patch('/api/me/role', async (req, res) => { try { const user = await getUser(req.user.id); if (!user) return fail(res, 'Không tìm thấy tài khoản.', 404); if (user.role === 'ADMIN') return fail(res, 'Tài khoản quản trị không thể đổi vai trò.', 403); const role = req.body.role; if (!['TENANT', 'LANDLORD'].includes(role)) return fail(res, 'Vai trò không hợp lệ.'); if (role === 'LANDLORD' && !String(user.phone || '').trim()) return fail(res, 'Vui lòng cập nhật số điện thoại trước khi chuyển sang Chủ trọ.'); if (role === 'LANDLORD' && !user.acceptedLandlordTerms && req.body.acceptedTerms !== true) return fail(res, 'Bạn cần đồng ý điều khoản để trở thành Chủ trọ.'); await db.collection('users').doc(String(req.user.id)).update({ role, acceptedLandlordTerms: role === 'LANDLORD' ? true : Boolean(user.acceptedLandlordTerms), updatedAt: now() }); return ok(res, role === 'LANDLORD' ? 'Đã chuyển sang vai trò Chủ trọ.' : 'Đã chuyển sang vai trò Người thuê.', true); } catch (error) { return fail(res, error.message, 500); } });

app.get('/api/favorites', async (req, res) => { try { const docs = await db.collection('users').doc(String(req.user.id)).collection('favorites').get(); const rooms = await Promise.all(docs.docs.map(async (doc) => roomById(doc.id))); return ok(res, '', await Promise.all(rooms.filter(Boolean).map((room) => roomDto(room, req.user.id)))); } catch (error) { return fail(res, error.message, 500); } });
app.post('/api/rooms/:id/favorite', async (req, res) => { try { const ref = db.collection('users').doc(String(req.user.id)).collection('favorites').doc(String(req.params.id)); const snap = await ref.get(); if (snap.exists) { await ref.delete(); return ok(res, 'Đã bỏ lưu phòng.', false); } await ref.set({ roomId: Number(req.params.id), createdAt: now() }); return ok(res, 'Đã lưu phòng yêu thích.', true); } catch (error) { return fail(res, error.message, 500); } });

app.post('/api/rooms/:id/requests', requireRole('TENANT', 'ADMIN'), async (req, res) => { try { const room = await roomById(req.params.id); if (!room || room.status !== 'AVAILABLE') return fail(res, 'Phòng không còn nhận yêu cầu.'); const contactPhone = String(req.body.phone || '').trim(); if (!contactPhone) return fail(res, 'Vui lòng nhập số điện thoại liên hệ.'); const exists = await db.collection('rentalRequests').where('roomId', '==', room.id).where('tenantId', '==', req.user.id).where('status', '==', 'PENDING').get(); if (!exists.empty) return fail(res, 'Bạn đã có yêu cầu đang chờ cho phòng này.', 409); const id = numberId(); await db.collection('rentalRequests').doc(String(id)).set({ id, roomId: room.id, ownerId: room.ownerId, tenantId: req.user.id, contactPhone, message: req.body.message || null, viewingTime: req.body.viewingTime || null, status: 'PENDING', ownerNote: null, createdAt: now(), updatedAt: now() }); return ok(res, 'Đã gửi yêu cầu đến chủ trọ.', true, 201); } catch (error) { return fail(res, error.message, 500); } });
app.get('/api/requests', async (req, res) => { try { let data = (await db.collection('rentalRequests').get()).docs.map((doc) => doc.data()); if (req.user.role === 'LANDLORD') data = data.filter((item) => item.ownerId === req.user.id); if (req.user.role === 'TENANT') data = data.filter((item) => item.tenantId === req.user.id); const result = await Promise.all(data.sort((a,b)=>String(b.createdAt).localeCompare(String(a.createdAt))).map(async (item) => { const [room, tenant] = await Promise.all([roomById(item.roomId), getUser(item.tenantId)]); return { id: item.id, roomId: item.roomId, roomTitle: room?.title || 'Phòng đã xóa', tenantId: item.tenantId, tenantName: tenant?.fullName || 'Người dùng', tenantPhone: item.contactPhone || tenant?.phone || null, message: item.message, viewingTime: item.viewingTime, status: item.status, ownerNote: item.ownerNote, createdAt: item.createdAt }; })); return ok(res, '', result); } catch (error) { return fail(res, error.message, 500); } });
app.patch('/api/requests/:id', requireRole('LANDLORD', 'ADMIN'), async (req, res) => { try { const ref = db.collection('rentalRequests').doc(String(req.params.id)); const snap = await ref.get(); if (!snap.exists) return fail(res, 'Không tìm thấy yêu cầu.', 404); const item = snap.data(); if (req.user.role !== 'ADMIN' && item.ownerId !== req.user.id) return fail(res, 'Bạn không có quyền xử lý yêu cầu.', 403); if (!['ACCEPTED', 'REJECTED', 'CANCELLED'].includes(req.body.status)) return fail(res, 'Trạng thái không hợp lệ.'); await ref.update({ status: req.body.status, ownerNote: req.body.ownerNote || null, updatedAt: now() }); return ok(res, 'Đã cập nhật yêu cầu.', true); } catch (error) { return fail(res, error.message, 500); } });

app.post('/api/uploads/image', requireRole('LANDLORD', 'ADMIN'), upload.single('image'), async (req, res) => {
  try {
    if (!req.file) return fail(res, 'Vui lòng chọn một ảnh để tải lên.');
    if (!req.file.mimetype?.startsWith('image/')) return fail(res, 'File phải là ảnh (jpg, png, ...).');
    const url = await uploadImageBuffer(req.file.buffer, req.file.mimetype, `rooms/${req.user.id}`);
    return ok(res, 'Đã tải ảnh lên.', { url }, 201);
  } catch (error) { return fail(res, error.message, 500); }
});
app.get('/api/owner/rooms', requireRole('LANDLORD', 'ADMIN'), async (req, res) => { try { let rooms = (await db.collection('rooms').get()).docs.map((doc) => doc.data()).filter((room) => !room.deletedAt); if (req.user.role !== 'ADMIN') rooms = rooms.filter((room) => room.ownerId === req.user.id); return ok(res, '', await Promise.all(rooms.map((room) => roomDto(room, req.user.id)))); } catch (error) { return fail(res, error.message, 500); } });
app.post('/api/rooms', requireRole('LANDLORD', 'ADMIN'), async (req, res) => { try { const id = numberId(); const room = { id, ...roomPayload(req.body, req.user.id), createdAt: now(), deletedAt: null }; await db.collection('rooms').doc(String(id)).set(room); return ok(res, 'Đăng phòng thành công.', { id }, 201); } catch (error) { return fail(res, error.message); } });
app.put('/api/rooms/:id', requireRole('LANDLORD', 'ADMIN'), async (req, res) => { try { const room = await roomById(req.params.id); if (!room) return fail(res, 'Không tìm thấy phòng.', 404); if (req.user.role !== 'ADMIN' && room.ownerId !== req.user.id) return fail(res, 'Không có quyền sửa phòng này.', 403); await db.collection('rooms').doc(String(room.id)).update(roomPayload(req.body, room.ownerId)); return ok(res, 'Đã cập nhật phòng.', true); } catch (error) { return fail(res, error.message); } });
app.patch('/api/rooms/:id/status', requireRole('LANDLORD', 'ADMIN'), async (req, res) => { try { const room = await roomById(req.params.id); if (!room) return fail(res, 'Không tìm thấy phòng.', 404); if (req.user.role !== 'ADMIN' && room.ownerId !== req.user.id) return fail(res, 'Không có quyền thay đổi trạng thái.', 403); if (!ROOM_STATUSES.includes(req.body.status)) return fail(res, 'Trạng thái không hợp lệ.'); await db.collection('rooms').doc(String(room.id)).update({ status: req.body.status, updatedAt: now() }); return ok(res, 'Đã cập nhật trạng thái.', true); } catch (error) { return fail(res, error.message); } });
app.delete('/api/rooms/:id', requireRole('LANDLORD', 'ADMIN'), async (req, res) => { try { const room = await roomById(req.params.id); if (!room) return fail(res, 'Không tìm thấy phòng.', 404); if (req.user.role !== 'ADMIN' && room.ownerId !== req.user.id) return fail(res, 'Không có quyền xóa phòng này.', 403); await db.collection('rooms').doc(String(room.id)).update({ status: 'HIDDEN', deletedAt: now(), updatedAt: now() }); return ok(res, 'Đã ẩn tin đăng.', true); } catch (error) { return fail(res, error.message); } });
app.get('/api/dashboard', async (req, res) => { try { let rooms = (await db.collection('rooms').get()).docs.map((doc) => doc.data()).filter((room) => !room.deletedAt); if (req.user.role !== 'ADMIN') rooms = rooms.filter((room) => room.ownerId === req.user.id); let requests = (await db.collection('rentalRequests').get()).docs.map((doc) => doc.data()); if (req.user.role !== 'ADMIN') requests = requests.filter((item) => item.ownerId === req.user.id); const favoriteCount = (await Promise.all(rooms.map(async (room) => (await db.collection('users').doc(String(room.ownerId)).collection('favorites').where('roomId', '==', room.id).get()).size))).reduce((sum, count) => sum + count, 0); return ok(res, '', { totalRooms: rooms.length, availableRooms: rooms.filter((room) => room.status === 'AVAILABLE').length, pendingRequests: requests.filter((item) => item.status === 'PENDING').length, totalFavorites: favoriteCount }); } catch (error) { return fail(res, error.message, 500); } });

app.use((error, _, res, __) => {
  if (error instanceof multer.MulterError) return fail(res, error.code === 'LIMIT_FILE_SIZE' ? 'Ảnh quá lớn, tối đa 5MB.' : 'Không tải được ảnh lên.', 400);
  console.error(error); return fail(res, 'Có lỗi máy chủ xảy ra.', 500);
});
app.listen(port, '0.0.0.0', () => console.log(`Thuê Trọ Node.js API đang chạy tại http://0.0.0.0:${port}`));
