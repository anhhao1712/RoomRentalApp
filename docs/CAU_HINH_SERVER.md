# Cấu hình server theo mạng hiện tại

Node.js luôn chạy cổng `8080`; không cần đổi IP trong code server.

Mỗi khi máy đổi Wi-Fi hoặc IP Wi-Fi thay đổi:

1. Mở PowerShell và chạy `ipconfig`.
2. Tìm dòng `IPv4 Address` của mục `Wi-Fi`.
3. Mở file `android/local.properties`.
4. Thêm hoặc sửa dòng sau, thay IP bằng IP vừa tìm được:

```properties
API_BASE_URL=http://10.11.4.29:8080/
```

5. Trong Android Studio, bấm `Run` lại để app được cài lại với địa chỉ mới.

`local.properties` không được đưa lên GitHub. Thành viên khác tự tạo dòng `API_BASE_URL` theo IP máy chạy Node.js của họ.

## Bảo mật Firebase

- Không đưa `server/secrets/firebase-service-account.json` lên GitHub.
- Không đưa `server/.env` lên GitHub.
- Chỉ máy chạy Node.js mới giữ service account JSON.
- Dùng `server/.env.example` làm mẫu; mỗi máy tạo file `.env` riêng và đặt `JWT_SECRET` ngẫu nhiên.
