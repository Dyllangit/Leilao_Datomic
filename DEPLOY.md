# Deploy no Railway

O projeto esta publicado no Railway (projeto `leilao-datomic`) com 3 servicos + 1 volume, na mesma arquitetura do ambiente local:

```
leilao-web (publico) --> datomic-bridge (privado) --> datomic-transactor (privado, com volume /data)
```

URL publica: https://leilao-web-production.up.railway.app

## Servicos

| Servico | Root Directory | O que roda | Rede |
|---|---|---|---|
| `datomic-transactor` | `datomic-deploy/` | Transactor Datomic (storage `dev`, dados em volume `/data`) | privado (`datomic-transactor.railway.internal:4334`) |
| `datomic-bridge` | `bridge/` | Ponte Java (Peer API -> HTTP/JSON) | privado (`datomic-bridge.railway.internal:8890`) |
| `leilao-web` | `web/` | Site Node.js/Express | publico (dominio Railway gerado) |

## Por que existe `datomic-deploy/` e `bridge/vendor/`

Essas pastas sao **geradas**, nao editadas a mao (estao no `.gitignore`):

- `datomic-deploy/` = copia de `datomic/` sem os drivers de Cassandra/DynamoDB (nao usados pelo storage `dev`), para caber no limite de upload do `railway up`. Regenerar com `scripts\prepare-datomic-deploy.cmd` apos atualizar o Datomic.
- `bridge/vendor/` = copia de `datomic/lib` + `peer-*.jar` dentro de `bridge/`, para o Root Directory `bridge/` ser um contexto de build autossuficiente (Docker precisa que tudo esteja dentro do contexto). Regenerar com `scripts\prepare-bridge-vendor.cmd`.

## Variaveis de ambiente configuradas

**datomic-transactor**
- `STORAGE_DATOMIC_PASSWORD` - senha para permitir que peers remotos (o bridge, em outro container) se conectem ao storage H2 embutido. Sem isso, o Datomic bloqueia conexoes remotas (`storage-access=local` e o padrao).
- `RAILWAY_PRIVATE_DOMAIN` - injetada automaticamente pelo Railway, usada pelo `entrypoint.sh` como `host` anunciado aos peers.

**datomic-bridge**
- `DATOMIC_URI=datomic:dev://datomic-transactor.railway.internal:4334/leilao?password=<STORAGE_DATOMIC_PASSWORD>`
  (a senha vai como **query param** `?password=`, nao como `usuario:senha@host` - esse segundo formato quebra o parser de URI do Datomic)

**leilao-web**
- `BRIDGE_URL=http://datomic-bridge.railway.internal:8890`
- `SESSION_SECRET` - string aleatoria
- `ADMIN_USER` / `ADMIN_PASS` - **trocar `ADMIN_PASS` do valor de teste `admin123` antes de divulgar o link**

## Redeploy apos alterar codigo

```
# transactor (raro precisar; so muda se editar datomic/entrypoint.sh)
scripts\prepare-datomic-deploy.cmd
cd datomic-deploy
railway up . --path-as-root --no-gitignore --service datomic-transactor -e production -y -c

# bridge (apos editar bridge/src/Bridge.java)
scripts\prepare-bridge-vendor.cmd
cd bridge
railway up . --path-as-root --no-gitignore --service datomic-bridge -e production -y -c

# site (apos editar algo em web/)
cd web
railway up . --path-as-root --service leilao-web -e production -y -c
```

Observacoes sobre o CLI do Railway (v5.23):
- `railway up` as vezes perde a conexao ao fazer streaming dos logs de build (`reqwest error ... operation timed out`) mesmo quando o build continua normalmente no servidor. Confirme o resultado real com `railway deployment list --service <nome> -e production --json` e `railway logs --service <nome> -e production --deployment`.
- `railway volume add --service <nome> ...` trava (bug conhecido nesta versao) quando usa o **nome** do servico/ambiente. Funciona passando os **IDs** (UUID) de servico/ambiente/projeto em vez do nome.
- No Git Bash (MSYS), argumentos tipo `/data` sao reescritos como caminho do Windows. Use `MSYS_NO_PATHCONV=1` antes do comando `railway` quando passar `--mount-path /data` ou similares.

## Seguranca

- Troque `ADMIN_PASS` (ainda esta `admin123`, valor de teste) via `railway variable set ADMIN_PASS=<nova senha> --service leilao-web -e production`.
- `datomic-transactor` e `datomic-bridge` nao tem dominio publico (so rede privada Railway) - nao exponha dominio para eles.
