# Kết nối Firebase cho Node.js API

Backend Node.js dùng **Firebase Firestore** thay MySQL. App Android gọi API tại `http://10.11.4.29:8080/`; không cần cài Firebase SDK vào app.

## 1. Tạo Firestore

Trong Firebase Console của project `ThueTro`:

1. Mở **Cơ sở dữ liệu và lưu trữ** → **Cloud Firestore**.
2. Bấm **Tạo cơ sở dữ liệu**.
3. Chọn **Production mode**.
4. Chọn vị trí gần nhất (Singapore nếu có lựa chọn đó).
5. Sau khi tạo xong, mở tab **Rules** và dán toàn bộ nội dung file `server/firestore.rules`, sau đó bấm **Publish**.

Rules này chặn mọi truy cập trực tiếp từ ứng dụng. Node.js API dùng Service Account nên vẫn có quyền truy cập Firestore.

## 2. Tải Service Account (không gửi file này vào chat)

1. Bấm bánh răng → **Cài đặt dự án**.
2. Mở tab **Tài khoản dịch vụ**.
3. Ở mục Firebase Admin SDK, bấm **Tạo khóa riêng tư mới** → **Tạo khóa**.
4. File JSON tải xuống là thông tin bí mật. Đổi tên thành `firebase-service-account.json`.
5. Tạo thư mục `server/secrets` và đặt file tại:

   `server/secrets/firebase-service-account.json`

File này đã được bỏ qua bởi Git; không đăng lên GitHub và không gửi nội dung file vào chat.

## 3. Đăng ký Cloudinary (để upload ảnh phòng, miễn phí, không cần thẻ)

Firebase Storage hiện bắt buộc nâng cấp gói Blaze (cần thẻ ngân hàng) nên project này dùng **Cloudinary** để lưu ảnh thay thế — có gói miễn phí vĩnh viễn (25GB lưu trữ, 25GB băng thông/tháng), không cần khai báo thẻ.

1. Vào [cloudinary.com](https://cloudinary.com), bấm **Sign up for free**, đăng ký bằng email hoặc Google.
2. Sau khi vào Dashboard, ngay trang chủ sẽ thấy 3 thông tin: **Cloud name**, **API Key**, **API Secret** (bấm vào icon con mắt để hiện API Secret).
3. Copy 3 giá trị này vào file `server/.env` ở bước 4 bên dưới, tương ứng với `CLOUDINARY_CLOUD_NAME`, `CLOUDINARY_API_KEY`, `CLOUDINARY_API_SECRET`.

Chỉ cần 1 người trong nhóm đăng ký 1 tài khoản Cloudinary rồi chia 3 giá trị này cho cả nhóm dùng chung là đủ (không cần mỗi người 1 tài khoản riêng như Firebase Service Account, vì đây không phải thông tin đăng nhập nhạy cảm ở mức đó — tuy vậy vẫn nên giữ trong `.env`, không đưa lên GitHub, để tránh người lạ lợi dụng quota upload).

## 4. Cấu hình và chạy Node.js

Trong thư mục `server`, sao chép `.env.example` thành `.env`. Giữ đường dẫn Service Account như mặc định, điền 3 biến `CLOUDINARY_...` theo bước 3, và thay `JWT_SECRET` bằng một chuỗi ngẫu nhiên dài.

```powershell
npm install
npm run seed
npm start
```

`npm run seed` chỉ cần chạy một lần để tạo dữ liệu mẫu trong Firestore, gồm `admin / 123`.

`npm start` phải giữ mở Terminal và sẽ hiển thị API đang chạy tại port 8080. Khi đó mở app Android trên Nox. Bản hiện tại gọi API qua IPv4 Wi-Fi `http://10.11.4.29:8080/`. Nếu máy đổi mạng Wi-Fi và IP thay đổi, cập nhật `API_BASE_URL` trong `android/app/build.gradle.kts`, rồi build/cài lại APK.
