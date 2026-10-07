# 0001. Arquitetura hexagonal com DDD tático

- **Status:** Aceita
- **Data:** 2026-10-07

## Contexto

O projeto precisa ser base de estudo e de cases técnicos, com regras de negócio reais (máquina
de estados, cálculo de frete, autorização por dono) e várias tecnologias de infraestrutura
(PostgreSQL, MongoDB, RabbitMQ, API externa, JWT). As regras precisam ser testáveis sem
infraestrutura, e a infraestrutura precisa ser trocável sem reescrever regras.

## Decisão

- Arquitetura **hexagonal** (portas e adaptadores) com a **regra de dependência** da Clean
  Architecture: `domain` ← `application` ← `adapter` ← `config`.
- Domínio modelado com **DDD tático**: agregados ricos, Value Objects, eventos de domínio,
  domain service e exceções categorizadas.
- Estrutura de pacotes **por contexto, depois por camada**.
- Regras verificadas por **ArchUnit** a cada build.

## Alternativas consideradas

| Alternativa | Por que não |
|---|---|
| Camadas tradicionais (controller → service → repository) com entidades JPA no centro | regras misturadas com framework; domínio anêmico; testar regra exige banco |
| Pacotes por camada técnica na raiz (`controllers/`, `services/`) | espalha cada funcionalidade; fronteiras invisíveis |
| Vertical slices (um pacote por funcionalidade, sem camadas) | ótimo para CRUDs variados; menos didático para mostrar inversão de dependência e modelagem de domínio |

## Consequências

- ✅ Regras testadas em milissegundos, sem Spring nem Docker (~140 testes).
- ✅ Tecnologias trocáveis por adaptador (ex.: ViaCEP → BrasilAPI, Caffeine → Redis).
- ✅ A arquitetura é verificada, não só documentada.
- ❌ Mais arquivos e mapeamentos (DTO ↔ Command ↔ domínio ↔ entidade JPA).
- ❌ Curva de aprendizado para quem só conhece MVC em camadas.
- ⚠️ Em funcionalidades puramente CRUD, avaliar caso a caso se o rigor compensa.
