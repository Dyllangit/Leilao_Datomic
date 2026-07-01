require("dotenv").config();
const express = require("express");
const session = require("express-session");
const path = require("path");

const indexRoutes = require("./src/routes/index");
const itensRoutes = require("./src/routes/itens");
const adminRoutes = require("./src/routes/admin");

const app = express();

app.set("view engine", "ejs");
app.set("views", path.join(__dirname, "views"));

app.use(express.urlencoded({ extended: false }));
app.use(express.json());
app.use(express.static(path.join(__dirname, "public")));

app.use(
  session({
    secret: process.env.SESSION_SECRET || "dev-secret",
    resave: false,
    saveUninitialized: false,
    cookie: { maxAge: 1000 * 60 * 60 * 4 },
  })
);

app.use((req, res, next) => {
  res.locals.nomeUsuario = req.session.nomeUsuario || null;
  res.locals.isAdmin = !!req.session.isAdmin;
  next();
});

app.use("/", indexRoutes);
app.use("/itens", itensRoutes);
app.use("/admin", adminRoutes);

app.use((req, res) => {
  res.status(404).render("nao-encontrado");
});

app.use((err, req, res, next) => {
  console.error(err);
  res.status(err.status && err.status < 500 ? err.status : 500).render("erro", {
    mensagem: err.message || "Erro inesperado",
  });
});

const PORT = process.env.PORT || 3000;
app.listen(PORT, () => {
  console.log(`Servidor do leilao ouvindo em http://localhost:${PORT}`);
});
