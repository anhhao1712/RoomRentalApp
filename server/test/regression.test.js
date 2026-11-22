const test = require('node:test');
const assert = require('node:assert/strict');
const { roomPayload } = require('../src/room-payload');
const { createAuth } = require('../src/auth');
const valid = { title: ' Phòng mới ', address: ' 12 Nguyễn Trãi ', district: 'Quận 1', price: 3000000, area: 20 };

test('room values preserve free fees and apply defaults only when absent', () => {
  const room = roomPayload({ ...valid, electricityPrice: 0, waterPrice: '0' }, 1);
  assert.equal(room.electricityPrice, 0); assert.equal(room.waterPrice, 0);
  assert.equal(room.maxTenants, 2); assert.equal(room.title, 'Phòng mới');
});
test('reject negative, non-finite and nonnumeric prices, areas and fees', () => {
  for (const field of ['price', 'area', 'deposit', 'electricityPrice', 'waterPrice', 'internetFee']) {
    for (const value of [-1, Infinity, 'Infinity', 'abc', true, [], {}]) {
      assert.throws(() => roomPayload({ ...valid, [field]: value }, 1), field + ': ' + String(value));
    }
  }
  for (const field of ['price', 'area', 'maxTenants']) assert.throws(() => roomPayload({ ...valid, [field]: 0 }, 1));
  assert.throws(() => roomPayload({ ...valid, maxTenants: 1.5 }, 1));
});
test('coordinates support clearing, omission, valid zero and reject partial values', () => {
  assert.equal('latitude' in roomPayload(valid, 1), false);
  const cleared = roomPayload({ ...valid, latitude: null, longitude: null }, 1);
  assert.equal(cleared.latitude, null); assert.equal(cleared.longitude, null);
  assert.equal(roomPayload({ ...valid, latitude: 0, longitude: 0 }, 1).latitude, 0);
  for (const coords of [{ latitude: 10 }, { latitude: null }, { latitude: 91, longitude: 0 }, { latitude: 0, longitude: 181 }]) {
    assert.throws(() => roomPayload({ ...valid, ...coords }, 1));
  }
});
test('malformed text is rejected and image values are restricted to web URLs', () => {
  assert.throws(() => roomPayload({ ...valid, title: 123 }, 1));
  const room = roomPayload({ ...valid, imageUrls: [true, {}, 'javascript:alert(1)', 'https://example.com/a.jpg'], amenityIds: [1, 1, 999] }, 1);
  assert.deepEqual(room.images, ['https://example.com/a.jpg']); assert.deepEqual(room.amenityIds, [1]);
});

async function authenticate({ header = 'Bearer token', user = { id: 1, role: 'TENANT' }, claims = { id: 1, role: 'LANDLORD' }, failure } = {}) {
  const result = {};
  const req = { headers: { authorization: header } };
  const auth = createAuth({ verifyToken: () => claims, getUser: async () => { if (failure) throw failure; return user; }, fail: (_, message, status) => { result.status = status; } });
  await auth(req, {}, (error) => { result.next = true; result.error = error; });
  return { ...result, user: req.user };
}
test('role changes take effect with the existing token in both directions', async () => {
  assert.equal((await authenticate()).user.role, 'TENANT');
  assert.equal((await authenticate({ user: { id: 1, role: 'LANDLORD' }, claims: { id: 1, role: 'TENANT' } })).user.role, 'LANDLORD');
});
test('deleted accounts and malformed authorization are rejected', async () => {
  assert.equal((await authenticate({ user: null })).status, 401);
  assert.equal((await authenticate({ header: 'token' })).status, 401);
  assert.equal((await authenticate({ claims: { id: '1' } })).status, 401);
});
test('database failures reach error handling without authorizing the request', async () => {
  const failure = new Error('database unavailable');
  const result = await authenticate({ failure });
  assert.equal(result.error, failure); assert.equal(result.user, undefined);
});
test('expired tokens are rejected before reading the user', async () => {
  let status;
  await createAuth({ verifyToken: () => { throw new Error('expired'); }, getUser: () => assert.fail('must not read user'), fail: (_, message, code) => { status = code; } })({ headers: { authorization: 'Bearer expired' } }, {}, () => assert.fail('must not authorize'));
  assert.equal(status, 401);
});

const { validatePassword } = require('../src/password-rules');
test('password rules require length, upper, lower and digit', () => {
  assert.equal(validatePassword('Abcdef1!'), null);
  assert.equal(validatePassword('Mậtkhẩu1@'), null);
  for (const weak of ['', '123', 'abcdefgh', 'ABCDEFGH', 'Abcdefgh', 'abcdef12', 'ABCDEF12', 'Abcdef12', 'Mậtkhẩu12', 'Abc 123!', 'A1b!'.repeat(30)]) {
    assert.notEqual(validatePassword(weak), null, weak);
  }
});

const resetRules = require('../src/password-reset');
test('reset codes are 6 digits, expire, lock after too many attempts and limit resends', () => {
  for (let i = 0; i < 50; i++) assert.match(resetRules.generateCode(), /^\d{6}$/);
  const record = resetRules.createRecord('hash', 1000);
  assert.equal(resetRules.checkRecord(record, 2000).ok, true);
  assert.equal(resetRules.checkRecord(record, 1000 + resetRules.CODE_TTL_MS + 1).reason, 'EXPIRED');
  assert.equal(resetRules.checkRecord({ ...record, attempts: resetRules.MAX_ATTEMPTS }, 2000).reason, 'LOCKED');
  assert.equal(resetRules.checkRecord(null, 2000).reason, 'MISSING');
  assert.equal(resetRules.secondsUntilResend(record, 1000 + 30000), 30);
  assert.equal(resetRules.secondsUntilResend(record, 1000 + 60000), 0);
  assert.equal(resetRules.secondsUntilResend(null, 5), 0);
});
test('email validation and masking', () => {
  assert.equal(resetRules.isEmail('duy@gmail.com'), true);
  for (const bad of ['', 'abc', 'a@b', 'a b@c.com', null]) assert.equal(resetRules.isEmail(bad), false, String(bad));
  assert.equal(resetRules.maskEmail('duy@gmail.com'), 'd**@gmail.com');
});

const { isEmail: isEmail2, normalizePhone } = require('../src/contact-rules');
test('phone numbers are normalized to 10 digits and invalid ones rejected', () => {
  assert.equal(normalizePhone('0912 345 678'), '0912345678');
  assert.equal(normalizePhone('+84912345678'), '0912345678');
  assert.equal(normalizePhone('84912345678'), '0912345678');
  for (const bad of ['', null, '12345', '091234567', '09123456789', 'abcdefghij']) assert.equal(normalizePhone(bad), null, String(bad));
  assert.equal(isEmail2('a@b.vn'), true);
});

const {
  normalizeText,
  matchesQuery,
  conversationIdFor,
  validateViewingTime,
  parseAmenityIds,
  matchesAmenities
} = require('../src/search-helpers');
test('search helpers match accent-insensitive text, usernames and phone numbers', () => {
  assert.equal(normalizeText('  TP. Thủ Đức  '), 'tp. thu duc');
  assert.equal(normalizeText('Đặng Minh Chủ'), 'dang minh chu');
  assert.equal(matchesQuery('thu duc', ['Phòng trọ Thủ Đức'], []), true);
  assert.equal(matchesQuery('minh chu', ['Nguyễn Minh Chủ'], []), true);
  assert.equal(matchesQuery('0901 234 567', ['Phòng mới'], ['0901234567']), true);
  assert.equal(matchesQuery('090123', ['Phòng mới'], ['0901234567']), true);
  assert.equal(matchesQuery('12', ['Phòng mới'], ['0901234567']), false);
  assert.equal(matchesQuery('', ['Phòng mới'], []), true);
});
test('conversationIdFor generates symmetric IDs and rejects invalid or self-chat IDs', () => {
  assert.equal(conversationIdFor(5, 2), '2_5');
  assert.equal(conversationIdFor('2', '5'), '2_5');
  assert.throws(() => conversationIdFor(3, 3));
  assert.throws(() => conversationIdFor(0, 3));
  assert.throws(() => conversationIdFor('abc', 3));
});
test('validateViewingTime enforces 07:00 to 21:00 range', () => {
  assert.equal(validateViewingTime('07:00 - 23/11/2026'), null);
  assert.equal(validateViewingTime('12:30'), null);
  assert.equal(validateViewingTime('21:00 - 23/11/2026'), null);
  assert.equal(validateViewingTime('18:30 - 23/11/2026'), null);
  assert.equal(validateViewingTime(''), null);
  assert.notEqual(validateViewingTime('06:59 - 23/11/2026'), null);
  assert.notEqual(validateViewingTime('21:01 - 23/11/2026'), null);
  assert.notEqual(validateViewingTime('23:00'), null);
  assert.notEqual(validateViewingTime('00:30'), null);
});
test('parseAmenityIds and matchesAmenities parse unique positive IDs and enforce all selected', () => {
  assert.deepEqual(parseAmenityIds('1, 2,2,abc,-1,0,4'), [1, 2, 4]);
  assert.deepEqual(parseAmenityIds(''), []);
  assert.deepEqual(parseAmenityIds(null), []);
  assert.equal(matchesAmenities([1, 2, 4], []), true);
  assert.equal(matchesAmenities([1, 2, 4], [1, 4]), true);
  assert.equal(matchesAmenities([1, 2], [1, 4]), false);
  assert.equal(matchesAmenities(null, [1]), false);
});
