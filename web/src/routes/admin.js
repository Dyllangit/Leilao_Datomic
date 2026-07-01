const express = require("express");
const router = express.Router();
const datomic = require("../datomicClient");
const { requireAdmin } = require("../middleware/auth");

router.get("/login", (req, res) => {
  if (req.session.isAdmin) return res.redirect("/admin");
  res.render("admin/login", { erro: null });
});

router.post("/login", (req, res) => {
  const { usuario, senha } = req.body;
  if (usuario === process.env.ADMIN_USER && senha === process.env.ADMIN_PASS) {
    req.session.isAdmin = true;
    return res.redirect("/admin");
  }
  res.render("admin/login", { erro: "Usuario ou senha invalidos." });
});

router.post("/logout", (req, res) => {
  req.session.isAdmin = false;
  res.redirect("/admin/login");
});

router.use(requireAdmin);

router.get("/", async (req, res, next) => {
  try {
    const itens = await datomic.listItems();
    itens.sort((a, b) => a.id - b.id);
    res.render("admin/dashboard", { itens });
  } catch (err) {
    next(err);
  }
});

router.get("/novo", (req, res) => {
  res.render("admin/novo-item", { erro: null });
});

router.post("/itens", async (req, res, next) => {
  const { nome, descricao, imagemUrl, lanceInicial, incrementoMinimo, dataEncerramento } = req.body;
  try {
    let dataEncerramentoIso = null;
    if (dataEncerramento) {
      dataEncerramentoIso = new Date(dataEncerramento).toISOString();
    }
    await datomic.createItem({
      nome,
      descricao,
      imagemUrl,
      lanceInicial,
      incrementoMinimo,
      dataEncerramento: dataEncerramentoIso,
    });
    res.redirect("/admin");
  } catch (err) {
    if (err.status && err.status < 500) {
      return res.render("admin/novo-item", { erro: err.message });
    }
    next(err);
  }
});

router.post("/itens/:id/encerrar", async (req, res, next) => {
  try {
    await datomic.closeItem(req.params.id);
    res.redirect("/admin");
  } catch (err) {
    next(err);
  }
});

module.exports = router;
