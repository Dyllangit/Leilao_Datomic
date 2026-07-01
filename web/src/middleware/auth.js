function requireUser(req, res, next) {
  if (!req.session.nomeUsuario) {
    return res.redirect("/");
  }
  next();
}

function requireAdmin(req, res, next) {
  if (!req.session.isAdmin) {
    return res.redirect("/admin/login");
  }
  next();
}

module.exports = { requireUser, requireAdmin };
