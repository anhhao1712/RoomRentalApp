// Read the current role on every protected request so role changes take effect immediately.
function createAuth({ verifyToken, getUser, fail }) {
  return async (req, res, next) => {
    const header = req.headers.authorization;
    const match = typeof header === 'string' && /^Bearer\s+(\S+)$/i.exec(header);
    if (!match) return fail(res, 'Bạn cần đăng nhập.', 401);
    let claims;
    try { claims = verifyToken(match[1]); }
    catch { return fail(res, 'Phiên đăng nhập đã hết hạn.', 401); }
    if (!claims || !Number.isSafeInteger(claims.id)) return fail(res, 'Phiên đăng nhập không hợp lệ.', 401);
    try {
      const user = await getUser(claims.id);
      if (!user) return fail(res, 'Không tìm thấy tài khoản.', 401);
      req.user = { id: user.id, role: user.role, username: user.username };
    } catch (error) { return next(error); }
    return next();
  };
}
module.exports = { createAuth };
