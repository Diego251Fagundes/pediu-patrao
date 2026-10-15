# PediuPatrão 🍕

Sistema de uma pizzaria para cadastrar clientes e produtos, gerenciar pedidos e consultar o histórico das alterações.

## Tecnologias

Java 21 · Spring Boot 3.4.4 · Spring Security · MongoDB · Thymeleaf

## Como executar

### Pré-requisitos

- JDK 21.
- MongoDB Community instalado e em execução.
- IntelliJ IDEA para executar pela IDE.
- Internet para baixar as dependências na primeira execução.

O Maven Wrapper está incluído no projeto; não é necessário instalar o Maven separadamente.

### 1. Obtenha o projeto

```powershell
git clone https://github.com/Diego251Fagundes/pediu-patrao.git
cd pediu-patrao
```

Se já tiver o repositório, utilize a pasta existente.

### 2. Prepare o MongoDB

Inicie o serviço do MongoDB. A configuração padrão usa:

```text
mongodb://127.0.0.1:27017/pizzaria
```

O banco utilizado é `pizzaria`. Para outra conexão, configure `PEDIUPATRAO_MONGODB_URI` no ambiente de execução, mantendo esse banco.

Como alternativa no Windows, extraia o MongoDB Community em `.local/mongodb-<versao>/`, contendo `bin/mongod.exe`, e execute na raiz do projeto:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\iniciar-mongodb.ps1
```

O script armazena os dados em `.local/mongodb-data`. A distribuição e os dados do MongoDB não acompanham o repositório. Utilize o serviço ou o script para iniciar o banco.

### 3. Execute no IntelliJ IDEA

1. Abra a pasta do projeto em **File > Open**.
2. Importe o `pom.xml` como projeto Maven e aguarde o download das dependências.
3. Configure o JDK 21 no projeto e no Maven.
4. Execute a classe `com.umc.pediupatrao.PizzariaPedidosApplication`.
5. Aguarde a inicialização e acesse **http://localhost:8080**.

Para executar pelo terminal, com `JAVA_HOME` apontando para o JDK 21 e o MongoDB iniciado:

```powershell
.\mvnw.cmd spring-boot:run
```

### 4. Faça o primeiro acesso

Quando a conta `admin` ainda não existe, a aplicação cria o seguinte acesso:

| Usuário | Senha inicial | Perfil |
| --- | --- | --- |
| `admin` | `teste123` | `ADMIN` |

A senha de uma conta existente não é redefinida na inicialização.

Entre como ADMIN e utilize **Usuários** para cadastrar as contas GERENTE e ATENDENTE, com os nomes e senhas desejados. Para registrar pedidos, cadastre um produto em **Produtos** e um cliente com o perfil ATENDENTE.

## Requisitos implementados

| Requisito | Funcionalidade |
| --- | --- |
| **RF01 — Controle de acesso** | ADMIN administra usuários e perfis; GERENTE gerencia pedidos; ATENDENTE cadastra e atualiza clientes e registra pedidos. Permissões verificadas no backend, com proteção CSRF. |
| **RF02 — Cadastro de pedidos** | Itens, quantidades, preços registrados, desconto, total calculado no servidor, responsável e data/hora de entrada. |
| **RF03 — Entrada e saída** | Transições validadas, data/hora e responsável pela saída e bloqueio de alterações após o cancelamento. |
| **RF04 — Descontos e cancelamentos** | Operações exclusivas do GERENTE antes da saída. Desconto limitado a 20% e cancelamento com justificativa, preservação dos dados e auditoria. |
| **RF05 — Auditoria** | Eventos persistidos no MongoDB com usuário, perfil, data/hora, operação, entidade, identificador e valores anteriores e novos. Consulta exclusiva de ADMIN e GERENTE, sem edição ou exclusão pelas APIs. |

O fluxo dos pedidos é:

```text
RECEBIDO → EM_PREPARACAO → PRONTO → SAIU_PARA_ENTREGA → FINALIZADO
                                ↘ RETIRADO         → FINALIZADO
```

O cancelamento é permitido antes da saída, somente pelo GERENTE. ADMIN e ATENDENTE não podem aplicar descontos ou cancelar pedidos. A exclusão de clientes permanece restrita ao ADMIN.

## Principais alterações

- Autenticação com os três perfis e restrição das páginas e APIs.
- Cadastro de pedidos com itens e cálculo dos valores no servidor.
- Controle das transições, horários e responsáveis dos pedidos.
- Desconto percentual e cancelamento com justificativa.
- Registro e consulta de auditoria de pedidos e clientes, incluindo alterações de telefone e endereço.

A organização existente em entidades, repositórios, serviços, controladores e templates foi mantida, assim como o padrão visual das páginas.

## Testes

Execute na raiz do projeto:

```powershell
.\mvnw.cmd test
```

Os testes verificam permissões, CSRF, regras dos pedidos e auditoria, utilizando repositórios simulados.
