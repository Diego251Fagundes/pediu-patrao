# Evidências dos testes — Dashboard Administrativo

Execução em **01/11/2026**, sobre o código do commit `28bd806`, em Windows, com Java Temurin 23.0.2 (projeto configurado para Java 21), Maven Wrapper e Node.js 22.23.1.

## Testes Java e integração com MongoDB

Com o MongoDB local iniciado, foram executados os comandos na raiz do projeto:

```powershell
$env:PEDIUPATRAO_TEST_MONGODB_URI = 'mongodb://127.0.0.1:27017'
.\mvnw.cmd test
```

Trecho do resultado obtido:

```text
[INFO] Tests run: 114, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
[INFO] Total time: 49.273 s
[INFO] Finished at: 2026-11-01T15:16:18-03:00
```

| Classe ou grupo | Testes | Resultado |
| --- | ---: | --- |
| DashboardServiceTest | 14 | Aprovados: indicadores, descontos, dias zerados, contagem por situação, datas e valores inválidos. |
| DashboardRankingTest | 6 | Aprovados: soma de unidades, somente finalizados, agrupamento por ID, nome mais recente, desempate, cinco produtos e itens inválidos. |
| DashboardSecurityTest | 24 | Aprovados: respostas dos quatro endpoints, parâmetros inválidos ou ausentes, acesso de ADMIN/GERENTE, bloqueio de ATENDENTE e de consultas sem sessão, e erros de dados inconsistentes. |
| ClientesPedidosSecurityTest | 28 | Aprovados: página inicial com dashboard para ADMIN/GERENTE, boas-vindas para ATENDENTE e regressão das permissões de clientes e pedidos. |
| DashboardMongoTest | 1 | Aprovado: consultas dos quatro recursos sobre pedidos persistidos no MongoDB real. |
| Demais testes de usuários, pedidos, clientes, auditoria e inicialização | 41 | Aprovados. |
| **Total** | **114** | **Nenhuma falha, erro ou teste ignorado.** |

O [teste de integração](../src/test/java/com/umc/pediupatrao/service/DashboardMongoTest.java) cria uma base temporária com nome único e a remove ao terminar. Ele não utiliza a base `pizzaria`. Sem a variável `PEDIUPATRAO_TEST_MONGODB_URI`, esse teste é ignorado; na execução registrada acima, a variável foi informada e o teste foi executado.

Nesse teste, foram incluídos pedidos exatamente no início e no último dia do período, além de pedidos anteriores, posteriores e sem data. Para o intervalo de 01/10/2026 a 03/10/2026, foram conferidos os seguintes resultados:

| Verificação | Esperado | Obtido |
| --- | --- | --- |
| Pedidos no período | 4, sendo 2 finalizados e 1 cancelado | Conforme esperado |
| Valor das vendas e ticket médio | R$ 99,99 e R$ 50,00 | Conforme esperado |
| Série diária | R$ 80,00; R$ 0,00; R$ 19,99 | Conforme esperado |
| Situação CANCELADO | 1 pedido | Conforme esperado |
| Ranking | Pizza: 5 unidades; Suco: 1 unidade | Conforme esperado |
| Período sem pedidos | Total zero e ranking vazio | Conforme esperado |

Os [exemplos de resposta da API](api.md) usam esse mesmo cenário.

## Testes JavaScript

Comando executado:

```powershell
node --test src/test/js/dashboard.test.cjs
```

Trecho do resultado obtido:

```text
1..7
# tests 7
# suites 0
# pass 7
# fail 0
# cancelled 0
# skipped 0
# todo 0
# duration_ms 3133.35
```

| Cenário | Resultado |
| --- | --- |
| Consultar os quatro recursos com o mesmo período, formatar os valores e inserir nomes como texto | Aprovado |
| Apresentar período vazio e tabelas quando a biblioteca de gráficos não está disponível | Aprovado |
| Ocultar resultados anteriores quando uma consulta falha e permitir nova tentativa | Aprovado |
| Descartar respostas atrasadas após aplicar outro período | Aprovado |
| Manter o período aplicado ao atualizar, mesmo com datas editadas sem aplicar | Aprovado |
| Rejeitar datas invertidas antes de consultar a API | Aprovado |
| Informar sessão expirada sem mostrar resultados parciais | Aprovado |

Esses [testes JavaScript](../src/test/js/dashboard.test.cjs) utilizam simulações do DOM, das requisições e dos gráficos. O teste de integração com banco real é o `DashboardMongoTest`, descrito acima.
