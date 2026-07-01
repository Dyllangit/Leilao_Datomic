(function () {
  const INTERVALO_MS = 5000;

  async function atualizar() {
    let itens;
    try {
      const res = await fetch("/itens/estado.json");
      if (!res.ok) return;
      itens = await res.json();
    } catch (e) {
      return;
    }

    itens.forEach(function (item) {
      const cartao = document.querySelector('.cartao-item[data-id="' + item.id + '"]');
      if (!cartao) return;

      const valorEl = cartao.querySelector(".valor-lance-atual");
      if (valorEl) valorEl.textContent = "R$ " + item.lanceAtual;

      const nomeEl = cartao.querySelector(".nome-lance-atual");
      if (nomeEl) nomeEl.textContent = item.lanceAtualNome ? "por " + item.lanceAtualNome : "nenhum lance ainda";

      const statusEl = cartao.querySelector(".status");
      if (statusEl) {
        statusEl.textContent = item.status === "ativo" ? "Ativo" : "Encerrado";
        statusEl.className = "status status-" + item.status;
      }
    });
  }

  setInterval(atualizar, INTERVALO_MS);
})();
