// Gửi email. Nếu chưa cấu hình SMTP_USER/SMTP_PASS thì chạy chế độ "console":
// không gửi thật mà in nội dung email ra cửa sổ server (tiện demo trên emulator).
const nodemailer = require('nodemailer');

function createMailer(env = process.env) {
  if (!env.SMTP_USER || !env.SMTP_PASS) {
    return {
      mode: 'console',
      async send({ to, subject, text }) {
        console.log(`\n[EMAIL - chế độ console] Tới: ${to}\nTiêu đề: ${subject}\n${text}\n`);
      }
    };
  }
  const port = Number(env.SMTP_PORT || 465);
  const transporter = nodemailer.createTransport({
    host: env.SMTP_HOST || 'smtp.gmail.com',
    port,
    secure: port === 465,
    auth: { user: env.SMTP_USER, pass: env.SMTP_PASS }
  });
  return {
    mode: 'smtp',
    async send({ to, subject, text }) {
      await transporter.sendMail({ from: env.MAIL_FROM || `Thuê Trọ <${env.SMTP_USER}>`, to, subject, text });
    }
  };
}

module.exports = { createMailer };
