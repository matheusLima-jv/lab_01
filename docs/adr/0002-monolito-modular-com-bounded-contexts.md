# 0002. Monólito modular com dois bounded contexts

- **Status:** Aceita
- **Data:** 2026-10-07

## Contexto

Há dois assuntos com linguagens diferentes: **identidade** (usuários, credenciais, papéis) e
**envios** (encomendas, frete, rastreamento). A mesma pessoa é "usuário" em um e "cliente" no
outro. Microsserviços trariam rede, deploy e observabilidade distribuída sem necessidade para o
tamanho do projeto.

## Decisão

- Um único deploy (**monólito modular**) com pacotes `identity` e `shipping` como bounded
  contexts, mais um `shared` mínimo (shared kernel + infraestrutura transversal).
- Contextos **não importam classes um do outro** (ArchUnit). `shipping` conhece o cliente apenas
  pelo UUID (`CustomerId`), vindo do token.
- Sem foreign key de `shipments.customer_id` para `users.id`.

## Alternativas consideradas

| Alternativa | Por que não |
|---|---|
| Microsserviços | complexidade operacional (rede, consistência distribuída, deploys) sem ganho neste porte |
| Um único modelo "User" compartilhado | acopla os contextos; mudanças em identidade quebrariam envios |
| Spring Modulith | ótimo para verificar módulos; adiado para manter a estrutura explícita e didática (citado como evolução) |

## Consequências

- ✅ Fronteiras claras e testadas; extrair um contexto para outro serviço é viável.
- ✅ Um deploy, uma base de código, transações locais.
- ❌ Sem FK, a integridade referencial entre contextos é garantida pela aplicação.
- ⚠️ O `shared` precisa continuar pequeno: tudo que entra nele acopla todos os contextos.
