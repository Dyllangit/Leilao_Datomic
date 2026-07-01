const BRIDGE_URL = process.env.BRIDGE_URL || "http://localhost:8890";

class BridgeError extends Error {
  constructor(status, message) {
    super(message);
    this.status = status;
  }
}

async function call(method, path, body) {
  let res;
  try {
    res = await fetch(`${BRIDGE_URL}${path}`, {
      method,
      headers: body ? { "Content-Type": "application/json" } : undefined,
      body: body ? JSON.stringify(body) : undefined,
    });
  } catch (err) {
    throw new BridgeError(503, "Nao foi possivel conectar ao servidor de dados (bridge/Datomic). Verifique se ele esta rodando.");
  }

  const text = await res.text();
  const data = text ? JSON.parse(text) : null;

  if (!res.ok) {
    throw new BridgeError(res.status, (data && data.error) || `Erro ${res.status}`);
  }
  return data;
}

const listItems = () => call("GET", "/items");
const getItem = (id) => call("GET", `/items/${id}`);
const createItem = (data) => call("POST", "/items", data);
const closeItem = (id) => call("POST", `/items/${id}/encerrar`);
const placeBid = (id, data) => call("POST", `/items/${id}/lances`, data);

module.exports = { BridgeError, listItems, getItem, createItem, closeItem, placeBid };
