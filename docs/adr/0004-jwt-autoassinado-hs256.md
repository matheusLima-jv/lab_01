# 0004. JWT autoassinado (HS256) com Spring Security Resource Server

- **Status:** Aceita
- **Data:** 2026-10-07

## Contexto

A API precisa de autenticação stateless para clientes e operadores. Não há Identity Provider
disponível no ambiente do projeto, e ele deve rodar com um único `docker compose up`.

## Decisão

- A própria API **emite** JWTs no login (`JwtAccessTokenIssuer`, `NimbusJwtEncoder`) e os
  **valida** com o **Resource Server** do Spring Security (`NimbusJwtDecoder`): assinatura, `exp`,
  `iss` e algoritmo fixo HS256.
- Chave HMAC de ≥ 256 bits, apenas por variável de ambiente; sem valor padrão no YAML base.
- Claims mínimas: `sub` (UUID), `roles`, `iss`, `iat`, `exp`, `jti`. Sem dados pessoais.
- Validade de 30 minutos, sem refresh token.

## Alternativas consideradas

| Alternativa | Por que não (agora) |
|---|---|
| Keycloak/Auth0 como IdP + RS256/JWKS | padrão corporativo, mas adiciona um serviço pesado ao ambiente de estudo; é a evolução recomendada |
| RS256 autoassinado | só compensa quando outros serviços precisam validar com a chave pública |
| Filtro JWT próprio (`OncePerRequestFilter` + jjwt) | reimplementa código de segurança sensível; fonte comum de falhas |
| Sessão com cookie | stateful; exige CSRF; atrapalha a escala horizontal |
| Tokens opacos no Redis | revogáveis, mas exigem consulta a cada requisição e mais infraestrutura |

## Consequências

- ✅ Validação robusta e testada (expirado, forjado, adulterado, outro emissor, `alg: none`).
- ✅ Stateless: qualquer instância atende qualquer requisição.
- ❌ Sem revogação antes da expiração (mitigado pela validade curta).
- ❌ Chave simétrica: quem valida também poderia emitir. Aceitável com uma única aplicação.
- ⚠️ Em produção: segredo em cofre, HTTPS obrigatório e, idealmente, migração para um IdP.
