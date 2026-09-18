const AMENITIES = [
  { id: 1, name: 'Wi-Fi', icon: 'wifi' },
  { id: 2, name: 'Máy lạnh', icon: 'ac' },
  { id: 3, name: 'Gác lửng', icon: 'stairs' },
  { id: 4, name: 'Bãi xe', icon: 'parking' },
  { id: 5, name: 'Camera an ninh', icon: 'camera' },
  { id: 6, name: 'Máy giặt', icon: 'washer' }
];

const ROOM_STATUSES = ['AVAILABLE', 'RESERVED', 'RENTED', 'HIDDEN'];
const REQUEST_STATUSES = ['PENDING', 'ACCEPTED', 'REJECTED', 'CANCELLED'];

const AREAS = [
  { city: 'TP. Hồ Chí Minh', district: 'TP. Thủ Đức' },
  ...['Quận 1', 'Quận 3', 'Quận 4', 'Quận 5', 'Quận 6', 'Quận 7', 'Quận 8', 'Quận 10', 'Quận 11', 'Quận 12', 'Bình Thạnh', 'Gò Vấp', 'Phú Nhuận', 'Tân Bình', 'Tân Phú', 'Bình Tân', 'Bình Chánh', 'Củ Chi', 'Hóc Môn', 'Nhà Bè', 'Cần Giờ'].map((district) => ({ city: 'TP. Hồ Chí Minh', district })),
  ...['TP. Dĩ An', 'TP. Thuận An', 'TP. Thủ Dầu Một'].map((district) => ({ city: 'Bình Dương', district })),
  { city: 'Đồng Nai', district: 'TP. Biên Hòa' },
  { city: 'Long An', district: 'Đức Hòa' },
  { city: 'Long An', district: 'Bến Lức' }
];

module.exports = { AMENITIES, ROOM_STATUSES, REQUEST_STATUSES, AREAS };
