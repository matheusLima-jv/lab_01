# 0009. Código em inglês, documentação e mensagens em português

- **Status:** Aceita
- **Data:** 2026-10-07

## Contexto

O projeto é estudado por um público brasileiro e simula um sistema brasileiro (CEP, UF, real),
mas deve seguir padrões de mercado e ser apresentável em cases técnicos.

## Decisão

| Elemento | Idioma | Motivo |
|---|---|---|
| Identificadores (classes, métodos, variáveis, tabelas) | **inglês** | padrão de mercado; consistente com bibliotecas e frameworks |
| Comentários, Javadoc, documentação, ADRs, commits | **português** | o objetivo é ensinar; explicação na língua de quem lê |
| Mensagens de erro da API e descrições de rastreamento | **português** | usuários finais brasileiros |
| Mensagens de log | **inglês** | técnicas, pesquisáveis, consistentes com logs de bibliotecas |
| Mensagens de Bean Validation | conforme `Accept-Language` | i18n padrão do Spring/Hibernate Validator |

Termos do domínio brasileiro mantêm o conceito com nome em inglês (`PostalCode` para CEP,
`BrazilianState` para UF), com o glossário em [Visão geral](../01-visao-geral.md) fazendo a ponte.

## Alternativas consideradas

- **Tudo em inglês:** mais universal, mas menos acessível para o objetivo de estudo.
- **Tudo em português (inclusive código):** comum em alguns sistemas legados; destoa do ecossistema
  e dificulta reaproveitar a base em empresas com times internacionais.

## Consequências

- ✅ Código alinhado ao mercado; explicações acessíveis.
- ❌ Mistura de idiomas exige disciplina (a tabela acima é a regra).
