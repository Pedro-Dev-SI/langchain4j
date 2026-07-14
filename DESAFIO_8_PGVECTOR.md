# Desafio 8: trocar InMemoryEmbeddingStore por PostgreSQL com pgvector

Este desafio e o proximo passo natural depois do RAG com documentos em memoria.

Hoje seu projeto funciona assim:

```text
documentos .md
  -> chunks
  -> embeddings
  -> InMemoryEmbeddingStore
  -> busca semantica
  -> resposta da IA
```

O problema e que o `InMemoryEmbeddingStore` guarda tudo apenas na memoria da aplicacao.

Memoria da aplicacao significa: os dados existem enquanto o Spring Boot esta rodando. Se voce parar a aplicacao, os embeddings somem e precisam ser gerados de novo.

Neste desafio, voce vai trocar isso por:

```text
documentos .md
  -> chunks
  -> embeddings
  -> PostgreSQL + pgvector
  -> busca semantica
  -> resposta da IA
```

`pgvector` e uma extensao do PostgreSQL que permite salvar e pesquisar vetores.

Vetor, nesse contexto, e a representacao numerica de um texto. Quando voce transforma um trecho como "documentos obrigatorios para locacao" em embedding, o resultado e uma lista de numeros. Essa lista de numeros e o vetor.

## Objetivo do desafio

Ao final, voce deve conseguir:

- Salvar embeddings no PostgreSQL.
- Reiniciar a aplicacao sem perder os embeddings.
- Usar o mesmo RAG atual, mas com armazenamento persistente.
- Explicar por que a dimensao do embedding precisa bater com a configuracao do pgvector.

## Antes de implementar: entenda as pecas

### EmbeddingModel

E o componente que transforma texto em numeros.

No seu projeto, esse papel hoje e feito pelo Ollama:

```java
OllamaEmbeddingModel.builder()
        .baseUrl(baseUrl)
        .modelName(modelName)
        .build();
```

Se o texto for:

```text
Quais documentos sao obrigatorios para alugar um carro?
```

O embedding model transforma isso em algo parecido com:

```text
[0.12, -0.04, 0.98, ...]
```

Essa lista pode ter centenas ou milhares de numeros.

### Dimensao

Dimensao e a quantidade de numeros dentro do vetor.

Exemplo:

```text
[0.10, 0.20, 0.30]
```

Esse vetor tem dimensao 3.

Um embedding real pode ter dimensao 384, 768, 1536 ou outro valor, dependendo do modelo.

No caso do `nomic-embed-text`, normalmente voce deve configurar dimensao `768`.

Isso e importante porque o banco precisa criar uma coluna preparada para receber vetores daquele tamanho.

Se o banco espera dimensao `768`, mas o modelo gerar dimensao `1536`, a gravacao ou busca vai falhar.

### EmbeddingStore

E onde os vetores ficam salvos.

Hoje voce usa:

```java
new InMemoryEmbeddingStore<>()
```

Isso quer dizer: "guarde os vetores em memoria".

No desafio 8, voce vai usar algo parecido com:

```java
PgVectorEmbeddingStore.builder()
        .host(...)
        .port(...)
        .database(...)
        .user(...)
        .password(...)
        .table(...)
        .dimension(...)
        .build();
```

Isso quer dizer: "guarde os vetores no PostgreSQL usando pgvector".

### ContentRetriever

E quem faz a busca.

Quando o cliente pergunta algo, o LangChain4j:

1. Transforma a pergunta em embedding.
2. Compara esse embedding com os embeddings salvos.
3. Recupera os trechos mais parecidos.
4. Entrega esses trechos para a IA responder.

Voce provavelmente nao vai precisar mudar muito o `ContentRetriever`, porque ele ja depende da interface `EmbeddingStore<TextSegment>`.

Essa e a vantagem de usar interface: voce troca a implementacao de memoria por pgvector sem reescrever o fluxo inteiro.

## Passo 1: adicionar a dependencia do pgvector no pom.xml

No `pom.xml`, adicione a dependencia do LangChain4j para pgvector.

Use a mesma familia de versao que voce esta usando no projeto.

Exemplo:

```xml
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-pgvector</artifactId>
    <version>1.17.1-beta27</version>
</dependency>
```

Observacao: se sua versao do `langchain4j-ollama` estiver diferente das outras dependencias LangChain4j, vale alinhar depois. Misturar versoes pode funcionar, mas aumenta a chance de erro estranho.

## Passo 2: trocar a imagem do banco no docker-compose

Seu banco atual e PostgreSQL normal.

Para usar pgvector, voce precisa de uma imagem que ja venha com a extensao instalada.

Exemplo:

```yaml
services:
  postgres:
    image: pgvector/pgvector:pg17
```

`pgvector/pgvector` e uma imagem Docker com PostgreSQL mais a extensao `vector`.

Extensao, no PostgreSQL, e um recurso extra que voce ativa no banco. O PostgreSQL puro nao sabe trabalhar com vetor por padrao. O pgvector ensina o PostgreSQL a salvar e comparar vetores.

## Passo 3: criar a extensao no banco

Mesmo usando a imagem certa, voce ainda precisa ativar a extensao no banco.

Crie uma migration do Flyway:

```text
src/main/resources/db/migration/V2__enable_pgvector.sql
```

Conteudo:

```sql
CREATE EXTENSION IF NOT EXISTS vector;
```

Essa migration diz:

```text
Se a extensao vector ainda nao existir, crie.
```

Use `IF NOT EXISTS` para evitar erro se ela ja estiver criada.

## Passo 4: criar a tabela por migration

Para estudo rapido, muita gente usa `createTable(true)` no `PgVectorEmbeddingStore`.

Para uma solucao com pensamento de producao, prefira criar a tabela por migration.

Migration, nesse contexto, e um arquivo versionado que descreve uma alteracao no banco. O Flyway executa essas migrations em ordem e registra quais ja foram aplicadas.

Isso e melhor para producao porque:

- Voce sabe exatamente qual estrutura existe no banco.
- A mesma estrutura e aplicada em todos os ambientes.
- O historico de mudancas fica versionado no Git.
- A aplicacao nao precisa ter responsabilidade de criar tabela em runtime.

Runtime significa "durante a execucao da aplicacao". Em producao, criar tabela durante a subida da aplicacao pode esconder erro de deploy e dificultar auditoria.

Crie uma segunda migration:

```text
src/main/resources/db/migration/V3__create_knowledge_embeddings.sql
```

Conteudo:

```sql
CREATE TABLE IF NOT EXISTS knowledge_embeddings (
    embedding_id UUID PRIMARY KEY,
    embedding vector(768),
    text TEXT NULL,
    metadata JSON NULL
);

CREATE INDEX IF NOT EXISTS knowledge_embeddings_embedding_ivfflat_index
    ON knowledge_embeddings
    USING ivfflat (embedding vector_cosine_ops)
    WITH (lists = 100);
```

Explicando cada coluna:

- `embedding_id`: identificador unico do chunk salvo.
- `embedding`: vetor gerado pelo modelo de embedding.
- `vector(768)`: coluna vetorial com dimensao 768.
- `text`: texto original do chunk.
- `metadata`: informacoes extras do chunk, como `source`, `title` e `category`.

O indice `ivfflat` acelera buscas vetoriais.

Indice, no banco, e uma estrutura auxiliar para encontrar dados mais rapidamente. Sem indice, o banco pode precisar comparar a pergunta com muitos vetores um por um.

`vector_cosine_ops` indica que a busca usa distancia por cosseno.

Distancia por cosseno e uma forma de medir se dois vetores apontam para direcoes parecidas. Em RAG, isso ajuda a encontrar textos semanticamente parecidos, mesmo quando as palavras nao sao exatamente iguais.

`lists = 100` e um parametro do indice `ivfflat`. Pense nele como uma forma de organizar os vetores em grupos internos para acelerar a busca.

Para uma base pequena de estudo, `100` esta aceitavel. Para base grande, esse valor deve ser testado.

Importante: a tabela acima usa `metadata JSON NULL` porque essa e a configuracao padrao do `PgVectorEmbeddingStore` do LangChain4j quando voce nao customiza o armazenamento de metadata.

Se depois voce quiser filtrar muito por `category`, `source` ou `title`, pode estudar outro modo: salvar metadados em colunas separadas. Por enquanto, mantenha simples.

## Passo 5: adicionar propriedades no application.yaml

Evite deixar configuracoes importantes hardcoded no Java.

Adicione algo assim:

```yaml
rag:
  embedding:
    base-url: ${RAG_EMBEDDING_BASE_URL:http://localhost:11434}
    model: ${RAG_EMBEDDING_MODEL:nomic-embed-text}
  vector-store:
    host: ${RAG_VECTOR_HOST:localhost}
    port: ${RAG_VECTOR_PORT:5433}
    database: ${RAG_VECTOR_DATABASE:langchain4j}
    user: ${RAG_VECTOR_USER:langchain4j}
    password: ${RAG_VECTOR_PASSWORD:langchain4j}
    table: ${RAG_VECTOR_TABLE:knowledge_embeddings}
    dimension: ${RAG_VECTOR_DIMENSION:768}
```

Explicando:

- `host`: onde o PostgreSQL esta rodando.
- `port`: porta do PostgreSQL no seu computador.
- `database`: nome do banco.
- `user`: usuario do banco.
- `password`: senha do banco.
- `table`: tabela onde os embeddings serao salvos.
- `dimension`: tamanho do vetor gerado pelo modelo de embedding.

## Passo 6: trocar o EmbeddingStore no RagConfig

Hoje seu `RagConfig` tem:

```java
@Bean
public EmbeddingStore<TextSegment> embeddingStore() {
    return new InMemoryEmbeddingStore<>();
}
```

Esse e o ponto exato que muda.

Voce vai trocar por algo conceitualmente assim:

```java
@Bean
public EmbeddingStore<TextSegment> embeddingStore(
        @Value("${rag.vector-store.host}") String host,
        @Value("${rag.vector-store.port}") int port,
        @Value("${rag.vector-store.database}") String database,
        @Value("${rag.vector-store.user}") String user,
        @Value("${rag.vector-store.password}") String password,
        @Value("${rag.vector-store.table}") String table,
        @Value("${rag.vector-store.dimension}") int dimension
) {
    return PgVectorEmbeddingStore.builder()
            .host(host)
            .port(port)
            .database(database)
            .user(user)
            .password(password)
            .table(table)
            .dimension(dimension)
            .createTable(false)
            .build();
}
```

`createTable(false)` significa: "nao crie a tabela automaticamente".

Isso combina com a abordagem de producao, porque a tabela foi criada pelo Flyway.

Se a tabela nao existir, a aplicacao deve falhar ao iniciar ou ao tentar gravar embeddings. Isso e bom, porque mostra rapidamente que o ambiente esta mal configurado.

Falhar rapido e melhor do que criar estrutura errada silenciosamente.

Silenciosamente significa: a aplicacao faz algo sem voce perceber. Em producao, isso e perigoso.

## Passo 7: versionar documentos para evitar duplicacao de ingestion

Hoje, quando sua aplicacao sobe, o `KnowledgeBaseLoader` carrega todos os documentos e ingere tudo de novo.

Com `InMemoryEmbeddingStore`, isso nao era um grande problema, porque a memoria começava vazia a cada start.

Com pgvector, o banco persiste.

Persistir significa: o dado continua la mesmo depois que a aplicacao para.

Entao se voce ingere tudo de novo a cada start, pode duplicar embeddings.

Exemplo:

```text
Start 1: salva 80 chunks
Start 2: salva os mesmos 80 chunks de novo
Start 3: salva os mesmos 80 chunks de novo
```

Depois de tres starts, voce teria 240 registros, mas apenas 80 realmente diferentes.

Para uma solucao mais proxima de producao, use versionamento de documentos.

Versionar documentos significa controlar qual versao de cada arquivo `.md` ja foi indexada.

A regra principal e:

```text
mesmo source + mesmo hash = nao faz nada
mesmo source + hash diferente = remove antigo e indexa novo
source novo = indexa pela primeira vez
```

`source` e a identidade do documento.

Exemplo:

```text
source = politica-pagamentos.md
```

`hash` e a impressao digital do conteudo do documento.

Hash, nesse contexto, e uma string gerada a partir do texto do arquivo. Se o arquivo mudar, o hash muda.

Exemplo fora do seu caso:

```text
manual-produto.md
conteudo: "Produto X aceita pagamento por boleto."
hash: abc123
```

Se o texto mudar para:

```text
"Produto X aceita pagamento por boleto e PIX."
```

o hash tambem muda.

Para o seu caso, seria assim:

```text
politica-pagamentos.md
conteudo atual do arquivo
hash calculado sobre esse conteudo
```

### 7.1 Criar uma tabela de controle dos documentos

Voce ja tera a tabela `knowledge_embeddings`, que guarda os chunks e vetores.

Agora crie uma tabela separada para controlar os documentos indexados:

```text
knowledge_documents
```

Essa tabela nao guarda embeddings. Ela guarda o estado de cada documento.

Crie uma migration:

```text
src/main/resources/db/migration/V4__create_knowledge_documents.sql
```

Estrutura sugerida:

```sql
CREATE TABLE IF NOT EXISTS knowledge_documents (
    source VARCHAR(255) PRIMARY KEY,
    title VARCHAR(255) NOT NULL,
    category VARCHAR(100) NOT NULL,
    content_hash VARCHAR(64) NOT NULL,
    ingested_at TIMESTAMP NOT NULL
);
```

Explicando:

- `source`: nome unico do documento. Exemplo: `politica-pagamentos.md`.
- `title`: nome amigavel. Exemplo: `Politica de Pagamentos`.
- `category`: categoria usada no RAG. Exemplo: `pagamentos`.
- `content_hash`: hash do conteudo atual do arquivo.
- `ingested_at`: data e hora em que aquela versao foi indexada.

### 7.2 Definir a identidade de cada documento

Cada arquivo `.md` precisa ter um cadastro fixo dentro da aplicacao.

Exemplo fora do seu caso:

```text
source = manual-produto.md
title = Manual do Produto
category = suporte
path = docs/manual-produto.md
```

Mas para o seu caso seria:

```text
source = politica-documentos.md
title = Politica de Documentos
category = documentos
path = knowledge/politica-documentos.md
```

Outro exemplo do seu projeto:

```text
source = politica-pagamentos.md
title = Politica de Pagamentos
category = pagamentos
path = knowledge/politica-pagamentos.md
```

Essa definicao pode virar um record simples no futuro, como `KnowledgeDocumentDefinition`.

O nome nao importa tanto agora. O importante e voce entender que cada documento precisa ter:

- caminho para carregar o arquivo;
- nome unico;
- titulo;
- categoria.

### 7.3 Calcular o hash do conteudo

Antes de ingerir o documento, leia o texto do arquivo e calcule um hash.

Exemplo fora do seu caso:

```java
String content = "Manual do produto versao atual";
String hash = sha256(content);
```

Mas para o seu caso seria:

```text
carregar knowledge/politica-pagamentos.md
pegar o texto do documento
calcular sha256 desse texto
```

`sha256` e um algoritmo de hash.

Algoritmo, aqui, significa uma regra matematica para transformar um texto em uma string fixa.

Exemplo conceitual:

```text
"Aceitamos PIX" -> a1b2c3...
"Aceitamos PIX e cartao" -> d4e5f6...
```

Voce nao precisa entender a matematica do SHA-256 agora. Precisa entender o comportamento:

```text
conteudo igual = hash igual
conteudo diferente = hash diferente
```

### 7.4 Consultar se o documento ja foi indexado

Depois de calcular o hash, consulte a tabela `knowledge_documents`.

Pergunta que seu codigo deve fazer:

```text
Existe um registro com esse source e esse content_hash?
```

Exemplo fora do seu caso:

```text
manual-produto.md tem hash abc123
banco ja tem manual-produto.md com hash abc123
resultado: pula ingestion
```

Para o seu caso:

```text
politica-pagamentos.md tem hash xyz789
banco tem politica-pagamentos.md com hash abc123
resultado: documento mudou, precisa reindexar
```

### 7.5 Se nao mudou, pular ingestion

Se `source` e `content_hash` forem iguais ao que ja esta no banco, nao gere embedding de novo.

Fluxo:

```text
documento igual
-> nao remove embeddings
-> nao gera novos embeddings
-> nao atualiza knowledge_documents
```

Isso evita trabalho desnecessario.

Tambem evita gastar processamento ou cota de API, caso voce use embedding pago no futuro.

### 7.6 Se mudou, remover embeddings antigos daquele source

Se o hash mudou, os chunks antigos representam uma versao antiga do documento.

Entao voce deve apagar os embeddings antigos antes de salvar os novos.

Exemplo:

Versao antiga:

```text
Aceitamos apenas cartao de credito.
```

Versao nova:

```text
Aceitamos cartao de credito, debito e PIX.
```

Se as duas versoes ficarem no banco, o RAG pode recuperar informacao antiga e nova ao mesmo tempo.

Como sua tabela `knowledge_embeddings` usa `metadata JSON`, a remocao conceitual seria:

```sql
DELETE FROM knowledge_embeddings
WHERE metadata ->> 'source' = 'politica-pagamentos.md';
```

`metadata ->> 'source'` significa:

```text
pegue o campo source dentro do JSON metadata como texto
```

Para o seu caso, cada documento teria uma remocao por `source`.

Exemplo:

```text
apagar todos os chunks onde source = politica-seguros.md
```

### 7.7 Inserir os novos embeddings com a versao atual

Depois de apagar os embeddings antigos, faca a ingestao normal:

```text
Document
-> chunks
-> embeddings
-> PgVectorEmbeddingStore
```

Mas agora adicione tambem a versao nos metadados:

```text
source = politica-pagamentos.md
title = Politica de Pagamentos
category = pagamentos
version = hashAtual
```

Exemplo fora do seu caso:

```java
document.metadata().put("source", "manual-produto.md");
document.metadata().put("title", "Manual do Produto");
document.metadata().put("category", "suporte");
document.metadata().put("version", hashAtual);
```

Mas para o seu caso seria:

```text
source = politica-pagamentos.md
title = Politica de Pagamentos
category = pagamentos
version = hash calculado do arquivo politica-pagamentos.md
```

### 7.8 Atualizar a tabela knowledge_documents

Depois que os embeddings novos forem salvos com sucesso, atualize a tabela de controle.

Voce deve salvar:

```text
source
title
category
content_hash
ingested_at
```

Se o documento ainda nao existia na tabela, insira.

Se ja existia, atualize.

Essa operacao se chama `upsert`.

Upsert significa:

```text
se existe, atualiza
se nao existe, insere
```

Exemplo conceitual de SQL:

```sql
INSERT INTO knowledge_documents (source, title, category, content_hash, ingested_at)
VALUES (...)
ON CONFLICT (source)
DO UPDATE SET
    title = EXCLUDED.title,
    category = EXCLUDED.category,
    content_hash = EXCLUDED.content_hash,
    ingested_at = EXCLUDED.ingested_at;
```

`ON CONFLICT (source)` significa:

```text
se ja existir um registro com esse source, nao quebre; atualize
```

### 7.9 Fazer tudo dentro de uma transacao

Transacao significa:

```text
ou tudo da certo
ou nada e aplicado
```

Isso e essencial porque o fluxo tem varias etapas:

```text
verificar hash
apagar embeddings antigos
gerar embeddings novos
salvar embeddings novos
atualizar knowledge_documents
```

Imagine que a aplicacao apaga os embeddings antigos e depois falha ao gerar os novos.

Sem transacao:

```text
voce perde os embeddings antigos
e nao salva os novos
```

Com transacao:

```text
se falhar, o banco desfaz a remocao
```

No Spring, isso normalmente fica em um servico com `@Transactional`.

### 7.10 Separar responsabilidades nas classes

Evite colocar toda essa logica no `KnowledgeBaseLoader`.

Uma separacao melhor:

```text
KnowledgeBaseLoader
```

Dispara o carregamento dos documentos ao subir a aplicacao.

```text
KnowledgeIngestionOrchestrator
```

Calcula hash, consulta o banco, decide se pula ou reindexa.

```text
DocumentIngestionService
```

Continua fazendo somente:

```text
Document -> chunks -> embeddings -> store
```

```text
KnowledgeDocumentRepository
```

Consulta e atualiza `knowledge_documents`.

```text
KnowledgeEmbeddingRepository ou JdbcTemplate
```

Remove embeddings antigos da tabela `knowledge_embeddings` por `source`.

### 7.11 Fluxo final do passo 7

Para cada documento:

```text
1. Tenho o documento politica-pagamentos.md
2. Leio o conteudo
3. Calculo hash
4. Consulto knowledge_documents
5. Se source + hash ja existe:
   - pulo
6. Se source existe mas hash e diferente:
   - apago embeddings antigos desse source
   - adiciono metadata com nova version
   - gero embeddings novos
   - salvo embeddings
   - atualizo knowledge_documents
7. Se source nao existe:
   - adiciono metadata com version
   - gero embeddings
   - salvo embeddings
   - insiro em knowledge_documents
```

Resumo:

```text
source igual + hash igual = nao faz nada
source igual + hash diferente = remove antigo e indexa novo
source novo = indexa pela primeira vez
```

## Passo 8: validar se a dimensao esta correta

A dimensao precisa bater com o modelo de embedding.

Para `nomic-embed-text`, comece com:

```yaml
dimension: 768
```

Se estiver errado, o erro normalmente aparece ao tentar salvar ou buscar embeddings.

Uma forma simples de validar e subir a aplicacao e observar se:

- O loader carrega os documentos.
- Os embeddings sao salvos.
- Nenhum erro de dimensao aparece.
- Uma pergunta sobre documentos, pagamento ou seguro retorna resposta com fonte.

Perguntas de teste:

```text
Quais documentos preciso apresentar para alugar um carro?
```

```text
Quais formas de pagamento sao aceitas?
```

```text
Como funciona o processo de locacao?
```

Resposta esperada:

```text
... resposta ...

Fonte: politica-documentos.md
```

ou:

```text
Fonte: politica-pagamentos.md
```

## Passo 9: criar um health check simples

Health check e uma verificacao de saude.

Em aplicacoes Spring, isso significa criar uma forma de saber se uma parte importante do sistema esta funcionando.

No seu caso, um health check de RAG poderia verificar:

- O banco esta acessivel?
- O vector store responde?
- Existe pelo menos um documento indexado?

Para estudo, voce pode criar um componente simples que faz uma busca por uma pergunta conhecida.

Exemplo conceitual:

```java
@Component
public class RagHealthCheck {

    private final ContentRetriever contentRetriever;

    public boolean isHealthy() {
        List<Content> contents = contentRetriever.retrieve(Query.from("documentos obrigatorios"));
        return !contents.isEmpty();
    }
}
```

Isso nao precisa ser perfeito agora.

O objetivo e voce aprender a validar que o RAG nao esta "subindo vazio".

## Passo 10: ordem recomendada de implementacao

Siga esta ordem:

1. Alterar Docker para imagem com pgvector.
2. Criar migration `CREATE EXTENSION IF NOT EXISTS vector`.
3. Criar migration da tabela `knowledge_embeddings`.
4. Criar migration da tabela `knowledge_documents`.
5. Adicionar dependencia `langchain4j-pgvector`.
6. Adicionar propriedades `rag.vector-store` no `application.yaml`.
7. Trocar `InMemoryEmbeddingStore` por `PgVectorEmbeddingStore`.
8. Usar `createTable(false)`.
9. Criar o calculo de hash dos documentos.
10. Criar a consulta de controle em `knowledge_documents`.
11. Criar a remocao de embeddings antigos por `source`.
12. Criar o upsert de `knowledge_documents`.
13. Fazer o fluxo de ingestion rodar dentro de transacao.
14. Subir o banco.
15. Subir a aplicacao.
16. Verificar logs de ingestion.
17. Fazer uma pergunta que use RAG.
18. Reiniciar a aplicacao e confirmar que os documentos nao foram duplicados.

Nao pule as etapas de versionamento.

Elas sao uma das partes mais importantes para entender a diferenca entre memoria, persistencia e ingestion controlada.

## Criterios de aceite

Considere o desafio concluido quando:

- A aplicacao usa `PgVectorEmbeddingStore`.
- O PostgreSQL esta com a extensao `vector` ativa.
- Os documentos sao pesquisaveis depois de reiniciar a aplicacao.
- A dimensao configurada bate com o modelo de embedding.
- Uma pergunta de teste retorna resposta baseada no RAG.
- A resposta continua citando fonte.
- O versionamento impede duplicacao de embeddings ao reiniciar a aplicacao.
- Voce sabe explicar por que `source` e `content_hash` controlam a reindexacao.

## Erros comuns

### Erro: extension "vector" does not exist

Significa que o banco nao tem pgvector instalado ou a extensao nao foi criada.

Verifique:

- A imagem Docker e `pgvector/pgvector`.
- A migration `CREATE EXTENSION IF NOT EXISTS vector` rodou.

### Erro: relation "knowledge_embeddings" does not exist

Significa que a tabela de embeddings nao foi criada.

Verifique:

- A migration `V3__create_knowledge_embeddings.sql` existe.
- O Flyway executou a migration.
- A propriedade `rag.vector-store.table` tem o mesmo nome da tabela criada.

Se voce usa:

```yaml
table: knowledge_embeddings
```

A migration tambem deve criar:

```sql
CREATE TABLE IF NOT EXISTS knowledge_embeddings (...)
```

### Erro de dimensao

Significa que o tamanho do vetor gerado pelo modelo nao bate com a coluna do banco.

Exemplo:

```text
modelo gera 768 numeros
banco espera 1536 numeros
```

Corrija a propriedade:

```yaml
rag.vector-store.dimension: 768
```

### Respostas duplicadas ou estranhas

Pode ser duplicacao de embeddings.

Se voce ingere os mesmos documentos varias vezes, a busca pode recuperar varios chunks repetidos.

Resolva limpando antes de reindexar ou impedindo ingestion quando a base ja estiver carregada.

### Aplicacao sobe, mas RAG nao responde bem

Possiveis causas:

- `minScore` alto demais.
- Documentos mal segmentados.
- Embedding model ruim para portugues.
- Documentos com texto pouco claro.
- Embeddings nao foram gravados.

Para estudo, se as respostas vierem vazias, teste reduzir temporariamente:

```java
.minScore(0.55)
```

Depois ajuste novamente.

## O que voce deve conseguir explicar depois

Quando terminar, voce deve conseguir responder:

- O que o `EmbeddingModel` faz?
- O que o `EmbeddingStore` faz?
- Por que o `InMemoryEmbeddingStore` perde dados?
- O que o pgvector adiciona ao PostgreSQL?
- O que significa dimensao de embedding?
- Por que duplicar ingestion e um problema?
- Por que `ContentRetriever` quase nao precisa mudar?
- Por que citar fonte continua funcionando depois da troca?

Se conseguir explicar esses pontos, voce realmente entendeu o desafio 8.
