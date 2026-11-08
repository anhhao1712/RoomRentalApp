const cloudinary = require('cloudinary').v2;

cloudinary.config({
  cloud_name: process.env.CLOUDINARY_CLOUD_NAME,
  api_key: process.env.CLOUDINARY_API_KEY,
  api_secret: process.env.CLOUDINARY_API_SECRET,
});

async function uploadImageBuffer(buffer, mimeType, folder) {
  if (!process.env.CLOUDINARY_CLOUD_NAME) {
    throw new Error('Chưa cấu hình Cloudinary trong file .env. Xem docs/FIREBASE_NODEJS.md.');
  }
  const dataUri = `data:${mimeType};base64,${buffer.toString('base64')}`;
  const result = await cloudinary.uploader.upload(dataUri, { folder, resource_type: 'image' });
  return result.secure_url;
}

module.exports = { uploadImageBuffer };
