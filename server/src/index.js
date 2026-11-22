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
const { validatePassword } = require('./password-rules');
const reset = require('./password-reset');
const { isEmail, normalizePhone } = require('./contact-rules');
const { createMailer } = require('./mailer');
const {
  normalizeText,
  matchesQuery,
  conversationIdFor,
  validateViewingTime,
  parseAmenityIds,
  matchesAmenities
} = require('./search-helpers');

const app = express();
const port = Number(process.env.PORT || 8080);
const jwtSecret = process.env.JWT_SECRET || 'chi-dung-cho-phat-trien-local';
const mailer = createMailer();
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
function publicProfileUser(data) {
  if (!data) return null;
  return {
    id: data.id,
    username: data.username,
    fullName: data.fullName,
    phone: data.phone || null,
    role: data.role,
    avatarUrl: data.avatarUrl || null,
    createdAt: data.createdAt || null
  };
}
function optionalViewer(req) {
  try {
    const token = req.headers.authorization?.replace(/^Bearer\s+/i, '');
    if (!token) return null;
    const claims = jwt.verify(token, jwtSecret);
    return Number.isSafeInteger(claims?.id) ? claims : null;
  } catch {
    return null;
  }
}
async function getUser(id) {
  const snap = await db.collection('users').doc(String(id)).get();
  return snap.exists ? snap.data() : null;
}
async function loadUsersMap(ids) {
  const uniqueIds = [...new Set(ids.filter((id) => id != null).map((id) => String(id)))];
  const map = new Map();
  await Promise.all(uniqueIds.map(async (id) => {
    const user = await getUser(id);
    if (user) map.set(id, user);
  }));
  return map;
}
async function getUserByUsername(username) {
  const userNameSnap = await db.collection('usernames').doc(normalizeUsername(username)).get();
  if (!userNameSnap.exists) return null;
  return getUser(userNameSnap.data().userId);
}
async function createNotification({ userId, type, title, body, roomId = null, requestId = null, actor = null }) {
  try {
    const id = numberId();
    await db.collection('notifications').doc(String(id)).set({
      id,
      userId: Number(userId),
      type,
      title,
      body,
      roomId,
      requestId,
      actorId: actor?.id || null,
      actorName: actor?.fullName || null,
      actorAvatarUrl: actor?.avatarUrl || null,
      isRead: false,
      createdAt: now()
    });
  } catch (err) {
    console.error('Tạo thông báo thất bại:', err.message);
  }
}
async function roomDto(room, viewerId = null, ownerCache = null) {
  const ownerKey = String(room.ownerId);
  const owner = ownerCache?.has(ownerKey) ? ownerCache.get(ownerKey) : await getUser(room.ownerId);
  if (ownerCache && owner && !ownerCache.has(ownerKey)) ownerCache.set(ownerKey, owner);
  const isFavorite = viewerId ? (await db.collection('users').doc(String(viewerId)).collection('favorites').doc(String(room.id)).get()).exists : false;
  return {
    id: room.id,
    landlordId: room.ownerId,
    landlordName: owner?.fullName || 'Chủ trọ',
    landlordPhone: owner?.phone || null,
    landlordAvatarUrl: owner?.avatarUrl || null,
    landlordUsername: owner?.username || null,
    landlordRole: owner?.role || 'LANDLORD',
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
async function conversationDto(conv, viewerId, usersMap = null) {
  const viewerKey = String(viewerId);
  const otherId = (conv.participants || []).find((id) => Number(id) !== Number(viewerId));
  const other = otherId != null
    ? (usersMap?.get(String(otherId)) || await getUser(otherId))
    : null;
  const pinnedAt = conv.pinnedBy?.[viewerKey] || null;
  const clearedAt = conv.clearedAt?.[viewerKey] || null;
  const lastMsgAt = conv.lastMessageAt || conv.updatedAt || conv.createdAt || null;
  const isClearedLast = Boolean(clearedAt && lastMsgAt && String(lastMsgAt) <= String(clearedAt));
  return {
    id: conv.id,
    otherUser: publicProfileUser(other) || {
      id: Number(otherId || 0),
      username: 'nguoidung',
      fullName: 'Người dùng',
      phone: null,
      role: 'TENANT',
      avatarUrl: null,
      createdAt: null
    },
    lastMessage: isClearedLast ? '' : (conv.lastMessage || ''),
    lastMessageAt: lastMsgAt,
    lastSenderId: isClearedLast ? null : (conv.lastSenderId || null),
    lastRoomId: isClearedLast ? null : (conv.lastRoomId || null),
    lastRoomTitle: isClearedLast ? null : (conv.lastRoomTitle || null),
    unreadCount: Number(conv.unreadCounts?.[viewerKey] || 0),
    isPinned: Boolean(pinnedAt),
    pinnedAt,
    blockedByMe: Boolean(conv.blockedBy?.[viewerKey]),
    blockedByOther: otherId != null ? Boolean(conv.blockedBy?.[String(otherId)]) : false
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
    if (!/^[a-z0-9_.-]{4,64}$/.test(username) || !req.body.fullName?.trim()) return fail(res, 'Tên đăng nhập gồm 4–64 ký tự chữ không dấu, số, dấu chấm, gạch dưới hoặc gạch ngang và cần nhập họ tên.');
    const passwordError = validatePassword(req.body.password);
    if (passwordError) return fail(res, passwordError);
    const email = String(req.body.email || '').trim().toLowerCase();
    if (!isEmail(email)) return fail(res, 'Vui lòng nhập email hợp lệ (dùng để lấy lại mật khẩu).');
    const phone = normalizePhone(req.body.phone);
    if (!phone) return fail(res, 'Vui lòng nhập số điện thoại hợp lệ (10 số, bắt đầu bằng 0).');
    const usernameRef = db.collection('usernames').doc(username);
    const existing = await usernameRef.get();
    if (existing.exists) return fail(res, 'Tên đăng nhập đã tồn tại.', 409);
    const id = numberId();
    const isLandlord = req.body.role === 'LANDLORD';
    if (isLandlord && req.body.acceptedTerms !== true) return fail(res, 'Bạn cần đồng ý điều khoản trước khi đăng ký Chủ trọ.');
    const user = { id, username, passwordHash: await bcrypt.hash(String(req.body.password), 10), fullName: req.body.fullName.trim(), phone, email, role: isLandlord ? 'LANDLORD' : 'TENANT', acceptedLandlordTerms: isLandlord, avatarUrl: null, createdAt: now(), updatedAt: now() };
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

// ---- Quên mật khẩu: gửi mã OTP 6 số qua email đã lưu trong Hồ sơ, rồi đặt lại mật khẩu ----
app.post('/api/auth/forgot-password', async (req, res) => {
  try {
    const user = await getUserByUsername(req.body.username);
    if (!user) return fail(res, 'Không tìm thấy tài khoản với tên đăng nhập này.', 404);
    const email = String(user.email || '').trim();
    if (!reset.isEmail(email)) return fail(res, 'Tài khoản này chưa có email. Hãy đăng nhập và cập nhật email trong tab Tài khoản trước.');
    const ref = db.collection('passwordResets').doc(String(user.id));
    const snap = await ref.get();
    const wait = reset.secondsUntilResend(snap.exists ? snap.data() : null, Date.now());
    if (wait > 0) return fail(res, `Vui lòng chờ ${wait} giây rồi gửi lại mã.`, 429);
    const code = reset.generateCode();
    await ref.set(reset.createRecord(await bcrypt.hash(code, 8), Date.now()));
    try {
      await mailer.send({
        to: email,
        subject: 'Mã đặt lại mật khẩu Thuê Trọ',
        text: `Xin chào ${user.fullName},\n\nMã xác nhận đặt lại mật khẩu của bạn là: ${code}\nMã có hiệu lực trong ${reset.CODE_TTL_MS / 60000} phút. Nếu bạn không yêu cầu, hãy bỏ qua email này.\n\nThuê Trọ`
      });
    } catch (mailError) {
      console.error('Gửi email thất bại:', mailError.message);
      await ref.delete();
      return fail(res, 'Không gửi được email lúc này. Vui lòng thử lại sau.', 502);
    }
    const note = mailer.mode === 'console' ? ' (Chế độ demo: xem mã trong cửa sổ chạy server.)' : '';
    return ok(res, `Đã gửi mã xác nhận đến ${reset.maskEmail(email)}.${note}`, { email: reset.maskEmail(email), expiresInSeconds: reset.CODE_TTL_MS / 1000 });
  } catch (error) { return fail(res, error.message, 500); }
});

app.post('/api/auth/reset-password', async (req, res) => {
  try {
    const user = await getUserByUsername(req.body.username);
    const code = String(req.body.code || '').trim();
    if (!user || !/^\d{6}$/.test(code)) return fail(res, 'Mã xác nhận không hợp lệ.');
    const passwordError = validatePassword(req.body.newPassword);
    if (passwordError) return fail(res, passwordError);
    const ref = db.collection('passwordResets').doc(String(user.id));
    const snap = await ref.get();
    const record = snap.exists ? snap.data() : null;
    const state = reset.checkRecord(record, Date.now());
    if (!state.ok) {
      if (state.reason === 'EXPIRED') await ref.delete();
      const messages = { MISSING: 'Bạn chưa yêu cầu mã hoặc mã đã được dùng. Hãy gửi lại mã mới.', EXPIRED: 'Mã đã hết hạn. Hãy gửi lại mã mới.', LOCKED: 'Bạn đã nhập sai quá nhiều lần. Hãy gửi lại mã mới.' };
      return fail(res, messages[state.reason]);
    }
    if (!(await bcrypt.compare(code, record.codeHash))) {
      await ref.update({ attempts: admin.firestore.FieldValue.increment(1) });
      return fail(res, state.attemptsLeft > 1 ? `Mã xác nhận không đúng. Bạn còn ${state.attemptsLeft - 1} lần thử.` : 'Mã xác nhận không đúng. Hãy gửi lại mã mới.');
    }
    await db.collection('users').doc(String(user.id)).update({ passwordHash: await bcrypt.hash(String(req.body.newPassword), 10), updatedAt: now() });
    await ref.delete();
    return ok(res, 'Đặt lại mật khẩu thành công. Hãy đăng nhập bằng mật khẩu mới.', true);
  } catch (error) { return fail(res, error.message, 500); }
});

app.get('/api/amenities', (_, res) => ok(res, '', AMENITIES));
app.get('/api/areas', (_, res) => ok(res, '', AREAS));
app.get('/api/rooms', async (req, res) => {
  try {
    const q = String(req.query.q || '').trim();
    const rawMin = req.query.minPrice != null && req.query.minPrice !== '' ? Number(req.query.minPrice) : 0;
    const rawMax = req.query.maxPrice != null && req.query.maxPrice !== '' ? Number(req.query.maxPrice) : Number.MAX_SAFE_INTEGER;
    const min = Number.isFinite(rawMin) ? rawMin : 0;
    const max = Number.isFinite(rawMax) ? rawMax : Number.MAX_SAFE_INTEGER;
    const district = String(req.query.district || '').trim();
    const requiredAmenities = parseAmenityIds(req.query.amenities);
    const viewerId = optionalViewer(req)?.id ?? null;

    const candidates = (await db.collection('rooms').where('status', '==', 'AVAILABLE').get())
      .docs.map((doc) => doc.data())
      .filter((room) => !room.deletedAt);
    const ownerCache = await loadUsersMap(candidates.map((room) => room.ownerId));

    const rooms = candidates.filter((room) => {
      if (room.price < min || room.price > max) return false;
      if (district && !normalizeText(room.district).includes(normalizeText(district))) return false;
      if (!matchesAmenities(room.amenityIds, requiredAmenities)) return false;
      const owner = ownerCache.get(String(room.ownerId));
      return matchesQuery(
        q,
        [room.title, room.description, room.address, room.ward, room.district, room.city, owner?.fullName, owner?.username],
        [owner?.phone]
      );
    });
    const sort = String(req.query.sort || 'newest');
    rooms.sort((a, b) => sort === 'price_asc' ? a.price - b.price : sort === 'price_desc' ? b.price - a.price : sort === 'area' ? b.area - a.area : String(b.createdAt).localeCompare(String(a.createdAt)));
    return ok(res, '', await Promise.all(rooms.map((room) => roomDto(room, viewerId, ownerCache))));
  } catch (error) { return fail(res, error.message, 500); }
});
app.get('/api/rooms/:id', async (req, res) => {
  try {
    const room = await roomById(req.params.id);
    if (!room) return fail(res, 'Không tìm thấy phòng.', 404);
    const viewerId = optionalViewer(req)?.id ?? null;
    return ok(res, '', await roomDto(room, viewerId));
  } catch (error) { return fail(res, error.message, 500); }
});

app.get('/api/users/search', async (req, res) => {
  try {
    const q = String(req.query.q || '').trim();
    if (!q) return ok(res, '', []);
    const [usersSnap, roomsSnap] = await Promise.all([
      db.collection('users').get(),
      db.collection('rooms').get()
    ]);
    const counts = new Map();
    for (const doc of roomsSnap.docs) {
      const room = doc.data();
      if (room.deletedAt || room.status === 'HIDDEN') continue;
      const key = String(room.ownerId);
      const prev = counts.get(key) || { total: 0, available: 0 };
      prev.total += 1;
      if (room.status === 'AVAILABLE') prev.available += 1;
      counts.set(key, prev);
    }
    const matched = usersSnap.docs
      .map((doc) => doc.data())
      .filter((u) => {
        const c = counts.get(String(u.id)) || { total: 0, available: 0 };
        const isLandlordOrHasRooms = u.role === 'LANDLORD' || u.role === 'ADMIN' || c.total > 0;
        if (!isLandlordOrHasRooms) return false;
        return matchesQuery(q, [u.fullName, u.username], [u.phone]);
      })
      .map((u) => {
        const c = counts.get(String(u.id)) || { total: 0, available: 0 };
        return {
          ...publicProfileUser(u),
          roomCount: c.total,
          availableRoomCount: c.available
        };
      })
      .sort((a, b) => {
        const aLandlord = a.role === 'LANDLORD' ? 1 : 0;
        const bLandlord = b.role === 'LANDLORD' ? 1 : 0;
        if (bLandlord !== aLandlord) return bLandlord - aLandlord;
        if (b.availableRoomCount !== a.availableRoomCount) return b.availableRoomCount - a.availableRoomCount;
        return b.roomCount - a.roomCount;
      })
      .slice(0, 20);
    return ok(res, '', matched);
  } catch (error) { return fail(res, error.message, 500); }
});

app.get('/api/users/:id/profile', async (req, res) => {
  try {
    const user = await getUser(req.params.id);
    if (!user) return fail(res, 'Không tìm thấy người dùng.', 404);
    const viewerClaims = optionalViewer(req);
    const viewer = viewerClaims ? await getUser(viewerClaims.id) : null;
    const canSeeHidden = Boolean(viewer && (viewer.id === user.id || viewer.role === 'ADMIN'));

    const snap = await db.collection('rooms').where('ownerId', '==', user.id).get();
    const rooms = snap.docs
      .map((doc) => doc.data())
      .filter((room) => !room.deletedAt && (canSeeHidden || room.status !== 'HIDDEN'))
      .sort((a, b) => String(b.createdAt || '').localeCompare(String(a.createdAt || '')));
    const ownerCache = new Map([[String(user.id), user]]);
    const roomDtos = await Promise.all(rooms.map((room) => roomDto(room, viewer?.id || null, ownerCache)));
    const photos = [];
    for (const r of roomDtos) {
      for (const url of (r.images || [])) {
        photos.push({ url, roomId: r.id, roomTitle: r.title, createdAt: r.createdAt || null });
      }
    }
    return ok(res, '', {
      user: publicProfileUser(user),
      stats: {
        totalRooms: roomDtos.length,
        availableRooms: roomDtos.filter((r) => r.status === 'AVAILABLE').length,
        totalPhotos: photos.length
      },
      rooms: roomDtos,
      photos
    });
  } catch (error) { return fail(res, error.message, 500); }
});

app.use('/api', auth);
app.get('/api/me', async (req, res) => { const user = await getUser(req.user.id); return user ? ok(res, '', publicUser(user)) : fail(res, 'Không tìm thấy tài khoản.', 404); });
app.put('/api/me', async (req, res) => { try { const user = await getUser(req.user.id); if (!user) return fail(res, 'Không tìm thấy tài khoản.', 404); const emailInput = String(req.body.email || '').trim().toLowerCase(); if (emailInput && !isEmail(emailInput)) return fail(res, 'Email không hợp lệ.'); const phoneInput = String(req.body.phone || '').trim(); const phone = phoneInput ? normalizePhone(phoneInput) : null; if (phoneInput && !phone) return fail(res, 'Số điện thoại không hợp lệ (10 số, bắt đầu bằng 0).'); const avatarInput = req.body.avatarUrl === undefined ? undefined : String(req.body.avatarUrl || '').trim(); if (avatarInput && !/^https?:\/\//i.test(avatarInput)) return fail(res, 'Ảnh đại diện không hợp lệ.'); const avatarUrl = avatarInput === undefined ? (user.avatarUrl || null) : (avatarInput || null); const update = { fullName: req.body.fullName?.trim() || user.fullName, phone, email: emailInput || null, avatarUrl, updatedAt: now() }; await db.collection('users').doc(String(req.user.id)).update(update); return ok(res, 'Đã cập nhật hồ sơ.', true); } catch (error) { return fail(res, error.message, 500); } });
app.patch('/api/me/role', async (req, res) => { try { const user = await getUser(req.user.id); if (!user) return fail(res, 'Không tìm thấy tài khoản.', 404); if (user.role === 'ADMIN') return fail(res, 'Tài khoản quản trị không thể đổi vai trò.', 403); const role = req.body.role; if (!['TENANT', 'LANDLORD'].includes(role)) return fail(res, 'Vai trò không hợp lệ.'); if (role === 'LANDLORD' && !String(user.phone || '').trim()) return fail(res, 'Vui lòng cập nhật số điện thoại trước khi chuyển sang Chủ trọ.'); if (role === 'LANDLORD' && !user.acceptedLandlordTerms && req.body.acceptedTerms !== true) return fail(res, 'Bạn cần đồng ý điều khoản để trở thành Chủ trọ.'); await db.collection('users').doc(String(req.user.id)).update({ role, acceptedLandlordTerms: role === 'LANDLORD' ? true : Boolean(user.acceptedLandlordTerms), updatedAt: now() }); return ok(res, role === 'LANDLORD' ? 'Đã chuyển sang vai trò Chủ trọ.' : 'Đã chuyển sang vai trò Người thuê.', true); } catch (error) { return fail(res, error.message, 500); } });

app.get('/api/favorites', async (req, res) => { try { const docs = await db.collection('users').doc(String(req.user.id)).collection('favorites').get(); const rooms = await Promise.all(docs.docs.map(async (doc) => roomById(doc.id))); return ok(res, '', await Promise.all(rooms.filter(Boolean).map((room) => roomDto(room, req.user.id)))); } catch (error) { return fail(res, error.message, 500); } });
app.post('/api/rooms/:id/favorite', async (req, res) => { try { const ref = db.collection('users').doc(String(req.user.id)).collection('favorites').doc(String(req.params.id)); const snap = await ref.get(); if (snap.exists) { await ref.delete(); return ok(res, 'Đã bỏ lưu phòng.', false); } await ref.set({ roomId: Number(req.params.id), createdAt: now() }); return ok(res, 'Đã lưu phòng yêu thích.', true); } catch (error) { return fail(res, error.message, 500); } });

app.post('/api/rooms/:id/requests', requireRole('TENANT', 'ADMIN'), async (req, res) => {
  try {
    const room = await roomById(req.params.id);
    if (!room || room.status !== 'AVAILABLE') return fail(res, 'Phòng không còn nhận yêu cầu.');
    if (room.ownerId === req.user.id) return fail(res, 'Bạn không thể đặt lịch xem phòng của chính mình.');
    const contactPhone = normalizePhone(req.body.phone);
    if (!contactPhone) return fail(res, 'Vui lòng nhập số điện thoại hợp lệ (10 số, bắt đầu bằng 0).');
    const viewingTime = req.body.viewingTime ? String(req.body.viewingTime).trim() : null;
    const timeError = validateViewingTime(viewingTime);
    if (timeError) return fail(res, timeError);
    const exists = await db.collection('rentalRequests').where('roomId', '==', room.id).where('tenantId', '==', req.user.id).where('status', '==', 'PENDING').get();
    if (!exists.empty) return fail(res, 'Bạn đã có yêu cầu đang chờ cho phòng này.', 409);

    const id = numberId();
    const ts = now();
    await db.collection('rentalRequests').doc(String(id)).set({
      id,
      roomId: room.id,
      ownerId: room.ownerId,
      tenantId: req.user.id,
      contactPhone,
      message: req.body.message ? String(req.body.message).trim() : null,
      viewingTime,
      status: 'PENDING',
      ownerNote: null,
      createdAt: ts,
      updatedAt: ts
    });

    const actor = await getUser(req.user.id);
    const timeText = viewingTime ? ` lúc ${viewingTime}` : '';
    await Promise.all([
      createNotification({
        userId: req.user.id,
        type: 'REQUEST_SENT',
        title: 'Đã gửi lịch xem phòng',
        body: `Bạn đã đặt lịch xem "${room.title}"${timeText}.`,
        roomId: room.id,
        requestId: id,
        actor
      }),
      createNotification({
        userId: room.ownerId,
        type: 'REQUEST_RECEIVED',
        title: 'Yêu cầu xem phòng mới',
        body: `${actor?.fullName || 'Người thuê'} muốn xem phòng "${room.title}"${timeText}.`,
        roomId: room.id,
        requestId: id,
        actor
      })
    ]);
    return ok(res, 'Đã gửi yêu cầu xem phòng.', true, 201);
  } catch (error) { return fail(res, error.message, 500); }
});

app.get('/api/requests', async (req, res) => {
  try {
    let data = (await db.collection('rentalRequests').get()).docs.map((doc) => doc.data());
    if (req.user.role === 'LANDLORD') data = data.filter((item) => item.ownerId === req.user.id);
    if (req.user.role === 'TENANT') data = data.filter((item) => item.tenantId === req.user.id);
    data.sort((a, b) => String(b.createdAt).localeCompare(String(a.createdAt)));

    const usersMap = await loadUsersMap(data.flatMap((item) => [item.tenantId, item.ownerId]));
    const uniqueRoomIds = [...new Set(data.map((item) => String(item.roomId)))];
    const roomsMap = new Map();
    await Promise.all(uniqueRoomIds.map(async (rid) => {
      const r = await roomById(rid);
      if (r) roomsMap.set(rid, r);
    }));

    const result = data.map((item) => {
      const room = roomsMap.get(String(item.roomId));
      const tenant = usersMap.get(String(item.tenantId));
      const owner = usersMap.get(String(item.ownerId));
      return {
        id: item.id,
        roomId: item.roomId,
        roomTitle: room?.title || 'Phòng đã xóa',
        roomImage: room?.images?.[0] || null,
        roomPrice: room?.price ?? null,
        ownerId: item.ownerId,
        ownerName: owner?.fullName || 'Chủ trọ',
        ownerPhone: owner?.phone || null,
        ownerAvatarUrl: owner?.avatarUrl || null,
        tenantId: item.tenantId,
        tenantName: tenant?.fullName || 'Người dùng',
        tenantPhone: item.contactPhone || tenant?.phone || null,
        tenantAvatarUrl: tenant?.avatarUrl || null,
        message: item.message || null,
        viewingTime: item.viewingTime || null,
        status: item.status,
        ownerNote: item.ownerNote || null,
        createdAt: item.createdAt
      };
    });
    return ok(res, '', result);
  } catch (error) { return fail(res, error.message, 500); }
});

app.patch('/api/requests/:id', async (req, res) => {
  try {
    const ref = db.collection('rentalRequests').doc(String(req.params.id));
    const snap = await ref.get();
    if (!snap.exists) return fail(res, 'Không tìm thấy yêu cầu.', 404);
    const item = snap.data();
    const nextStatus = String(req.body.status || '');

    if (req.user.role === 'TENANT') {
      if (item.tenantId !== req.user.id) return fail(res, 'Bạn không có quyền xử lý yêu cầu.', 403);
      if (nextStatus !== 'CANCELLED') return fail(res, 'Người thuê chỉ có thể hủy yêu cầu của mình.', 403);
    } else if (req.user.role === 'LANDLORD') {
      if (item.ownerId !== req.user.id) return fail(res, 'Bạn không có quyền xử lý yêu cầu.', 403);
      if (!['ACCEPTED', 'REJECTED', 'CANCELLED'].includes(nextStatus)) return fail(res, 'Trạng thái không hợp lệ.');
    } else if (req.user.role === 'ADMIN') {
      if (!['ACCEPTED', 'REJECTED', 'CANCELLED'].includes(nextStatus)) return fail(res, 'Trạng thái không hợp lệ.');
    } else {
      return fail(res, 'Bạn không có quyền thực hiện chức năng này.', 403);
    }

    if (item.status !== 'PENDING') return fail(res, 'Yêu cầu này đã được xử lý trước đó.');

    const ownerNote = req.body.ownerNote ? String(req.body.ownerNote).trim() : null;
    await ref.update({ status: nextStatus, ownerNote, updatedAt: now() });

    const [actor, room] = await Promise.all([getUser(req.user.id), roomById(item.roomId)]);
    const roomName = room?.title || 'phòng trọ';
    const noteText = ownerNote ? ` (${ownerNote})` : '';
    if (nextStatus === 'ACCEPTED') {
      await createNotification({
        userId: item.tenantId,
        type: 'REQUEST_ACCEPTED',
        title: 'Lịch xem phòng đã được duyệt',
        body: `${actor?.fullName || 'Chủ trọ'} đã chấp nhận yêu cầu xem "${roomName}"${noteText}.`,
        roomId: item.roomId,
        requestId: item.id,
        actor
      });
    } else if (nextStatus === 'REJECTED') {
      await createNotification({
        userId: item.tenantId,
        type: 'REQUEST_REJECTED',
        title: 'Yêu cầu xem phòng bị từ chối',
        body: `${actor?.fullName || 'Chủ trọ'} đã từ chối yêu cầu xem "${roomName}"${noteText}.`,
        roomId: item.roomId,
        requestId: item.id,
        actor
      });
    } else if (nextStatus === 'CANCELLED') {
      const targetUserId = req.user.id === item.tenantId ? item.ownerId : item.tenantId;
      await createNotification({
        userId: targetUserId,
        type: 'REQUEST_CANCELLED',
        title: 'Lịch xem phòng đã bị hủy',
        body: `${actor?.fullName || 'Người dùng'} đã hủy lịch xem "${roomName}".`,
        roomId: item.roomId,
        requestId: item.id,
        actor
      });
    }
    return ok(res, 'Đã cập nhật yêu cầu.', true);
  } catch (error) { return fail(res, error.message, 500); }
});

app.get('/api/conversations', async (req, res) => {
  try {
    const uid = String(req.user.id);
    const snap = await db.collection('conversations').where('participants', 'array-contains', req.user.id).get();
    const convs = snap.docs
      .map((doc) => doc.data())
      .filter((c) => !c.hiddenBy?.[uid])
      .sort((a, b) => {
        const aPinned = a.pinnedBy?.[uid] ? 1 : 0;
        const bPinned = b.pinnedBy?.[uid] ? 1 : 0;
        if (bPinned !== aPinned) return bPinned - aPinned;
        return String(b.lastMessageAt || b.updatedAt || b.createdAt || '').localeCompare(String(a.lastMessageAt || a.updatedAt || a.createdAt || ''));
      });
    const otherIds = convs.map((c) => (c.participants || []).find((id) => Number(id) !== Number(req.user.id)));
    const usersMap = await loadUsersMap(otherIds);
    return ok(res, '', await Promise.all(convs.map((c) => conversationDto(c, req.user.id, usersMap))));
  } catch (error) { return fail(res, error.message, 500); }
});

app.post('/api/conversations/open', async (req, res) => {
  try {
    const otherId = Number(req.body.otherUserId);
    let convId;
    try { convId = conversationIdFor(req.user.id, otherId); }
    catch (err) { return fail(res, err.message); }
    const otherUser = await getUser(otherId);
    if (!otherUser) return fail(res, 'Không tìm thấy người dùng để trò chuyện.', 404);
    const room = req.body.roomId != null ? await roomById(req.body.roomId) : null;

    const ref = db.collection('conversations').doc(convId);
    const snap = await ref.get();
    if (!snap.exists) {
      const ts = now();
      const docData = {
        id: convId,
        participants: [Math.min(req.user.id, otherId), Math.max(req.user.id, otherId)],
        lastMessage: '',
        lastMessageAt: ts,
        lastSenderId: null,
        lastRoomId: room?.id || null,
        lastRoomTitle: room?.title || null,
        unreadCounts: { [String(req.user.id)]: 0, [String(otherId)]: 0 },
        pinnedBy: {},
        hiddenBy: {},
        clearedAt: {},
        blockedBy: {},
        createdAt: ts,
        updatedAt: ts
      };
      await ref.set(docData);
      return ok(res, '', await conversationDto(docData, req.user.id));
    }
    const existing = snap.data();
    const uid = String(req.user.id);
    const patch = {};
    if (existing.hiddenBy?.[uid]) {
      patch[`hiddenBy.${uid}`] = false;
      existing.hiddenBy = { ...(existing.hiddenBy || {}), [uid]: false };
    }
    if (room && existing.lastRoomId !== room.id) {
      patch.lastRoomId = room.id;
      patch.lastRoomTitle = room.title;
      existing.lastRoomId = room.id;
      existing.lastRoomTitle = room.title;
    }
    if (Object.keys(patch).length > 0) await ref.update(patch);
    return ok(res, '', await conversationDto(existing, req.user.id));
  } catch (error) { return fail(res, error.message, 500); }
});

app.get('/api/conversations/:id/messages', async (req, res) => {
  try {
    const ref = db.collection('conversations').doc(String(req.params.id));
    const snap = await ref.get();
    if (!snap.exists) return fail(res, 'Không tìm thấy cuộc trò chuyện.', 404);
    const conv = snap.data();
    if (!(conv.participants || []).includes(req.user.id)) return fail(res, 'Bạn không có quyền xem cuộc trò chuyện này.', 403);

    const uid = String(req.user.id);
    if (Number(conv.unreadCounts?.[uid] || 0) > 0) {
      await ref.update({ [`unreadCounts.${uid}`]: 0 });
      conv.unreadCounts = { ...(conv.unreadCounts || {}), [uid]: 0 };
    }
    const clearedAt = conv.clearedAt?.[uid] || null;
    const msgSnap = await ref.collection('messages').get();
    const messages = msgSnap.docs
      .map((doc) => doc.data())
      .filter((m) => !clearedAt || String(m.createdAt || '') > String(clearedAt))
      .sort((a, b) => String(a.createdAt).localeCompare(String(b.createdAt)) || (Number(a.id) - Number(b.id)));
    return ok(res, '', { conversation: await conversationDto(conv, req.user.id), messages });
  } catch (error) { return fail(res, error.message, 500); }
});

app.post('/api/conversations/:id/messages', async (req, res) => {
  try {
    const ref = db.collection('conversations').doc(String(req.params.id));
    const snap = await ref.get();
    if (!snap.exists) return fail(res, 'Không tìm thấy cuộc trò chuyện.', 404);
    const conv = snap.data();
    if (!(conv.participants || []).includes(req.user.id)) return fail(res, 'Bạn không có quyền gửi tin nhắn trong cuộc trò chuyện này.', 403);

    const uid = String(req.user.id);
    const otherId = (conv.participants || []).find((id) => Number(id) !== Number(req.user.id));
    if (conv.blockedBy?.[uid]) return fail(res, 'Bạn đã chặn người dùng này. Hãy bỏ chặn để tiếp tục nhắn tin.', 403);
    if (otherId != null && conv.blockedBy?.[String(otherId)]) return fail(res, 'Bạn không thể gửi tin nhắn vì người dùng này đã chặn bạn.', 403);

    const text = String(req.body.text || '').trim();
    if (!text) return fail(res, 'Vui lòng nhập nội dung tin nhắn.');
    if (text.length > 1000) return fail(res, 'Tin nhắn tối đa 1000 ký tự.');

    const room = req.body.roomId != null ? await roomById(req.body.roomId) : null;
    const msgId = numberId();
    const ts = now();
    const message = {
      id: msgId,
      senderId: req.user.id,
      text,
      roomId: room?.id || null,
      roomTitle: room?.title || null,
      roomPrice: room?.price ?? null,
      roomImage: room?.images?.[0] || null,
      createdAt: ts
    };
    await ref.collection('messages').doc(String(msgId)).set(message);

    const updatePayload = {
      lastMessage: text,
      lastMessageAt: ts,
      lastSenderId: req.user.id,
      updatedAt: ts,
      [`unreadCounts.${uid}`]: 0,
      [`hiddenBy.${uid}`]: false
    };
    if (room) {
      updatePayload.lastRoomId = room.id;
      updatePayload.lastRoomTitle = room.title;
    }
    if (otherId != null) {
      updatePayload[`unreadCounts.${otherId}`] = admin.firestore.FieldValue.increment(1);
      updatePayload[`hiddenBy.${otherId}`] = false;
    }
    await ref.update(updatePayload);
    return ok(res, '', message, 201);
  } catch (error) { return fail(res, error.message, 500); }
});

app.patch('/api/conversations/:id/read', async (req, res) => {
  try {
    const ref = db.collection('conversations').doc(String(req.params.id));
    const snap = await ref.get();
    if (!snap.exists) return fail(res, 'Không tìm thấy cuộc trò chuyện.', 404);
    const conv = snap.data();
    if (!(conv.participants || []).includes(req.user.id)) return fail(res, 'Bạn không có quyền thao tác.', 403);
    await ref.update({ [`unreadCounts.${req.user.id}`]: 0 });
    return ok(res, '', true);
  } catch (error) { return fail(res, error.message, 500); }
});

app.patch('/api/conversations/:id/pin', async (req, res) => {
  try {
    const ref = db.collection('conversations').doc(String(req.params.id));
    const snap = await ref.get();
    if (!snap.exists) return fail(res, 'Không tìm thấy cuộc trò chuyện.', 404);
    const conv = snap.data();
    if (!(conv.participants || []).includes(req.user.id)) return fail(res, 'Bạn không có quyền thao tác.', 403);
    const uid = String(req.user.id);
    const nextPinned = typeof req.body.pinned === 'boolean' ? req.body.pinned : !Boolean(conv.pinnedBy?.[uid]);
    await ref.update({ [`pinnedBy.${uid}`]: nextPinned ? now() : null });
    return ok(res, nextPinned ? 'Đã ghim cuộc trò chuyện.' : 'Đã bỏ ghim cuộc trò chuyện.', { isPinned: nextPinned });
  } catch (error) { return fail(res, error.message, 500); }
});

app.delete('/api/conversations/:id', async (req, res) => {
  try {
    const ref = db.collection('conversations').doc(String(req.params.id));
    const snap = await ref.get();
    if (!snap.exists) return fail(res, 'Không tìm thấy cuộc trò chuyện.', 404);
    const conv = snap.data();
    if (!(conv.participants || []).includes(req.user.id)) return fail(res, 'Bạn không có quyền thao tác.', 403);
    const uid = String(req.user.id);
    await ref.update({
      [`hiddenBy.${uid}`]: true,
      [`clearedAt.${uid}`]: now(),
      [`pinnedBy.${uid}`]: null,
      [`unreadCounts.${uid}`]: 0
    });
    return ok(res, 'Đã xóa cuộc trò chuyện.', true);
  } catch (error) { return fail(res, error.message, 500); }
});

app.patch('/api/conversations/:id/block', async (req, res) => {
  try {
    const ref = db.collection('conversations').doc(String(req.params.id));
    const snap = await ref.get();
    if (!snap.exists) return fail(res, 'Không tìm thấy cuộc trò chuyện.', 404);
    const conv = snap.data();
    if (!(conv.participants || []).includes(req.user.id)) return fail(res, 'Bạn không có quyền thao tác.', 403);
    const uid = String(req.user.id);
    const nextBlocked = typeof req.body.blocked === 'boolean' ? req.body.blocked : !Boolean(conv.blockedBy?.[uid]);
    await ref.update({ [`blockedBy.${uid}`]: nextBlocked });
    return ok(res, nextBlocked ? 'Đã chặn người dùng.' : 'Đã bỏ chặn người dùng.', { blockedByMe: nextBlocked });
  } catch (error) { return fail(res, error.message, 500); }
});

app.get('/api/notifications', async (req, res) => {
  try {
    const snap = await db.collection('notifications').where('userId', '==', req.user.id).get();
    const items = snap.docs
      .map((doc) => doc.data())
      .sort((a, b) => String(b.createdAt || '').localeCompare(String(a.createdAt || '')))
      .slice(0, 50);
    return ok(res, '', items);
  } catch (error) { return fail(res, error.message, 500); }
});

app.patch('/api/notifications/read-all', async (req, res) => {
  try {
    const snap = await db.collection('notifications').where('userId', '==', req.user.id).get();
    const unreadDocs = snap.docs.filter((doc) => !doc.data().isRead);
    for (let i = 0; i < unreadDocs.length; i += 400) {
      const batch = db.batch();
      unreadDocs.slice(i, i + 400).forEach((doc) => batch.update(doc.ref, { isRead: true }));
      await batch.commit();
    }
    return ok(res, 'Đã đánh dấu tất cả thông báo là đã đọc.', true);
  } catch (error) { return fail(res, error.message, 500); }
});

app.patch('/api/notifications/:id/read', async (req, res) => {
  try {
    const ref = db.collection('notifications').doc(String(req.params.id));
    const snap = await ref.get();
    if (!snap.exists) return fail(res, 'Không tìm thấy thông báo.', 404);
    if (snap.data().userId !== req.user.id) return fail(res, 'Bạn không có quyền thao tác thông báo này.', 403);
    await ref.update({ isRead: true });
    return ok(res, '', true);
  } catch (error) { return fail(res, error.message, 500); }
});

app.get('/api/badges', async (req, res) => {
  try {
    const [convSnap, notifSnap, reqSnap] = await Promise.all([
      db.collection('conversations').where('participants', 'array-contains', req.user.id).get(),
      db.collection('notifications').where('userId', '==', req.user.id).get(),
      db.collection('rentalRequests').where('status', '==', 'PENDING').get()
    ]);
    const unreadMessages = convSnap.docs
      .filter((doc) => !doc.data().hiddenBy?.[String(req.user.id)])
      .reduce((sum, doc) => sum + Number(doc.data().unreadCounts?.[String(req.user.id)] || 0), 0);
    const unreadNotifications = notifSnap.docs.filter((doc) => !doc.data().isRead).length;
    const pendingRequests = reqSnap.docs
      .map((doc) => doc.data())
      .filter((item) => req.user.role === 'ADMIN' || (req.user.role === 'LANDLORD' ? item.ownerId === req.user.id : item.tenantId === req.user.id))
      .length;
    return ok(res, '', { unreadMessages, unreadNotifications, pendingRequests });
  } catch (error) { return fail(res, error.message, 500); }
});

app.post('/api/uploads/image', upload.single('image'), async (req, res) => {
  try {
    const isAvatar = req.query.type === 'avatar';
    // Ảnh đại diện: ai đăng nhập cũng tải được. Ảnh phòng: chỉ Chủ trọ / Quản trị viên.
    if (!isAvatar && !['LANDLORD', 'ADMIN'].includes(req.user.role)) return fail(res, 'Bạn không có quyền thực hiện chức năng này.', 403);
    if (!req.file) return fail(res, 'Vui lòng chọn một ảnh để tải lên.');
    if (!req.file.mimetype?.startsWith('image/')) return fail(res, 'File phải là ảnh (jpg, png, ...).');
    const url = await uploadImageBuffer(req.file.buffer, req.file.mimetype, `${isAvatar ? 'avatars' : 'rooms'}/${req.user.id}`);
    return ok(res, 'Đã tải ảnh lên.', { url }, 201);
  } catch (error) { return fail(res, error.message, 500); }
});
app.get('/api/owner/rooms', requireRole('LANDLORD', 'ADMIN'), async (req, res) => { try { let rooms = (await db.collection('rooms').get()).docs.map((doc) => doc.data()).filter((room) => !room.deletedAt); if (req.user.role !== 'ADMIN') rooms = rooms.filter((room) => room.ownerId === req.user.id); const ownerCache = await loadUsersMap(rooms.map((room) => room.ownerId)); return ok(res, '', await Promise.all(rooms.map((room) => roomDto(room, req.user.id, ownerCache)))); } catch (error) { return fail(res, error.message, 500); } });
app.post('/api/rooms', requireRole('LANDLORD', 'ADMIN'), async (req, res) => { try { const id = numberId(); const room = { id, ...roomPayload(req.body, req.user.id), createdAt: now(), deletedAt: null }; await db.collection('rooms').doc(String(id)).set(room); return ok(res, 'Đăng phòng thành công.', { id }, 201); } catch (error) { return fail(res, error.message); } });
app.put('/api/rooms/:id', requireRole('LANDLORD', 'ADMIN'), async (req, res) => { try { const room = await roomById(req.params.id); if (!room) return fail(res, 'Không tìm thấy phòng.', 404); if (req.user.role !== 'ADMIN' && room.ownerId !== req.user.id) return fail(res, 'Không có quyền sửa phòng này.', 403); await db.collection('rooms').doc(String(room.id)).update(roomPayload(req.body, room.ownerId)); return ok(res, 'Đã cập nhật phòng.', true); } catch (error) { return fail(res, error.message); } });
app.patch('/api/rooms/:id/status', requireRole('LANDLORD', 'ADMIN'), async (req, res) => { try { const room = await roomById(req.params.id); if (!room) return fail(res, 'Không tìm thấy phòng.', 404); if (req.user.role !== 'ADMIN' && room.ownerId !== req.user.id) return fail(res, 'Không có quyền thay đổi trạng thái.', 403); if (!ROOM_STATUSES.includes(req.body.status)) return fail(res, 'Trạng thái không hợp lệ.'); await db.collection('rooms').doc(String(room.id)).update({ status: req.body.status, updatedAt: now() }); return ok(res, 'Đã cập nhật trạng thái.', true); } catch (error) { return fail(res, error.message); } });
app.delete('/api/rooms/:id', requireRole('LANDLORD', 'ADMIN'), async (req, res) => { try { const room = await roomById(req.params.id); if (!room) return fail(res, 'Không tìm thấy phòng.', 404); if (req.user.role !== 'ADMIN' && room.ownerId !== req.user.id) return fail(res, 'Không có quyền xóa phòng này.', 403); await db.collection('rooms').doc(String(room.id)).update({ status: 'HIDDEN', deletedAt: now(), updatedAt: now() }); return ok(res, 'Đã ẩn tin đăng.', true); } catch (error) { return fail(res, error.message); } });
app.get('/api/dashboard', async (req, res) => {
  try {
    const requestsAll = (await db.collection('rentalRequests').get()).docs.map((doc) => doc.data());
    if (req.user.role === 'TENANT') {
      const myRequests = requestsAll.filter((item) => item.tenantId === req.user.id);
      const favCount = (await db.collection('users').doc(String(req.user.id)).collection('favorites').get()).size;
      return ok(res, '', {
        totalRooms: 0,
        availableRooms: 0,
        pendingRequests: myRequests.filter((item) => item.status === 'PENDING').length,
        totalFavorites: favCount,
        totalRequests: myRequests.length,
        acceptedRequests: myRequests.filter((item) => item.status === 'ACCEPTED').length
      });
    }
    let rooms = (await db.collection('rooms').get()).docs.map((doc) => doc.data()).filter((room) => !room.deletedAt);
    if (req.user.role !== 'ADMIN') rooms = rooms.filter((room) => room.ownerId === req.user.id);
    let requests = requestsAll;
    if (req.user.role !== 'ADMIN') requests = requests.filter((item) => item.ownerId === req.user.id);
    const favoriteCount = (await Promise.all(rooms.map(async (room) => (await db.collection('users').doc(String(room.ownerId)).collection('favorites').where('roomId', '==', room.id).get()).size))).reduce((sum, count) => sum + count, 0);
    return ok(res, '', {
      totalRooms: rooms.length,
      availableRooms: rooms.filter((room) => room.status === 'AVAILABLE').length,
      pendingRequests: requests.filter((item) => item.status === 'PENDING').length,
      totalFavorites: favoriteCount,
      totalRequests: requests.length,
      acceptedRequests: requests.filter((item) => item.status === 'ACCEPTED').length
    });
  } catch (error) { return fail(res, error.message, 500); }
});

app.use((error, _, res, __) => {
  if (error instanceof multer.MulterError) return fail(res, error.code === 'LIMIT_FILE_SIZE' ? 'Ảnh quá lớn, tối đa 5MB.' : 'Không tải được ảnh lên.', 400);
  console.error(error); return fail(res, 'Có lỗi máy chủ xảy ra.', 500);
});
app.listen(port, '0.0.0.0', () => console.log(`Thuê Trọ Node.js API đang chạy tại http://0.0.0.0:${port}`));
