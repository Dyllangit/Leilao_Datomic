const express = require("express");
const router = express.Router();

router.get("/", (req, res) => {
  if (req.session.nomeUsuario) {
    return res.redirect("/itens");
  }
  res.render("entrada", { erro: null });
});

router.post("/entrar", (req, res) => {
  const nome = (req.body.nome || "").trim();
  if (!nome) {
    return res.render("entrada", { erro: "Digite um nome para entrar." });
  }
  req.session.nomeUsuario = nome;
  res.redirect("/itens");
});

router.post("/sair", (req, res) => {
  req.session.destroy(() => res.redirect("/"));
});

module.exports = router;
