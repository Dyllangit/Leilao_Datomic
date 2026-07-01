const express = require("express");
const router = express.Router();
const datomic = require("../datomicClient");
const { requireUser } = require("../middleware/auth");

router.use(requireUser);

router.get("/", async (req, res, next) => {
  try {
    const itens = await datomic.listItems();
    itens.sort((a, b) => a.id - b.id);
    res.render("itens", { itens, nomeUsuario: req.session.nomeUsuario });
  } catch (err) {
    next(err);
  }
});

// Usado pelo polling em JS no front-end para atualizar a lista sem reload.
router.get("/estado.json", async (req, res, next) => {
  try {
    const itens = await datomic.listItems();
    res.json(itens);
  } catch (err) {
    res.status(503).json({ error: "bridge indisponivel" });
  }
});

router.get("/:id", async (req, res, next) => {
  try {
    const item = await datomic.getItem(req.params.id);
    res.render("item", {
      item,
      nomeUsuario: req.session.nomeUsuario,
      erro: req.query.erro || null,
      ok: req.query.ok === "1",
    });
  } catch (err) {
    if (err.status === 404) return res.status(404).render("item-nao-encontrado");
    next(err);
  }
});

// Usado pelo polling na pagina de detalhe do item.
router.get("/:id/estado.json", async (req, res) => {
  try {
    const item = await datomic.getItem(req.params.id);
    res.json(item);
  } catch (err) {
    res.status(err.status || 503).json({ error: err.message });
  }
});

router.post("/:id/lances", async (req, res, next) => {
  const { id } = req.params;
  try {
    await datomic.placeBid(id, {
      nomeUsuario: req.session.nomeUsuario,
      valor: req.body.valor,
    });
    res.redirect(`/itens/${id}?ok=1`);
  } catch (err) {
    if (err.status && err.status < 500) {
      return res.redirect(`/itens/${id}?erro=${encodeURIComponent(err.message)}`);
    }
    next(err);
  }
});

module.exports = router;
