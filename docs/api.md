# Documentação da API — Dashboard Administrativo

## Acesso e parâmetros comuns

URL local: `http://localhost:8080`.

Todos os endpoints abaixo utilizam **GET**, retornam JSON em caso de sucesso e exigem uma sessão autenticada com perfil **ADMIN** ou **GERENTE**. O login é realizado pelo formulário `/login`; as consultas usam o cookie `JSESSIONID` da sessão. Não há corpo de requisição.

| Parâmetro de consulta | Tipo | Obrigatório | Descrição |
| --- | --- | --- | --- |
| dataInicio | Data no formato AAAA-MM-DD | Sim | Primeiro dia do período, incluído na consulta. |
| dataFim | Data no formato AAAA-MM-DD | Sim | Último dia do período, incluído na consulta; deve ser igual ou posterior à data inicial. |

O período usa a **data de entrada do pedido**. A consulta inclui todos os horários do último dia, usando como limite exclusivo o início do dia seguinte. Pedidos sem data de entrada não correspondem ao intervalo.

O valor das vendas, a série diária e o ranking consideram somente pedidos com status `FINALIZADO`. O valor total persistido já contém o desconto e não é descontado novamente. O ticket médio é o valor das vendas dividido pela quantidade de pedidos finalizados; quando não há finalizados, retorna zero.

Os exemplos de resposta a seguir correspondem ao cenário do teste de integração: quatro pedidos no período de 01/10/2026 a 03/10/2026, sendo dois finalizados, um recebido e um cancelado. Os resultados de uma instalação dependem dos dados de seu banco.

## GET /api/dashboard/indicadores

Retorna os indicadores gerais. `totalPedidos` inclui todas as situações; os valores monetários são expressos em reais.

Requisição, com a sessão já autenticada:

```http
GET /api/dashboard/indicadores?dataInicio=2026-10-01&dataFim=2026-10-03
Accept: application/json
Cookie: JSESSIONID=<sessao-autenticada>
```

Resposta **200 OK**:

```json
{
  "totalPedidos": 4,
  "pedidosFinalizados": 2,
  "pedidosCancelados": 1,
  "valorVendas": 99.99,
  "ticketMedio": 50.00
}
```

## GET /api/dashboard/vendas

Retorna a série em ordem crescente de data, incluindo os dias sem vendas com zero. `quantidadePedidos` representa a quantidade de pedidos finalizados naquele dia, pela data de entrada.

Requisição:

```http
GET /api/dashboard/vendas?dataInicio=2026-10-01&dataFim=2026-10-03
Accept: application/json
Cookie: JSESSIONID=<sessao-autenticada>
```

Resposta **200 OK**:

```json
[
  { "data": "2026-10-01", "quantidadePedidos": 1, "valorVendas": 80.00 },
  { "data": "2026-10-02", "quantidadePedidos": 0, "valorVendas": 0.00 },
  { "data": "2026-10-03", "quantidadePedidos": 1, "valorVendas": 19.99 }
]
```

## GET /api/dashboard/situacoes

Retorna a quantidade de pedidos em cada situação, incluindo as situações sem pedidos com zero.

Requisição:

```http
GET /api/dashboard/situacoes?dataInicio=2026-10-01&dataFim=2026-10-03
Accept: application/json
Cookie: JSESSIONID=<sessao-autenticada>
```

Resposta **200 OK**:

```json
[
  { "status": "RECEBIDO", "quantidadePedidos": 1 },
  { "status": "EM_PREPARACAO", "quantidadePedidos": 0 },
  { "status": "PRONTO", "quantidadePedidos": 0 },
  { "status": "SAIU_PARA_ENTREGA", "quantidadePedidos": 0 },
  { "status": "RETIRADO", "quantidadePedidos": 0 },
  { "status": "FINALIZADO", "quantidadePedidos": 2 },
  { "status": "CANCELADO", "quantidadePedidos": 1 }
]
```

## GET /api/dashboard/produtos-mais-vendidos

Retorna até cinco produtos, agrupados por `produtoId`, em ordem decrescente de `quantidadeVendida`. O desempate é pelo identificador do produto. O nome é obtido do pedido finalizado com data de entrada mais recente; se as datas forem iguais, considera-se a ordem crescente do ID do pedido.

Requisição:

```http
GET /api/dashboard/produtos-mais-vendidos?dataInicio=2026-10-01&dataFim=2026-10-03
Accept: application/json
Cookie: JSESSIONID=<sessao-autenticada>
```

Resposta **200 OK**:

```json
[
  { "produtoId": "pizza", "nomeProduto": "Pizza", "quantidadeVendida": 5 },
  { "produtoId": "bebida", "nomeProduto": "Suco", "quantidadeVendida": 1 }
]
```

## Período sem dados

Todos os endpoints retornam **200 OK**. Os indicadores ficam zerados, a série diária mantém os dias com zero, as situações apresentam contagens zeradas e o ranking retorna `[]`.

## Possíveis erros

| Código HTTP | Condição |
| --- | --- |
| 400 — Bad Request | Parâmetro obrigatório ausente, data inválida ou data inicial posterior à final. Também é rejeitada a maior data suportada por LocalDate como data final, pois o cálculo exige o dia seguinte. |
| 401 — Unauthorized | Sessão ausente ou expirada. A API não redireciona para o formulário de login; essa resposta não possui corpo JSON definido. |
| 403 — Forbidden | Usuário autenticado sem permissão, como ATENDENTE. O corpo segue o tratamento de segurança do sistema, sem contrato JSON específico. |
| 500 — Internal Server Error | Nos indicadores e nas vendas, pedido finalizado sem valor total válido; no ranking, pedido finalizado sem itens ou com item inválido. Falhas internas de acesso ao banco também podem retornar 500. |

Exemplo de requisição inválida:

```http
GET /api/dashboard/indicadores?dataInicio=2026-10-03&dataFim=2026-10-01
Cookie: JSESSIONID=<sessao-autenticada>
```

Resposta **400 Bad Request**, comum aos quatro endpoints:

```json
{
  "erro": "Informe dataInicio e dataFim no formato AAAA-MM-DD, com início anterior ou igual ao fim."
}
```

Resposta **500 Internal Server Error** para as inconsistências de pedidos tratadas pelo controlador:

```json
{
  "erro": "Não foi possível calcular os dados do dashboard. Verifique os pedidos finalizados."
}
```

Outras falhas internas utilizam o tratamento padrão de erro da aplicação.
