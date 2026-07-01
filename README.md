# Leilao_Datomic

Sistema de leilao online. Usuarios entram so com um nome e dao lances nos itens publicados por um administrador. Veja o [PRD.md](PRD.md) para o detalhamento do produto.

**No ar:** https://leilao-web-production.up.railway.app (deploy no Railway, veja [DEPLOY.md](DEPLOY.md) para detalhes da infra e como redeployar).

## Arquitetura

```
Navegador --HTTP/EJS--> Node.js/Express (web/) --HTTP/JSON--> Bridge Java (bridge/) --Peer API--> Datomic Transactor (datomic/)
```

O Datomic Pro nao tem driver oficial para Node.js. Por isso existe uma pequena camada em Java (`bridge/`), usando a biblioteca Peer que acompanha o Datomic, que fala com o transactor e expoe tudo como uma API HTTP/JSON simples para o backend Node.js consumir.

- `datomic/` - distribuicao do Datomic Pro (extraida do zip baixado em my.datomic.com, nao versionada no git)
- `config/transactor.properties` - configuracao do transactor (storage `dev`, sem custo, dados em `datomic-data/`)
- `bridge/` - servico Java que conversa com o Datomic via Peer API e expoe HTTP/JSON na porta 8890
- `web/` - aplicacao Node.js/Express + EJS (front-end e back-end), porta 3000

## Pre-requisitos

- Java 11+ (`java -version`)
- Node.js 18+ (`node --version`)
- Datomic Pro (On-Prem, versao free) extraido na pasta `datomic/` na raiz do projeto. Baixe em https://my.datomic.com (necessita cadastro gratuito) e extraia o zip de forma que exista `datomic/bin/transactor.cmd`.

## Como rodar (3 processos, cada um em um terminal)

1. **Transactor Datomic**
   ```
   scripts\1-iniciar-transactor.cmd
   ```
   Espere aparecer `System started`.

2. **Bridge Java** (primeira vez, compile antes)
   ```
   scripts\2-compilar-bridge.cmd
   scripts\3-iniciar-bridge.cmd
   ```
   Espere aparecer `Bridge Datomic ouvindo em http://localhost:8890`. O schema do banco (itens/lances) e instalado automaticamente na primeira vez que o bridge sobe.

3. **Site Node.js**
   ```
   scripts\4-iniciar-site.cmd
   ```
   Acesse http://localhost:3000

## Configuracao

As variaveis de ambiente do site ficam em `web/.env` (copie de `web/.env.example` se precisar recriar):

- `PORT` - porta do site (padrao 3000)
- `BRIDGE_URL` - endereco do bridge Java (padrao http://localhost:8890)
- `SESSION_SECRET` - segredo da sessao (troque em producao)
- `ADMIN_USER` / `ADMIN_PASS` - credenciais fixas da area do administrador

Login do admin padrao: usuario `admin`, senha `admin123` (definido em `web/.env`).

## Fluxo do usuario

1. Acessa `/`, digita um nome (sem senha) e entra.
2. Ve a lista de itens ativos em `/itens`, que se atualiza sozinha a cada poucos segundos (polling).
3. Abre um item, ve o historico de lances e da um lance (precisa ser maior ou igual ao lance atual + incremento minimo do item).

## Fluxo do administrador

1. Acessa `/admin/login` e entra com usuario/senha fixos.
2. Em `/admin`, ve todos os itens, pode encerrar leiloes ativos e ver o vencedor.
3. Em `/admin/novo`, publica um novo item (nome, descricao, imagem opcional, lance inicial, incremento minimo, data de encerramento opcional).

## Notas tecnicas

- Itens com data de encerramento vencida sao automaticamente marcados como encerrados pelo bridge na proxima leitura/lance.
- O historico completo de lances de cada item fica gravado como fatos imutaveis no Datomic (entidades `:lance/...` referenciando o item via `:lance/item`).
