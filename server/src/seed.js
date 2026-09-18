require('dotenv').config();
const bcrypt = require('bcryptjs');
const { db } = require('./firebase');

const now = () => new Date().toISOString();

async function writeUser(user) {
  const hash = await bcrypt.hash(user.password, 10);
  const payload = { id: user.id, username: user.username, passwordHash: hash, fullName: user.fullName, phone: user.phone, email: user.email, role: user.role, avatarUrl: null, createdAt: now(), updatedAt: now() };
  const batch = db.batch();
  batch.set(db.collection('users').doc(String(user.id)), payload, { merge: true });
  batch.set(db.collection('usernames').doc(user.username), { userId: user.id });
  await batch.commit();
}

async function main() {
  await writeUser({ id: 1, username: 'admin', password: '123', fullName: 'Quản trị viên', phone: '0900000000', email: 'admin@thuetro.local', role: 'ADMIN' });
  await writeUser({ id: 2, username: 'chutro_demo', password: '123', fullName: 'Nguyễn Minh Chủ', phone: '0901234567', email: 'chutro@thuetro.local', role: 'LANDLORD' });
  await writeUser({ id: 3, username: 'nguoithue_demo', password: '123', fullName: 'Trần An Thuê', phone: '0907654321', email: 'thue@thuetro.local', role: 'TENANT' });

  const rooms = [
    { id: 1, ownerId: 2, title: 'Phòng studio đầy đủ nội thất, gần ĐH HUTECH', description: 'Phòng mới, sạch sẽ, có cửa sổ lớn. Giờ giấc tự do, an ninh tốt.', address: '25/8 Ung Văn Khiêm', ward: 'Phường 25', district: 'Bình Thạnh', city: 'Hồ Chí Minh', price: 4200000, area: 28, maxTenants: 2, deposit: 4200000, electricityPrice: 3500, waterPrice: 15000, internetFee: 100000, status: 'AVAILABLE', images: ['https://images.unsplash.com/photo-1522708323590-d24dbb6b0267?auto=format&fit=crop&w=900&q=80'], amenityIds: [1,2,4,5] },
    { id: 2, ownerId: 2, title: 'Phòng trọ có gác gần Làng Đại học', description: 'Phù hợp sinh viên, đường rộng, gần chợ và xe buýt.', address: '88 Đường Số 8', ward: 'Linh Trung', district: 'Thủ Đức', city: 'Hồ Chí Minh', price: 2800000, area: 20, maxTenants: 2, deposit: 2800000, electricityPrice: 3500, waterPrice: 12000, internetFee: 0, status: 'AVAILABLE', images: ['https://images.unsplash.com/photo-1560185007-cde436f6a4d0?auto=format&fit=crop&w=900&q=80'], amenityIds: [1,3,4] },
    { id: 3, ownerId: 2, title: 'Căn hộ mini 1 phòng ngủ', description: 'Căn hộ riêng tư, nội thất cơ bản, có ban công.', address: '120 Nguyễn Xí', ward: 'Phường 26', district: 'Bình Thạnh', city: 'Hồ Chí Minh', price: 6500000, area: 42, maxTenants: 3, deposit: 6500000, electricityPrice: 4000, waterPrice: 18000, internetFee: 150000, status: 'RENTED', images: ['https://images.unsplash.com/photo-1502672023488-70e25813eb80?auto=format&fit=crop&w=900&q=80'], amenityIds: [1,2,4,6] }
  ];
  const batch = db.batch();
  rooms.forEach((room) => batch.set(db.collection('rooms').doc(String(room.id)), { ...room, createdAt: now(), updatedAt: now(), deletedAt: null }, { merge: true }));
  await batch.commit();
  console.log('Đã tạo dữ liệu Firebase: admin / 123, 3 tài khoản và 3 phòng mẫu.');
}

main().catch((error) => { console.error(error); process.exit(1); });
