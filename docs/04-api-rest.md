# 04. API REST

> **Resumo:** recursos com substantivos no plural, versionamento na URL (`/api/v1`), verbos e
> status HTTP com a semântica da RFC 9110, transições de estado como *controller resources*
> (`POST /shipments/{id}/cancel`), paginação com teto, e **todo** erro no formato RFC 9457
> (Problem Details) com um `code` estável.

## Nível de maturidade

O modelo de Richardson classifica APIs em níveis:

| Nível | O que exige | Aqui |
|---|---|---|
| 0 | um endpoint, tudo via POST (estilo RPC/SOAP) | — |
| 1 | recursos com URLs próprias | ✅ |
| 2 | verbos HTTP e status codes com significado | ✅ |
| 3 | HATEOAS: links na resposta guiam o cliente | ❌ (evolução opcional) |

O nível 2 é o padrão de mercado. O nível 3 é raro na prática porque clientes quase nunca navegam
por links dinamicamente; pode valer em APIs públicas de longa duração.

## Convenções

| Convenção | Exemplo | Por quê |
|---|---|---|
| Substantivos no plural | `/shipments`, `/users` | a URL nomeia um recurso/coleção, o verbo HTTP diz a ação |
| Versão na URL | `/api/v1/...` | explícita, fácil de rotear e testar (o Spring 7 também suporta versão por header) |
| JSON em camelCase | `weightGrams`, `createdAt` | convenção do ecossistema JavaScript, maior consumidor de APIs |
| Datas ISO-8601 em UTC | `2026-01-15T10:00:00Z` | sem ambiguidade de fuso; o cliente converte para exibição |
| Dinheiro com moeda | `{"amount": 30.00, "currency": "BRL"}` | valor sem moeda é ambíguo |
| IDs como UUID | `3f2a...` | não enumeráveis e gerados pela aplicação |
| `Location` relativo no 201 | `/api/v1/shipments/{id}` | permitido pela RFC 9110 e correto atrás de proxies |

## Verbos e status codes

| Situação | Status | Exemplo |
|---|---|---|
| Criação | **201 Created** + `Location` | `POST /users`, `POST /shipments` |
| Consulta/ação com corpo de resposta | **200 OK** | `GET /shipments/{id}`, `POST /{id}/dispatch` |
| Formato inválido | **400 Bad Request** | CEP com 7 dígitos, JSON quebrado, `size=500` |
| Sem token / token inválido | **401 Unauthorized** + `WWW-Authenticate` | requisição sem `Authorization` |
| Autenticado, sem permissão | **403 Forbidden** | cliente tentando despachar |
| Não existe **ou não é seu** | **404 Not Found** | envio de outro cliente (anti-IDOR) |
| Conflito com o estado atual | **409 Conflict** | cancelar envio entregue; e-mail já cadastrado; edição concorrente |
| Bem formado, mas impossível | **422 Unprocessable Content** | CEP com formato válido que não existe |
| Erro nosso | **500 Internal Server Error** | bug; resposta genérica, detalhe só no log |
| Dependência fora | **503 Service Unavailable** + `Retry-After` | ViaCEP indisponível |

### Por que 404 (e não 403) para o recurso de outra pessoa?

Responder 403 confirma que o ID existe. A OWASP lista "Broken Object Level Authorization" como
o risco número 1 em APIs (OWASP API Security Top 10, API1:2023). Com 404, o recurso alheio é
indistinguível de um inexistente, e para o cliente legítimo não faz diferença.

### 400 x 422

- **400**: a requisição está malformada (sintaxe, tipo, formato). Repetir sem corrigir dá o mesmo
  erro.
- **422**: a requisição está bem formada, mas o conteúdo não pode ser processado (o CEP tem 8
  dígitos, mas não existe nos Correios).

## Transições de estado como *controller resources*

Alternativas para "cancelar um envio":

| Opção | Problema |
|---|---|
| `PATCH /shipments/{id}` `{"status":"CANCELLED"}` | esconde a intenção numa edição genérica; permite pular estados; onde vai o motivo? |
| `PUT /shipments/{id}/status` | melhor, mas trata a ação como dado |
| **`POST /shipments/{id}/cancel`** ✅ | ação explícita, com corpo próprio (`reason`), mapeada 1:1 para `shipment.cancel()` |

A terceira é o arquétipo *controller* do "REST API Design Rulebook" (Mark Massé) e o equivalente
dos *custom methods* das guidelines do Google (`POST /shipments/123:cancel`). É `POST` porque as
ações não são idempotentes: repetir o despacho gera 409.

## Paginação

```
GET /api/v1/shipments?status=IN_TRANSIT&page=0&size=20
```

```json
{
  "content": [ { "id": "...", "status": "IN_TRANSIT", ... } ],
  "page": 0,
  "size": 20,
  "totalElements": 42,
  "totalPages": 3
}
```

- `page` começa em 0; `size` entre 1 e **100** (teto contra abuso: `size=1000000` derrubaria a
  aplicação).
- Ordenação fixa: mais recentes primeiro, com desempate pelo ID (sem desempate, itens com o
  mesmo `createdAt` poderiam pular entre páginas).
- O envelope é nosso, e não o `Page` do Spring Data, cuja serialização não é contrato estável.
- **Evolução:** com tabelas muito grandes, `OFFSET` fica lento nas páginas finais; a alternativa é
  paginação por cursor (*keyset*: `WHERE (created_at, id) < (?, ?)`).

## Erros: RFC 9457 (Problem Details)

Todo erro, de qualquer origem (validação, regra de negócio, segurança, rota inexistente, bug),
tem o mesmo formato, com `Content-Type: application/problem+json`:

```json
{
  "type": "https://logistics.example.com/problems/shipment-invalid-status-transition",
  "title": "Conflict",
  "status": 409,
  "detail": "O envio 3f2a... não pode passar de DELIVERED para CANCELLED.",
  "instance": "/api/v1/shipments/3f2a.../cancel",
  "code": "SHIPMENT_INVALID_STATUS_TRANSITION",
  "timestamp": "2026-01-15T10:00:00Z",
  "correlationId": "b7c1e3d2-..."
}
```

Erros de validação trazem também a lista de campos:

```json
{
  "status": 400,
  "code": "VALIDATION_FAILED",
  "detail": "A requisição contém campos inválidos.",
  "errors": [
    { "field": "origin.postalCode", "message": "deve estar no formato 00000-000" },
    { "field": "weightGrams", "message": "deve ser menor que ou igual à 30000" }
  ]
}
```

| Campo | Para quem | Estável? |
|---|---|---|
| `code` | programas (o cliente decide o que fazer com base nele) | **sim**: mudar é quebra de contrato |
| `detail` | pessoas | não: o texto pode mudar |
| `correlationId` | suporte (encontra os logs da requisição) | — |

As mensagens de validação seguem o header `Accept-Language` (`pt-BR` → português).

### Catálogo de códigos

| `code` | Status | Quando |
|---|---|---|
| `VALIDATION_FAILED` | 400 | campos do corpo ou parâmetros inválidos |
| `MALFORMED_REQUEST` | 400 | corpo não é JSON válido |
| `INVALID_PARAMETER` | 400 | parâmetro de URL com tipo errado |
| `INVALID_POSTAL_CODE`, `INVALID_WEIGHT`, `INVALID_EMAIL`, `WEAK_PASSWORD`... | 400 | regra de formação de um Value Object |
| `UNAUTHENTICATED` | 401 | sem token, token inválido ou expirado |
| `INVALID_CREDENTIALS` | 401 | login com e-mail ou senha errados |
| `ACCESS_DENIED`, `OPERATION_NOT_ALLOWED` | 403 | papel insuficiente |
| `SHIPMENT_NOT_FOUND`, `USER_NOT_FOUND` | 404 | inexistente ou de outra pessoa |
| `SHIPMENT_INVALID_STATUS_TRANSITION` | 409 | transição não permitida |
| `EMAIL_ALREADY_REGISTERED` | 409 | e-mail já usado |
| `CONCURRENT_UPDATE` | 409 | outra operação alterou o envio ao mesmo tempo |
| `POSTAL_CODE_NOT_FOUND` | 422 | CEP inexistente |
| `EXTERNAL_SERVICE_UNAVAILABLE` | 503 | ViaCEP fora do ar (com `Retry-After`) |
| `INTERNAL_ERROR` | 500 | erro inesperado |
| `NOT_FOUND`, `METHOD_NOT_ALLOWED`, `NOT_ACCEPTABLE`, `UNSUPPORTED_MEDIA_TYPE`... | 404, 405, 406, 415... | erros gerados pelo próprio Spring MVC (rota inexistente, verbo errado, `Accept` ou `Content-Type` não suportados); o `code` é o nome do status HTTP |

**Limitação conhecida:** nesses erros do próprio Spring MVC, o `detail` sai com o texto padrão do
framework, em inglês (`"Method 'DELETE' is not supported."`). O formato e o `code` seguem o
contrato, mas o idioma destoa do resto da API. A correção é traduzir as mensagens com as chaves
`problemDetail.<classe da exceção>` num `messages.properties`; ficou como exercício no
[guia de estudo](12-guia-de-estudo.md).

## Documentação interativa

- Swagger UI: `http://localhost:8080/swagger-ui.html`
- OpenAPI JSON: `http://localhost:8080/v3/api-docs`
- Roteiro executável: [`docs/api/requests.http`](api/requests.http), com 42 requisições e
  asserts. Roda no IntelliJ, no terminal (CLI `ijhttp` da JetBrains, via Docker) e no VS Code
  com a extensão httpYac. A extensão REST Client não serve, porque não executa os scripts que
  capturam o token.

O documento é gerado do código (springdoc), mas "gerado" não quer dizer "correto". Duas
correções ficam no `OpenApiConfig` e nos controllers:

- **Erros com o schema certo.** Um `@ApiResponse(responseCode = "404")` sem `content` herda o
  tipo de retorno do método, e o 404 apareceria com o corpo de um envio. Um `OpenApiCustomizer`
  troca o corpo de todo 4xx/5xx por `application/problem+json` com o schema `Problem`, e soma o
  401 a todo endpoint protegido.
- **2xx explícito.** Quando um método declara qualquer `@ApiResponse`, o springdoc deixa de gerar
  o 200 automático. Os controllers declaram o 2xx, e o `OpenApiContractIT` falha se alguma
  operação ficar sem ele.

Isso importa porque muitos times geram o cliente direto do documento (openapi-generator). Um
contrato errado vira código errado do outro lado.

No Swagger: faça login em `POST /api/v1/auth/token`, copie o `accessToken`, clique em
**Authorize** e cole. O parâmetro `Requester` dos endpoints fica oculto, porque vem do token e
não da requisição.

## Headers relevantes

| Header | Direção | Uso |
|---|---|---|
| `Authorization: Bearer <jwt>` | requisição | autenticação |
| `X-Correlation-Id` | requisição/resposta | rastreio da requisição nos logs (gerado se ausente) |
| `Accept-Language` | requisição | idioma das mensagens de validação |
| `Location` | resposta 201 | URL do recurso criado |
| `Retry-After` | resposta 503 | quando tentar de novo (segundos) |
| `WWW-Authenticate` | resposta 401 | desafio Bearer (RFC 6750) |
