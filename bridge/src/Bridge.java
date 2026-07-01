import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import clojure.lang.Keyword;
import datomic.Connection;
import datomic.Database;
import datomic.Entity;
import datomic.Peer;
import datomic.Util;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;
import org.json.simple.parser.JSONParser;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;

/**
 * Ponte HTTP/JSON entre o Node.js e o Datomic Pro (via biblioteca Peer).
 * Datomic On-Prem nao possui driver oficial para Node.js, entao este
 * processo Java roda ao lado do transactor e expoe as operacoes do
 * leilao (itens, lances) como uma API REST simples em JSON.
 */
public class Bridge {

    static Connection conn;
    static final Object BID_LOCK = new Object();

    public static void main(String[] args) throws Exception {
        String dbUri = envOr("DATOMIC_URI", "datomic:dev://localhost:4334/leilao");
        Peer.createDatabase(dbUri);
        conn = Peer.connect(dbUri);
        installSchema();

        int port = Integer.parseInt(envOr("BRIDGE_PORT", "8890"));
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.setExecutor(Executors.newFixedThreadPool(8));
        server.createContext("/", Bridge::handle);
        server.start();
        System.out.println("Bridge Datomic ouvindo em http://localhost:" + port);
    }

    static String envOr(String name, String def) {
        String v = System.getenv(name);
        return (v == null || v.isEmpty()) ? def : v;
    }

    // ---------------------------------------------------------------
    // Schema
    // ---------------------------------------------------------------

    static void installSchema() throws Exception {
        String edn = "["
                + "{:db/ident :item/nome :db/valueType :db.type/string :db/cardinality :db.cardinality/one}"
                + "{:db/ident :item/descricao :db/valueType :db.type/string :db/cardinality :db.cardinality/one}"
                + "{:db/ident :item/imagem-url :db/valueType :db.type/string :db/cardinality :db.cardinality/one}"
                + "{:db/ident :item/lance-inicial :db/valueType :db.type/bigdec :db/cardinality :db.cardinality/one}"
                + "{:db/ident :item/incremento-minimo :db/valueType :db.type/bigdec :db/cardinality :db.cardinality/one}"
                + "{:db/ident :item/status :db/valueType :db.type/keyword :db/cardinality :db.cardinality/one}"
                + "{:db/ident :item/data-encerramento :db/valueType :db.type/instant :db/cardinality :db.cardinality/one}"
                + "{:db/ident :lance/item :db/valueType :db.type/ref :db/cardinality :db.cardinality/one}"
                + "{:db/ident :lance/valor :db/valueType :db.type/bigdec :db/cardinality :db.cardinality/one}"
                + "{:db/ident :lance/nome-usuario :db/valueType :db.type/string :db/cardinality :db.cardinality/one}"
                + "{:db/ident :lance/data-hora :db/valueType :db.type/instant :db/cardinality :db.cardinality/one}"
                + "]";
        List txData = (List) Util.read(edn);
        conn.transact(txData).get();
    }

    static Keyword kw(String full) {
        int slash = full.indexOf('/');
        if (slash < 0) return Keyword.intern(full);
        return Keyword.intern(full.substring(0, slash), full.substring(slash + 1));
    }

    // ---------------------------------------------------------------
    // Roteamento
    // ---------------------------------------------------------------

    static void handle(HttpExchange exchange) {
        try {
            route(exchange);
        } catch (Exception ex) {
            ex.printStackTrace();
            try {
                sendJson(exchange, 500, err("erro interno: " + ex.getMessage()));
            } catch (IOException ignored) {
            }
        }
    }

    static void route(HttpExchange exchange) throws Exception {
        String method = exchange.getRequestMethod();
        String path = exchange.getRequestURI().getPath();
        String[] parts = path.split("/");
        // parts[0] == "" ; parts[1] == "items" ; parts[2] == id ; parts[3] == acao

        if (path.equals("/health")) {
            sendJson(exchange, 200, ok());
            return;
        }

        if (parts.length >= 2 && parts[1].equals("items")) {
            if (parts.length == 2) {
                if (method.equals("GET")) { listItems(exchange); return; }
                if (method.equals("POST")) { createItem(exchange); return; }
            } else if (parts.length == 3) {
                long id;
                try { id = Long.parseLong(parts[2]); } catch (NumberFormatException nfe) { sendJson(exchange, 400, err("id invalido")); return; }
                if (method.equals("GET")) { getItem(exchange, id); return; }
            } else if (parts.length == 4) {
                long id;
                try { id = Long.parseLong(parts[2]); } catch (NumberFormatException nfe) { sendJson(exchange, 400, err("id invalido")); return; }
                String action = parts[3];
                if (method.equals("POST") && action.equals("lances")) { placeBid(exchange, id); return; }
                if (method.equals("POST") && action.equals("encerrar")) { closeItem(exchange, id); return; }
            }
        }

        sendJson(exchange, 404, err("rota nao encontrada"));
    }

    // ---------------------------------------------------------------
    // Handlers
    // ---------------------------------------------------------------

    static void listItems(HttpExchange exchange) throws Exception {
        Collection<List<Object>> ids = Peer.q("[:find ?e :where [?e :item/nome]]", conn.db());
        List<Long> eids = new ArrayList<>();
        for (List<Object> row : ids) eids.add(((Number) row.get(0)).longValue());
        for (Long eid : eids) maybeExpire(eid);

        Database db = conn.db();
        Collections.sort(eids);
        JSONArray arr = new JSONArray();
        for (Long eid : eids) arr.add(itemToJson(db, eid));
        sendJson(exchange, 200, arr);
    }

    static void getItem(HttpExchange exchange, long eid) throws Exception {
        maybeExpire(eid);
        Database db = conn.db();
        Entity e = db.entity(eid);
        if (e.get(kw("item/nome")) == null) { sendJson(exchange, 404, err("item nao encontrado")); return; }

        JSONObject o = itemToJson(db, eid);

        Collection<List<Object>> rows = Peer.q(
                "[:find ?v ?n ?d :in $ ?item :where [?l :lance/item ?item] [?l :lance/valor ?v] [?l :lance/nome-usuario ?n] [?l :lance/data-hora ?d]]",
                db, eid);
        List<List<Object>> sorted = new ArrayList<>(rows);
        sorted.sort((a, b) -> ((Date) b.get(2)).compareTo((Date) a.get(2)));

        JSONArray lancesArr = new JSONArray();
        for (List<Object> row : sorted) {
            JSONObject l = new JSONObject();
            l.put("nomeUsuario", row.get(1));
            l.put("valor", row.get(0));
            l.put("dataHora", ((Date) row.get(2)).toInstant().toString());
            lancesArr.add(l);
        }
        o.put("lances", lancesArr);
        sendJson(exchange, 200, o);
    }

    static void createItem(HttpExchange exchange) throws Exception {
        JSONObject body = readJsonBody(exchange);

        String nome = strOrNull(body.get("nome"));
        if (nome == null || nome.trim().isEmpty()) { sendJson(exchange, 400, err("nome e obrigatorio")); return; }

        String descricao = strOrNull(body.get("descricao"));
        String imagemUrl = strOrNull(body.get("imagemUrl"));

        BigDecimal lanceInicial;
        try { lanceInicial = toBigDecimal(body.get("lanceInicial")); }
        catch (Exception ex) { sendJson(exchange, 400, err("lanceInicial invalido")); return; }

        BigDecimal incremento;
        try { incremento = body.get("incrementoMinimo") == null ? BigDecimal.ZERO : toBigDecimal(body.get("incrementoMinimo")); }
        catch (Exception ex) { sendJson(exchange, 400, err("incrementoMinimo invalido")); return; }

        Date dataEncerramento = null;
        String dataStr = strOrNull(body.get("dataEncerramento"));
        if (dataStr != null && !dataStr.trim().isEmpty()) {
            try { dataEncerramento = Date.from(Instant.parse(dataStr)); }
            catch (Exception ex) { sendJson(exchange, 400, err("dataEncerramento invalida (use formato ISO 8601)")); return; }
        }

        Object tempId = Peer.tempid(kw("db.part/user"));
        List<Object> kvs = new ArrayList<>(Arrays.asList(
                kw("db/id"), tempId,
                kw("item/nome"), nome.trim(),
                kw("item/descricao"), descricao == null ? "" : descricao,
                kw("item/imagem-url"), imagemUrl == null ? "" : imagemUrl,
                kw("item/lance-inicial"), lanceInicial,
                kw("item/incremento-minimo"), incremento,
                kw("item/status"), kw("status/ativo")
        ));
        if (dataEncerramento != null) { kvs.add(kw("item/data-encerramento")); kvs.add(dataEncerramento); }

        List txData = Util.list(Util.map(kvs.toArray()));
        Map txResult = conn.transact(txData).get();
        Database dbAfter = (Database) txResult.get(Connection.DB_AFTER);
        Object tempids = txResult.get(Connection.TEMPIDS);
        long eid = ((Number) Peer.resolveTempid(dbAfter, tempids, tempId)).longValue();

        sendJson(exchange, 201, itemToJson(dbAfter, eid));
    }

    static void closeItem(HttpExchange exchange, long eid) throws Exception {
        Database db = conn.db();
        Entity e = db.entity(eid);
        if (e.get(kw("item/nome")) == null) { sendJson(exchange, 404, err("item nao encontrado")); return; }

        List txData = Util.list(Util.map(kw("db/id"), eid, kw("item/status"), kw("status/encerrado")));
        conn.transact(txData).get();
        sendJson(exchange, 200, itemToJson(conn.db(), eid));
    }

    static void placeBid(HttpExchange exchange, long eid) throws Exception {
        JSONObject body = readJsonBody(exchange);
        String nomeUsuario = strOrNull(body.get("nomeUsuario"));
        Object valorObj = body.get("valor");
        if (nomeUsuario == null || nomeUsuario.trim().isEmpty() || valorObj == null) {
            sendJson(exchange, 400, err("nomeUsuario e valor sao obrigatorios"));
            return;
        }
        BigDecimal valor;
        try { valor = toBigDecimal(valorObj); }
        catch (Exception ex) { sendJson(exchange, 400, err("valor invalido")); return; }

        synchronized (BID_LOCK) {
            maybeExpire(eid);
            Database db = conn.db();
            Entity e = db.entity(eid);
            if (e.get(kw("item/nome")) == null) { sendJson(exchange, 404, err("item nao encontrado")); return; }

            Object statusKw = e.get(kw("item/status"));
            if (!"ativo".equals(((Keyword) statusKw).getName())) {
                sendJson(exchange, 409, err("leilao encerrado"));
                return;
            }

            BigDecimal lanceInicial = (BigDecimal) e.get(kw("item/lance-inicial"));
            BigDecimal incremento = (BigDecimal) e.get(kw("item/incremento-minimo"));
            if (incremento == null) incremento = BigDecimal.ZERO;

            BigDecimal maiorAtual = null;
            Collection<List<Object>> rows = Peer.q(
                    "[:find ?v :in $ ?item :where [?l :lance/item ?item] [?l :lance/valor ?v]]", db, eid);
            for (List<Object> row : rows) {
                BigDecimal v = (BigDecimal) row.get(0);
                if (maiorAtual == null || v.compareTo(maiorAtual) > 0) maiorAtual = v;
            }
            BigDecimal minimo = maiorAtual == null ? lanceInicial : maiorAtual.add(incremento);

            if (valor.compareTo(minimo) < 0) {
                sendJson(exchange, 409, err("o lance deve ser de pelo menos " + minimo.toPlainString()));
                return;
            }

            Object tempId = Peer.tempid(kw("db.part/user"));
            List txData = Util.list(Util.map(
                    kw("db/id"), tempId,
                    kw("lance/item"), eid,
                    kw("lance/valor"), valor,
                    kw("lance/nome-usuario"), nomeUsuario.trim(),
                    kw("lance/data-hora"), new Date()
            ));
            conn.transact(txData).get();
            sendJson(exchange, 201, itemToJson(conn.db(), eid));
        }
    }

    // ---------------------------------------------------------------
    // Auxiliares de dominio
    // ---------------------------------------------------------------

    static void maybeExpire(long eid) throws Exception {
        Database db = conn.db();
        Entity e = db.entity(eid);
        Object statusKw = e.get(kw("item/status"));
        Object dataEnc = e.get(kw("item/data-encerramento"));
        if (statusKw != null && "ativo".equals(((Keyword) statusKw).getName()) && dataEnc != null) {
            if (((Date) dataEnc).before(new Date())) {
                List txData = Util.list(Util.map(kw("db/id"), eid, kw("item/status"), kw("status/encerrado")));
                conn.transact(txData).get();
            }
        }
    }

    static JSONObject itemToJson(Database db, long eid) {
        Entity e = db.entity(eid);
        JSONObject o = new JSONObject();
        o.put("id", eid);
        o.put("nome", e.get(kw("item/nome")));
        o.put("descricao", e.get(kw("item/descricao")));
        o.put("imagemUrl", e.get(kw("item/imagem-url")));

        BigDecimal lanceInicial = (BigDecimal) e.get(kw("item/lance-inicial"));
        o.put("lanceInicial", lanceInicial);
        o.put("incrementoMinimo", e.get(kw("item/incremento-minimo")));

        Object statusKw = e.get(kw("item/status"));
        o.put("status", statusKw == null ? "ativo" : ((Keyword) statusKw).getName());

        Object dataEnc = e.get(kw("item/data-encerramento"));
        o.put("dataEncerramento", dataEnc == null ? null : ((Date) dataEnc).toInstant().toString());

        Collection<List<Object>> rows = Peer.q(
                "[:find ?v ?n :in $ ?item :where [?l :lance/item ?item] [?l :lance/valor ?v] [?l :lance/nome-usuario ?n]]",
                db, eid);
        BigDecimal melhorValor = null;
        String melhorNome = null;
        int total = 0;
        for (List<Object> row : rows) {
            total++;
            BigDecimal v = (BigDecimal) row.get(0);
            if (melhorValor == null || v.compareTo(melhorValor) > 0) {
                melhorValor = v;
                melhorNome = (String) row.get(1);
            }
        }
        o.put("lanceAtual", melhorValor == null ? lanceInicial : melhorValor);
        o.put("lanceAtualNome", melhorNome);
        o.put("totalLances", total);
        return o;
    }

    // ---------------------------------------------------------------
    // JSON / HTTP utilitarios
    // ---------------------------------------------------------------

    static JSONObject readJsonBody(HttpExchange exchange) throws IOException {
        byte[] bytes = exchange.getRequestBody().readAllBytes();
        if (bytes.length == 0) return new JSONObject();
        String s = new String(bytes, StandardCharsets.UTF_8);
        try {
            Object parsed = new JSONParser().parse(s);
            return parsed instanceof JSONObject ? (JSONObject) parsed : new JSONObject();
        } catch (Exception pe) {
            return new JSONObject();
        }
    }

    static void sendJson(HttpExchange exchange, int status, Object payload) throws IOException {
        byte[] bytes = JSONValue.toJSONString(payload).getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    static JSONObject err(String msg) {
        JSONObject o = new JSONObject();
        o.put("error", msg);
        return o;
    }

    static JSONObject ok() {
        JSONObject o = new JSONObject();
        o.put("status", "ok");
        return o;
    }

    static BigDecimal toBigDecimal(Object numObj) {
        if (numObj instanceof BigDecimal) return (BigDecimal) numObj;
        return new BigDecimal(numObj.toString().trim());
    }

    static String strOrNull(Object o) {
        return o == null ? null : o.toString();
    }
}
