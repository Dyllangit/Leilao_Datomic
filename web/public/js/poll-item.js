(function () {
  const INTERVALO_MS = 4000;
  const container = document.querySelector(".conteudo[data-item-id]");
  if (!container) return;
  const itemId = container.getAttribute("data-item-id");

  function formatarDataHora(iso) {
    return new Date(iso).toLocaleString("pt-BR");
  }

  async function atualizar() {
    let item;
    try {
      const res = await fetch("/itens/" + itemId + "/estado.json");
      if (!res.ok) return;
      item = await res.json();
    } catch (e) {
      return;
    }

    document.getElementById("valor-lance-atual").textContent = "R$ " + item.lanceAtual;
    document.getElementById("lance-atual-nome").textContent = item.lanceAtualNome ? "(por " + item.lanceAtualNome + ")" : "";

    const statusEl = document.getElementById("status-item");
    statusEl.textContent = item.status === "ativo" ? "Leilao ativo" : "Leilao encerrado";
    statusEl.className = "status status-" + item.status;

    const form = document.getElementById("form-lance");
    const aviso = document.getElementById("leilao-encerrado-aviso");
    if (item.status !== "ativo") {
      form.classList.add("oculto");
      aviso.classList.remove("oculto");
    }

    const lista = document.getElementById("lista-lances");
    lista.innerHTML = "";
    if (item.lances.length === 0) {
      lista.innerHTML = '<li class="sem-lances">Nenhum lance ainda.</li>';
    } else {
      item.lances.forEach(function (l) {
        const li = document.createElement("li");
        li.innerHTML = "<strong></strong> - R$ " + l.valor + " - " + formatarDataHora(l.dataHora);
        li.querySelector("strong").textContent = l.nomeUsuario;
        lista.appendChild(li);
      });
    }
  }

  setInterval(atualizar, INTERVALO_MS);
})();
