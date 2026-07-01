# PRD — Sistema de Leilão Online (Leilao_Datomic)

## 1. Visão Geral

Site de leilão online onde visitantes entram informando apenas um nome, visualizam itens publicados por um administrador e dão lances neles. O administrador tem uma área própria para cadastrar/gerenciar os itens leiloados.

- **Backend:** Node.js (Express)
- **Banco de dados:** Datomic On-Prem (versão free), acessado via **Peer Server** local
- **Front-end:** Server-rendered com Express + EJS (sem SPA)
- **Atualização de lances:** Polling (a cada poucos segundos), sem WebSockets

## 2. Objetivo

Permitir que qualquer pessoa entre com um nome, veja os itens em leilão e dê lances, enquanto um administrador controla quais itens estão disponíveis, seus valores iniciais e o encerramento dos leilões.

## 3. Personas

| Persona | Descrição |
|---|---|
| **Visitante/Licitante** | Entra com um nome (sem senha), navega pelos itens, dá lances |
| **Administrador** | Faz login com usuário/senha fixos, publica/edita/encerra itens de leilão |

## 4. Escopo Funcional

### 4.1 Tela inicial (usuário comum)
- Campo de texto para digitar o nome
- Botão "Entrar"
- Sem senha, sem cadastro prévio — qualquer nome é aceito
- Nome fica salvo na sessão (cookie de sessão) para identificar os lances dados por aquele usuário
- **Regra em aberto:** nomes duplicados serão permitidos (não há verificação de unicidade) — ver seção 8

### 4.2 Listagem de itens em leilão
- Lista todos os itens **ativos** publicados pelo admin
- Cada item mostra: nome, descrição, imagem (opcional), lance atual mais alto, quem deu o último lance, tempo restante (se houver prazo)
- Atualização automática via polling (ex: a cada 5 segundos) para refletir novos lances

### 4.3 Página de detalhe do item / dar lance
- Detalhes completos do item
- Histórico de lances (nome + valor + horário)
- Campo para o usuário logado (pelo nome) inserir um novo lance
- Validação: o lance precisa ser maior que o lance atual (+ incremento mínimo, se definido)
- Feedback imediato de sucesso/erro (ex: "alguém deu um lance maior antes de você")

### 4.4 Área do Administrador
- Tela de login (usuário/senha fixos, via variável de ambiente ou registro único no Datomic)
- CRUD de itens de leilão:
  - Criar item (nome, descrição, imagem, lance inicial, incremento mínimo, data/hora de encerramento)
  - Editar item (antes de receber lances, ou com restrições depois)
  - Encerrar leilão manualmente ou automaticamente ao atingir a data/hora limite
  - Ver histórico completo de lances de cada item
  - Excluir/arquivar item

### 4.5 Encerramento de leilão
- Ao encerrar (manual ou automático por prazo), o item passa para status "encerrado"
- Exibe o vencedor (maior lance) na tela do item
- **Regra em aberto:** notificação ao vencedor — ver seção 8

## 5. Modelo de Dados (Datomic)

Esquema inicial em EDN (a refinar durante implementação):

```clojure
;; Item de leilão
{:db/ident :item/id}
{:db/ident :item/nome :db/valueType :db.type/string :db/cardinality :db.cardinality/one}
{:db/ident :item/descricao :db/valueType :db.type/string :db/cardinality :db.cardinality/one}
{:db/ident :item/imagem-url :db/valueType :db.type/string :db/cardinality :db.cardinality/one}
{:db/ident :item/lance-inicial :db/valueType :db.type/bigdec :db/cardinality :db.cardinality/one}
{:db/ident :item/incremento-minimo :db/valueType :db.type/bigdec :db/cardinality :db.cardinality/one}
{:db/ident :item/status :db/valueType :db.type/keyword :db/cardinality :db.cardinality/one} ;; :ativo :encerrado
{:db/ident :item/data-encerramento :db/valueType :db.type/instant :db/cardinality :db.cardinality/one}

;; Lance
{:db/ident :lance/item :db/valueType :db.type/ref :db/cardinality :db.cardinality/one}
{:db/ident :lance/valor :db/valueType :db.type/bigdec :db/cardinality :db.cardinality/one}
{:db/ident :lance/nome-usuario :db/valueType :db.type/string :db/cardinality :db.cardinality/one}
{:db/ident :lance/data-hora :db/valueType :db.type/instant :db/cardinality :db.cardinality/one}

;; Admin (se necessário persistir, alternativa é usar apenas env vars)
{:db/ident :admin/usuario :db/valueType :db.type/string :db/cardinality :db.cardinality/one :db/unique :db.unique/identity}
{:db/ident :admin/senha-hash :db/valueType :db.type/string :db/cardinality :db.cardinality/one}
```

O uso do Datomic (imutabilidade + histórico) é uma vantagem natural aqui: o histórico de lances de cada item pode ser consultado diretamente via `d/history`/`d/as-of`, sem precisar de uma entidade "log" separada.

## 6. Arquitetura Técnica

```
[Navegador] <--HTTP/EJS--> [Node.js/Express] <--HTTP (Peer Server)--> [Datomic On-Prem Peer Server] <--> [Storage (dev-local ou outro)]
```

- **Datomic não possui driver oficial para Node.js/JavaScript.** A integração será feita via **Datomic Peer Server**, que expõe uma API HTTP (protocolo do Datomic Client API) para o backend Node.js consumir usando requisições HTTP simples (fetch/axios), enviando queries em EDN.
- Isso exige rodar, junto com o projeto:
  1. **Datomic transactor** (armazenamento dev-local, sem custo)
  2. **Datomic Peer Server** apontando para esse transactor
  3. Backend Node.js/Express se conecta ao Peer Server via HTTP
- Sessão do usuário (nome) e do admin (autenticado) via `express-session` (cookie).
- Polling: front-end faz `fetch` a cada N segundos para buscar lances/itens atualizados e re-renderiza a parte relevante (ou faz refresh de fragmento via JS simples).

## 7. Fora de Escopo (v1)

- Pagamentos/processamento financeiro
- Notificações por e-mail/SMS
- Upload de imagens (assume-se URL de imagem, não upload de arquivo)
- Múltiplos administradores com cadastro próprio
- Aplicativo mobile
- Internacionalização (apenas português)

## 8. Perguntas em Aberto

Estas decisões ainda precisam ser tomadas antes ou durante a implementação:

1. **Nomes duplicados:** ok permitir dois usuários com o mesmo nome simultaneamente (sem controle de unicidade), como confirmado? Isso significa que não há garantia de que "Fulano" dando um lance é sempre a mesma pessoa.
2. **Incremento mínimo de lance:** cada item pode ter seu próprio incremento mínimo, ou um valor fixo global (ex: sempre 5% acima do lance atual)?
3. **Encerramento automático:** os itens têm data/hora de encerramento obrigatória, ou o admin pode criar itens sem prazo e encerrar manualmente quando quiser?
4. **Notificação de vencedor:** apenas exibir na tela quem venceu é suficiente para v1, ou é necessário algum aviso (mesmo que só visual/destaque)?
5. **Persistência da sessão do admin:** credenciais fixas via variável de ambiente (mais simples) são suficientes, ou prefere que fiquem no próprio Datomic?
6. **Ambiente de execução do Datomic:** já tem o Datomic On-Prem baixado/licenciado (a versão free exige cadastro em my.datomic.com), ou preciso te orientar nesse setup do zero?

## 9. Critérios de Sucesso (v1)

- Usuário consegue entrar só com nome e ver a lista de itens ativos
- Usuário consegue dar um lance válido e vê-lo refletido na tela (via polling) em poucos segundos
- Sistema rejeita lances menores/iguais ao lance atual
- Admin consegue logar, criar um item e vê-lo aparecer na listagem pública
- Admin consegue encerrar um leilão e o vencedor é exibido corretamente
- Histórico de lances de um item é consultável (aproveitando o histórico imutável do Datomic)