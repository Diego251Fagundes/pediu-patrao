# Requisitos — Dashboard Administrativo

## Requisitos funcionais

| Código | Requisito |
| --- | --- |
| RF01 | Apresentar valor das vendas, quantidade de pedidos finalizados, ticket médio, total de pedidos e pedidos cancelados no período. |
| RF02 | Exibir gráfico de vendas por dia, com os valores também disponíveis em tabela. |
| RF03 | Apresentar gráfico e tabela da quantidade de pedidos por situação. |
| RF04 | Exibir os cinco produtos com mais unidades vendidas no período. |
| RF05 | Filtrar todas as consultas por data inicial e data final. |
| RF06 | Alimentar os indicadores, gráficos e ranking por endpoints próprios integrados ao MongoDB. |
| RF07 | Carregar os dados ao abrir o painel, aplicar filtros e atualizar; informar carregamento, período vazio, falhas e sessão expirada; impedir que respostas antigas substituam uma consulta mais recente. |

## Requisitos não funcionais

| Código | Requisito |
| --- | --- |
| RNF01 — Segurança | Restringir o dashboard e suas consultas aos perfis ADMIN e GERENTE, utilizando a autenticação existente. |
| RNF02 — Precisão | Calcular os valores monetários com BigDecimal e apresentar duas casas decimais, com arredondamento HALF_UP. |
| RNF03 — Usabilidade | Utilizar interface em português, campos identificados, mensagens de estado e tabelas como alternativa aos gráficos. |
| RNF04 — Manutenção | Separar os endpoints, as regras de cálculo e o acesso aos dados em controlador, serviço e repositório. |
| RNF05 — Testabilidade | Manter testes automatizados dos cálculos, filtros, permissões, atualização da interface e integração com o banco. |
