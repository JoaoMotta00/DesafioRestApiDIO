# Santander Dev Week 2023 — API REST (Java / Spring Boot)

Reprodução do repositório [falvojr/santander-dev-week-2023](https://github.com/falvojr/santander-dev-week-2023),
preparada para rodar **localmente** (sem banco externo) e **em nuvem** (PostgreSQL + Docker).

> **Leia primeiro:** veja a seção [Status da verificação](#status-da-verificação) para saber exatamente o que foi
> testado e o que **não** pôde ser executado durante a criação deste projeto.

## Sumário

1. [Status da verificação](#status-da-verificação)
2. [Endpoints](#endpoints)
3. [Executar localmente](#executar-localmente)
4. [Deploy em nuvem](#deploy-em-nuvem)
5. [Variáveis de ambiente](#variáveis-de-ambiente)
6. [Decisões tomadas (diferenças para o original)](#decisões-tomadas-diferenças-para-o-original)
7. [Problemas encontrados e limitações](#problemas-encontrados-e-limitações)
8. [Estrutura do projeto](#estrutura-do-projeto)
9. [Sobre o projeto (README original)](#sobre-o-projeto-readme-original)

---

## Status da verificação

| Item | Status | Observação |
|---|---|---|
| Código Java de `src/main` | ✅ Idêntico ao original | Conferido com `diff -r` contra o clone do repositório |
| Sintaxe dos YAMLs (`application*.yml`, compose, CI) | ✅ Validada | Parse com PyYAML |
| Sintaxe do novo teste de integração | ✅ Sem erros de sintaxe | Verificada com `javac` (sem dependências, então não foi executado) |
| **Compilação com Gradle** | ❌ **Não executada** | O ambiente de criação bloqueia `repo.maven.apache.org`, `services.gradle.org` e `plugins.gradle.org` (HTTP 403 `host_not_allowed`). Não foi possível baixar o Gradle nem as dependências do Spring |
| **Testes (`./gradlew test`)** | ❌ **Não executados** | Mesmo motivo |
| **API rodando (local e Docker)** | ❌ **Não executada** | Mesmo motivo; Docker também não está disponível no ambiente |

**Consequência:** a configuração foi escrita com base no código-fonte e nas convenções do Spring Boot 3.1, mas
**nada disso foi executado de ponta a ponta**. Para fechar essa lacuna, o projeto inclui:

- um teste de integração (`UserApiIntegrationTest`) que exercita `POST /users`, `GET /users/{id}`, conflito (422) e 404;
- um workflow do GitHub Actions (`.github/workflows/ci.yml`) que roda `./gradlew build` em JDK 17 **e** faz `docker build`.

Ao subir este projeto para um repositório seu, o primeiro run do CI é a verificação oficial. Se algo falhar, o log
apontará o ponto exato. Para validar manualmente, use os passos da próxima seção.

---

## Endpoints

| Método | Rota | Descrição | Respostas |
|---|---|---|---|
| `GET` | `/users/{id}` | Busca usuário (com conta, cartão, features e news) | `200`, `404` (`Resource ID not found.`) |
| `POST` | `/users` | Cria usuário (cascata para conta, cartão, features e news) | `201` + header `Location`, `422` (número de conta já existe) |
| `GET` | `/swagger-ui/index.html` | Documentação interativa (OpenAPI/Swagger) | — |
| `GET` | `/v3/api-docs` | Especificação OpenAPI em JSON | — |
| `GET` | `/h2-console` | Console do H2 (**somente perfil `dev`**) | — |

Exemplo de corpo para `POST /users`:

```json
{
  "name": "Naruto",
  "account": { "number": "1001", "agency": "0001", "balance": 1500.50, "limit": 500.00 },
  "card": { "number": "xxxx xxxx xxxx 1001", "limit": 1000.00 },
  "features": [ { "icon": "pix.svg", "description": "PIX" } ],
  "news": [ { "icon": "credit.svg", "description": "Invista!" } ]
}
```

---

## Executar localmente

### Opção A — Gradle + H2 em memória (perfil `dev`, sem instalar banco)

Requisito: **JDK 17** (veja [Gradle 7.6.1 × JDK 20+](#problemas-encontrados-e-limitações)).

```bash
java -version            # deve mostrar 17.x
chmod +x gradlew         # Linux/macOS, caso o arquivo tenha perdido a permissão de execução
./gradlew bootRun        # Windows: gradlew.bat bootRun
```

Sem nenhuma variável definida, o perfil `dev` é usado (`spring.profiles.default: dev`). A API sobe em
<http://localhost:8080> e o Swagger em <http://localhost:8080/swagger-ui/index.html>.

Testes automatizados:

```bash
./gradlew test
```

### Opção B — Docker Compose + PostgreSQL (perfil `prd`, simula a nuvem)

Requisito: Docker com Compose v2.

```bash
docker compose up --build
```

Sobe um PostgreSQL 16 e a API com `SPRING_PROFILES_ACTIVE=prd`. Para customizar usuário/senha/porta,
copie `.env.example` para `.env`. Para limpar tudo (inclusive o volume do banco): `docker compose down -v`.

### Testando a API (qualquer opção)

```bash
curl -i -X POST http://localhost:8080/users \
  -H 'Content-Type: application/json' \
  -d '{"name":"Naruto","account":{"number":"1001","agency":"0001","balance":1500.50,"limit":500.00},"card":{"number":"xxxx 1001","limit":1000.00},"features":[{"icon":"pix.svg","description":"PIX"}],"news":[{"icon":"credit.svg","description":"Invista!"}]}'
# esperado: HTTP 201 e header "Location: http://localhost:8080/users/1"

curl -i http://localhost:8080/users/1          # esperado: 200 com o JSON do usuário
curl -i http://localhost:8080/users/999        # esperado: 404 "Resource ID not found."
# repetir o POST com o mesmo account.number -> 422 "This Account number already exists."
```

---

## Deploy em nuvem

A API é agnóstica de provedor: basta um **PostgreSQL** e as variáveis de ambiente abaixo. Há dois caminhos:

### Caminho 1 — Docker (recomendado)

O `Dockerfile` (multi-stage, JDK 17 no build e JRE 17 no runtime, usuário não-root) funciona em qualquer
plataforma que construa imagens a partir do repositório (Railway, Render, Fly.io, Cloud Run, etc.).
O passo a passo genérico é:

1. Crie um banco **PostgreSQL** no provedor.
2. Crie o serviço da API apontando para este repositório (a plataforma deve detectar o `Dockerfile`).
3. Defina as variáveis: `SPRING_PROFILES_ACTIVE=prd`, `PGHOST`, `PGPORT`, `PGDATABASE`, `PGUSER`, `PGPASSWORD`
   (em provedores com banco gerenciado, costuma ser possível referenciar as variáveis do banco no serviço).
4. A plataforma injeta `PORT`; a aplicação já o respeita (`server.port: ${PORT:8080}`).
5. Configure o *health check* da plataforma para `GET /v3/api-docs` (não há Actuator no projeto; veja as decisões).
6. Após o deploy, acesse `https://<seu-dominio>/swagger-ui/index.html`.

### Caminho 2 — Buildpacks / `Procfile`

O `Procfile` original foi mantido e agora ativa o perfil `prd` explicitamente:

```
web: java -Dspring.profiles.active=prd -jar build/libs/santander-dev-week-2023-0.0.1-SNAPSHOT.jar
```

Requer que a plataforma rode `./gradlew build` (ou `bootJar`) com **JDK 17** antes de executar o comando.
Se ela escolher outro JDK, prefira o Caminho 1.

### Banco de dados gerenciado que só fornece `DATABASE_URL`

Alguns provedores (ex.: Heroku, Render) fornecem `DATABASE_URL` no formato `postgres://user:senha@host:porta/db`,
que **o driver JDBC não entende**. Nesse caso, converta para o formato JDBC e use as variáveis do Spring:

```
SPRING_DATASOURCE_URL=jdbc:postgresql://host:porta/db?sslmode=require
SPRING_DATASOURCE_USERNAME=user
SPRING_DATASOURCE_PASSWORD=senha
```

Elas têm precedência sobre o `application-prd.yml` e são também a forma de exigir SSL (`sslmode=require`) quando o
provedor pedir.

---

## Variáveis de ambiente

| Variável | Obrigatória | Padrão | Uso |
|---|---|---|---|
| `SPRING_PROFILES_ACTIVE` | Em nuvem: sim (`prd`) | `dev` | Escolhe o perfil |
| `PORT` | Não | `8080` | Porta HTTP (injetada pelas plataformas) |
| `PGHOST` | Perfil `prd` | — | Host do PostgreSQL |
| `PGPORT` | Não | `5432` | Porta do PostgreSQL |
| `PGDATABASE` | Perfil `prd` | — | Nome do banco |
| `PGUSER` | Perfil `prd` | — | Usuário |
| `PGPASSWORD` | Perfil `prd` | — | Senha |
| `DDL_AUTO` | Não | `update` | Estratégia do Hibernate (`validate`, `update`, ...) no perfil `prd` |
| `JAVA_OPTS` | Não | vazio | Opções extras da JVM (imagem Docker) |

As variáveis obrigatórias do `prd` **não têm valor padrão de propósito**: se faltar alguma, a aplicação falha na
inicialização com uma mensagem clara (`Could not resolve placeholder ...`), em vez de tentar conectar em um banco errado.

---

## Decisões tomadas (diferenças para o original)

O código Java de `src/main` **não foi alterado**. Mudanças e adições:

| Arquivo | Mudança | Motivo |
|---|---|---|
| `src/main/resources/application.yml` (novo) | `spring.profiles.default: dev` | Sem perfil definido, o original não tinha configuração explícita de banco. Agora `./gradlew bootRun` funciona direto, com H2 |
| `application.yml` | `server.port: ${PORT:8080}` | Plataformas de nuvem definem a porta via `PORT` |
| `application.yml` | `server.forward-headers-strategy: framework` | Atrás de proxy com TLS, o header `Location` do `POST /users` sairia com `http://` e host interno. Com isso usa `X-Forwarded-*` |
| `application.yml` | `server.shutdown: graceful` | Encerramento limpo quando a plataforma reinicia o contêiner |
| `application-prd.yml` | `ddl-auto: ${DDL_AUTO:update}` (era `validate`) | Nenhum script de schema existe no projeto; com `validate` a primeira subida em um banco vazio falha. Veja o problema nº 3 |
| `application-prd.yml` | `PGPORT` com padrão `5432` | Conveniência; as demais continuam obrigatórias |
| `Procfile` | Adicionado `-Dspring.profiles.active=prd` | O `Procfile` só é usado em deploy, onde o perfil correto é `prd` |
| `Dockerfile`, `.dockerignore` (novos) | Build multi-stage com JDK 17 | Garante JDK 17 (exigido pelo Gradle 7.6.1) e deploy reproduzível em qualquer provedor. O `find ... ! -name '*-plain.jar'` evita copiar o jar "plain" gerado junto do `bootJar` |
| `docker-compose.yml`, `.env.example` (novos) | API + PostgreSQL | Testar o perfil `prd` localmente antes de publicar |
| `src/test/.../UserApiIntegrationTest.java` (novo) | 3 testes MockMvc | O único teste original (`contextLoads`) não exercita a API |
| `.github/workflows/ci.yml` (novo) | Build + testes + `docker build` | Verificação automática, já que não foi possível executar o build na criação |
| `.gitignore` | Adicionado `.env` | Evitar versionar credenciais |

**Decisões deliberadamente não tomadas:**

- **Não adicionei o Actuator** (`/actuator/health`): é uma dependência nova que eu não conseguiria validar aqui.
  O health check usa `/v3/api-docs`. Se quiser algo mais semântico, adicione `spring-boot-starter-actuator`.
- **Não atualizei Spring Boot/Gradle**, para manter fidelidade ao original (Boot 3.1.2, Gradle 7.6.1, Java 17).
- **Não corrigi os bugs do código Java** listados abaixo; apenas os documentei.

---

## Problemas encontrados e limitações

1. **Build não verificado no ambiente de criação** (rede bloqueada para Maven Central e Gradle). Veja o
   [Status da verificação](#status-da-verificação). É o principal ponto de atenção desta entrega.

2. **Gradle 7.6.1 × JDK 20+.** O wrapper usa Gradle 7.6.1, que **não roda em JDK 20 ou superior**
   (erro típico: `Unsupported class file major version 6x`). Use JDK 17 localmente. O `Dockerfile` e o CI já usam 17.
   Atualizar para Gradle 8.5+ e Spring Boot 3.2+ resolveria, mas foge da reprodução fiel.

3. **`ddl-auto: validate` no `prd` original.** Não existe `schema.sql` nem migration; em um PostgreSQL vazio a
   aplicação não sobe. Solução adotada: `update` por padrão (configurável). **Recomendação:** adotar Flyway ou
   Liquibase e voltar para `validate`, pois `update` não é uma estratégia segura para produção.

4. **`NullPointerException` quando o JSON não tem `account`.** `UserServiceImpl.create` chama
   `userToCreate.getAccount().getNumber()` sem checar `null`. O resultado é `500` em vez de um `4xx`.

5. **JSON inválido também vira `500`.** O `GlobalExceptionHandler` captura `Throwable`, inclusive
   `HttpMessageNotReadableException` (deveria ser `400`).

6. **Duas coleções `List` com `FetchType.EAGER` na entidade `User`** (`features` e `news`). Em versões antigas do
   Hibernate isso lançava `MultipleBagFetchException`. O projeto original funciona com Boot 3.1.x, então não deve
   ser problema, mas **não pude confirmar executando** — o `contextLoads` e os testes novos vão revelar no CI.

7. **Entidades JPA expostas diretamente na API** (sem DTOs) e **sem autenticação/autorização**. O próprio README
   original aponta que o projeto é educacional e que o repositório
   [digitalinnovationone/santander-dev-week-2023-api](https://github.com/digitalinnovationone/santander-dev-week-2023-api)
   traz uma versão mais completa (CRUD completo, DTOs, documentação OpenAPI refinada).

8. **API pública de demonstração fora do ar.** O notebook `SantanderDevWeek2023.ipynb` informa que
   `sdw-2023-prd.up.railway.app` foi descontinuada. Não há instância de referência para comparar respostas.

9. **O notebook usa `PUT /users/{id}`, que esta API não implementa** (só existem `GET` e `POST`). Para a etapa
   *Load* do notebook funcionar contra esta API seria preciso adicionar o endpoint (ou seguir a orientação do
   próprio notebook e salvar o resultado localmente).

10. **Perfil `dev` não deve ir para a nuvem:** expõe o console do H2 e perde os dados a cada reinício.

### Próximos passos sugeridos

- Subir o projeto para um repositório seu e conferir o primeiro run do GitHub Actions.
- Se o CI falhar, corrigir conforme o log (os pontos de maior risco são os itens 2 e 6).
- Adotar Flyway, DTOs com validação (`jakarta.validation`) e tratar os itens 4 e 5.

---

## Estrutura do projeto

```
.
├── Dockerfile                         # build multi-stage (JDK 17 -> JRE 17)
├── docker-compose.yml                 # API + PostgreSQL (perfil prd)
├── Procfile                           # deploy via buildpacks
├── build.gradle / settings.gradle     # Gradle 7.6.1 (wrapper), Spring Boot 3.1.2
├── .github/workflows/ci.yml           # build + testes + docker build
├── SantanderDevWeek2023.ipynb         # notebook ETL (original)
└── src
    ├── main
    │   ├── java/me/dio
    │   │   ├── Application.java
    │   │   ├── controller/UserController.java
    │   │   ├── controller/exception/GlobalExceptionHandler.java
    │   │   ├── domain/model/{User,Account,Card,Feature,News,BaseItem}.java
    │   │   ├── domain/repository/UserRepository.java
    │   │   └── service/{UserService.java, impl/UserServiceImpl.java}
    │   └── resources
    │       ├── application.yml        # novo: perfil padrão, porta, proxy
    │       ├── application-dev.yml    # H2 em memória
    │       └── application-prd.yml    # PostgreSQL
    └── test/java/me/dio
        ├── SantanderDevWeek2023ApplicationTests.java
        └── UserApiIntegrationTest.java   # novo
```

---

## Sobre o projeto (README original)


Java RESTful API criada para a Santander Dev Week.

## Principais Tecnologias
 - **Java 17**: Utilizaremos a versão LTS mais recente do Java para tirar vantagem das últimas inovações que essa linguagem robusta e amplamente utilizada oferece;
 - **Spring Boot 3**: Trabalharemos com a mais nova versão do Spring Boot, que maximiza a produtividade do desenvolvedor por meio de sua poderosa premissa de autoconfiguração;
 - **Spring Data JPA**: Exploraremos como essa ferramenta pode simplificar nossa camada de acesso aos dados, facilitando a integração com bancos de dados SQL;
 - **OpenAPI (Swagger)**: Vamos criar uma documentação de API eficaz e fácil de entender usando a OpenAPI (Swagger), perfeitamente alinhada com a alta produtividade que o Spring Boot oferece;
 - **Railway**: facilita o deploy e monitoramento de nossas soluções na nuvem, além de oferecer diversos bancos de dados como serviço e pipelines de CI/CD.

## [Link do Figma](https://www.figma.com/file/0ZsjwjsYlYd3timxqMWlbj/SANTANDER---Projeto-Web%2FMobile?type=design&node-id=1421%3A432&mode=design&t=6dPQuerScEQH0zAn-1)

O Figma foi utilizado para a abstração do domínio desta API, sendo útil na análise e projeto da solução.

## Diagrama de Classes (Domínio da API)

```mermaid
classDiagram
  class User {
    -String name
    -Account account
    -Feature[] features
    -Card card
    -News[] news
  }

  class Account {
    -String number
    -String agency
    -Number balance
    -Number limit
  }

  class Feature {
    -String icon
    -String description
  }

  class Card {
    -String number
    -Number limit
  }

  class News {
    -String icon
    -String description
  }

  User "1" *-- "1" Account
  User "1" *-- "N" Feature
  User "1" *-- "1" Card
  User "1" *-- "N" News
```

## IMPORTANTE

Este projeto foi construído com um viés totalmente educacional para a DIO. Por isso, disponibilizamos uma versão mais robusta dele no repositório oficial da DIO:

### [digitalinnovationone/santander-dev-week-2023-api](https://github.com/digitalinnovationone/santander-dev-week-2023-api)

Lá incluímos todas os endpoints de CRUD, além de aplicar boas práticas (uso de DTOs e refinamento na documentação da OpenAPI). Sendo assim, caso queira um desafio/referência mais completa é só acessar 👊🤩
