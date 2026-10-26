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
