# 0008. Testcontainers em vez de banco em memória nos testes

- **Status:** Aceita
- **Data:** 2026-10-07

## Contexto

O projeto usa recursos específicos do PostgreSQL (`JSONB`, `FOR UPDATE SKIP LOCKED`, índice
parcial, `CHECK` com regex), do MongoDB (índices, chave única) e do RabbitMQ (filas quorum,
dead-lettering, publisher confirms). Bancos em memória (H2, Mongo embutido) não reproduzem esses
comportamentos.

## Decisão

- Testes de integração usam **Testcontainers** com as **mesmas versões** do `docker-compose.yml`
  (`ContainerImages`), conectados ao Spring via `@ServiceConnection`.
- APIs externas são simuladas com **WireMock** (HTTP real, inclusive falhas de rede); nenhum teste
  depende da internet.
- Separação Surefire (`*Test`, sem Docker) x Failsafe (`*IT`, com Docker).

## Alternativas consideradas

| Alternativa | Por que não |
|---|---|
| H2 em modo PostgreSQL | não tem JSONB, SKIP LOCKED nem índice parcial; falsos positivos |
| Banco compartilhado de testes | estado compartilhado, testes interdependentes, conflitos no CI |
| Mockar repositórios em testes de adaptador | testaria o mock, não o SQL nem o mapeamento |

## Consequências

- ✅ Testes de integração refletem produção (migrações, SQL, locks, índices).
- ✅ `./mvnw spring-boot:test-run` reaproveita a mesma configuração para desenvolvimento.
- ❌ Exige Docker e é mais lento (dezenas de segundos), por isso fica na fase `verify`.
- ⚠️ Contextos Spring diferentes sobem containers diferentes; manter poucas configurações
  distintas de teste para aproveitar o cache de contexto.
