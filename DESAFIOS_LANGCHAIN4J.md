# Trilha de desafios LangChain4j no projeto

Este documento foi escrito olhando para o projeto atual, que hoje tem um assistente simples de locadora corporativa:

- `AssistantAiService`: define o comportamento do assistente com `@SystemMessage` e `@UserMessage`.
- `AssistantConfig`: cria o `GoogleAiGeminiChatModel` e registra a tool no `AiServices`.
- `AssistantTools`: expõe uma função Java com `@Tool` para calcular cotação.
- `AssistantAiController`: recebe uma mensagem via `POST /api/assistant`.

A ideia da trilha é evoluir esse projeto aos poucos, aprendendo LangChain4j sem perder a arquitetura de vista.

## Antes de começar

### 1. Corrigir configuração local de Java

O `pom.xml` está configurado com:

```xml
<java.version>25</java.version>
```

Ao rodar `./mvnw test`, o build falhou com:

```text
Fatal error compiling: error: release version 25 not supported
```

Desafio inicial:

- Ajuste o ambiente para usar JDK 25 ou reduza temporariamente o `java.version` para uma versão instalada, como 21.
- Rode `./mvnw test` até pelo menos compilar.

Critério de aceite:

- `./mvnw test` compila.
- Você sabe explicar qual JDK está sendo usado pelo Maven.

### 2. Remover segredo do `application.yaml`

O projeto tem uma chave de API diretamente em `src/main/resources/application.yaml`. Isso é perigoso porque esse arquivo costuma ir para Git.

Desafio:

- Troque a configuração para usar variável de ambiente.
- Exemplo:

```yaml
gemini:
  api-key: ${GEMINI_API_KEY}
  model: gemini-3.1-flash-lite
```

Critério de aceite:

- A aplicação só sobe se `GEMINI_API_KEY` estiver configurada.
- Nenhuma chave real fica versionada no projeto.

Conceitos aprendidos:

- Configuração Spring Boot.
- Separação entre código e segredo.
- Preparação mínima para produção.

### 3. Usar LLM local para testes gratuitos

Para evitar gastar cota do Gemini enquanto você aprende, use um modelo local com Ollama. Ele roda no seu computador e expõe uma API local em `http://localhost:11434`.

Fluxo sugerido:

```bash
ollama pull llama3.2
ollama run llama3.2
```

Depois, adicione a dependência do Ollama no `pom.xml`, usando a mesma família de versão do LangChain4j que o projeto já usa:

```xml
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-ollama</artifactId>
    <version>1.17.1-beta27</version>
</dependency>
```

Crie um profile separado para escolher o provedor:

```yaml
app:
  ai:
    provider: ollama

ollama:
  base-url: http://localhost:11434
  model: llama3.2
```

Direção de implementação:

- manter Gemini para testes pontuais de qualidade;
- usar Ollama para testes repetitivos, tools, memória, guardrails e RAG;
- criar uma configuração Spring separada para `OllamaChatModel`;
- deixar o provider selecionável por profile ou propriedade.

Critério de aceite:

- A aplicação roda sem `GEMINI_API_KEY` quando `app.ai.provider=ollama`.
- O endpoint `/api/assistant` responde usando modelo local.
- Os testes automatizados continuam sem chamar API externa.

Limitações:

- O modelo local pode ser menos inteligente que Gemini.
- A velocidade depende da sua máquina.
- Para validar comportamento final de produção, ainda vale testar às vezes com o modelo real.

Conceitos aprendidos:

- Modelo local.
- Troca de provider por configuração.
- Desenvolvimento sem custo de API.
- Separação entre ambiente de estudo e ambiente real.

## Observações sobre a arquitetura atual

O pacote `com.br.langchain4j.ai` concentra controller, configuração, interface do assistente e tools. Para um exemplo pequeno está aceitável, mas conforme você adicionar RAG, memória, testes e regras de negócio, esse pacote tende a ficar confuso.

Uma divisão simples para evoluir seria:

```text
com.br.langchain4j
  assistant
    api
    application
    config
    tools
  rental
    domain
    application
  knowledge
    ingestion
    retrieval
    config
```

Não precisa refatorar tudo agora. Use essa estrutura como direção quando os desafios começarem a criar classes novas.

Também existe uma decisão importante no projeto atual: `AssistantAiService` está anotado com `@AiService`, mas `AssistantConfig` também cria manualmente um bean com `AiServices.builder(...)`. Para estudo isso pode passar despercebido, mas com mais configurações vale escolher uma estratégia principal:

- usar `@AiService` e deixar o starter do LangChain4j/Spring criar o serviço;
- ou remover `@AiService` e manter a criação explícita no `AssistantConfig`.

Como você já está registrando tools manualmente, a criação explícita em `AssistantConfig` é uma boa opção para aprender o que está sendo ligado.

## Desafio 1: endurecer a tool de cotação

Hoje `AssistantTools.calculateQuotation` consulta mapas em memória e calcula o valor. O problema é que uma categoria inválida ou `days <= 0` pode gerar erro ou resposta incorreta.

Arquivos prováveis:

- `src/main/java/com/br/langchain4j/ai/AssistantTools.java`
- Novo teste em `src/test/java/com/br/langchain4j/ai/AssistantToolsTest.java`

O que implementar:

- Validar `category`.
- Validar `days`.
- Retornar mensagem clara quando os dados forem inválidos.
- Adicionar `@P` nos parâmetros da tool para guiar melhor o modelo.

Exemplo de direção:

```java
@Tool("Calcula o valor total do aluguel corporativo.")
public String calculateQuotation(
        @P("Categoria do carro: economico, suv ou premium") String category,
        @P("Quantidade de dias do aluguel. Deve ser maior que zero") int days) {
    // valida e calcula
}
```

Critérios de aceite:

- Categoria `moto` não quebra a aplicação.
- `days` igual a zero ou negativo retorna erro amigável.
- O teste unitário cobre sucesso e erro.

Conceitos aprendidos:

- Function calling com `@Tool`.
- Descrição de parâmetros com `@P`.
- Validação defensiva em tools.
- Teste unitário determinístico, sem chamar LLM.

## Desafio 2: separar regra de negócio da tool

Hoje a regra de preço está dentro da classe exposta ao modelo. Isso mistura duas responsabilidades: regra de locação e adaptação para LLM.

Arquivos prováveis:

- Novo `rental/domain/RentalCategory.java`
- Novo `rental/application/QuotationService.java`
- `assistant/tools/AssistantTools.java`

O que implementar:

- Criar um enum `RentalCategory`.
- Criar um serviço `QuotationService`.
- Deixar `AssistantTools` apenas como adaptador entre LangChain4j e sua regra de negócio.

Critérios de aceite:

- `AssistantTools` não conhece os mapas de preço diretamente.
- `QuotationService` pode ser testado sem Spring e sem LangChain4j.
- A resposta do endpoint continua funcionando.

Conceitos aprendidos:

- Arquitetura em camadas simples.
- Tools como adapters, não como domínio.
- Testes rápidos de regra de negócio.

## Desafio 3: melhorar o contrato HTTP

O controller recebe o corpo como `String` puro. Funciona para estudo, mas limita evolução.

Arquivos prováveis:

- `AssistantAiController.java`
- Novo DTO `AssistantRequest`
- Novo DTO `AssistantResponse`

O que implementar:

```json
{
  "message": "Quanto custa um SUV por 3 dias?"
}
```

Resposta sugerida:

```json
{
  "answer": "Cotação: suv por 3 dias -> R$ ...",
  "model": "gemini-3.1-flash-lite"
}
```

Critérios de aceite:

- O endpoint aceita JSON.
- Entrada vazia retorna erro HTTP adequado.
- O controller não contém regra de IA nem regra de locação.

Conceitos aprendidos:

- Contrato de API.
- DTOs.
- Separação entre controller e aplicação.

## Desafio 4: criar memória de conversa

Hoje cada pergunta é independente. Para aprender AI Services, adicione memória por sessão.

Arquivos prováveis:

- `AssistantAiService.java`
- `AssistantConfig.java`
- DTO de request com `sessionId`

O que implementar:

- Adicionar `@MemoryId` na interface.
- Configurar `chatMemoryProvider`.
- Limitar a janela de memória, por exemplo a 10 mensagens.

Exemplo de direção:

```java
Result<String> handleRequest(@MemoryId String sessionId, @UserMessage String userMessage);
```

Critérios de aceite:

- Duas mensagens com o mesmo `sessionId` compartilham contexto.
- Duas sessões diferentes não compartilham contexto.
- A memória tem limite.

Conceitos aprendidos:

- `@MemoryId`.
- `MessageWindowChatMemory`.
- Isolamento por usuário ou sessão.
- Custo de contexto em aplicações com LLM.

## Desafio 5: criar guardrails simples antes e depois do modelo

Seu `@SystemMessage` já orienta o modelo a responder apenas sobre locação corporativa, mas prompt não é barreira de segurança. Crie validações explícitas no código.

Arquivos prováveis:

- Novo `assistant/application/AssistantGuardrailService.java`
- `AssistantAiController.java` ou um serviço intermediário
- Testes unitários

O que implementar:

- Bloquear mensagem vazia.
- Bloquear mensagem grande demais.
- Bloquear perguntas claramente fora do domínio antes de chamar a LLM.
- Validar a resposta final para não devolver conteúdo sensível, stack trace ou texto vazio.

Critérios de aceite:

- Perguntas fora do domínio recebem resposta controlada.
- Mensagens inválidas não chamam o modelo.
- Existem testes para os bloqueios.

Conceitos aprendidos:

- Guardrails de entrada.
- Guardrails de saída.
- Redução de custo evitando chamada desnecessária ao modelo.
- Segurança além de prompt engineering.

## Desafio 6: criar uma base de conhecimento simples para RAG

Agora adicione conhecimento que não deve ficar todo dentro do prompt. Exemplo: políticas da locadora.

Arquivos prováveis:

- `src/main/resources/knowledge/politica-combustivel.md`
- `src/main/resources/knowledge/documentos.md`
- `src/main/resources/knowledge/seguros.md`
- Novo `knowledge/ingestion/DocumentIngestionService.java`
- Novo `knowledge/retrieval/RagConfig.java`

O que implementar:

- Criar arquivos `.md` com regras da locadora.
- Carregar esses documentos na inicialização.
- Quebrar documentos em segmentos.
- Gerar embeddings.
- Salvar em `InMemoryEmbeddingStore` no primeiro momento.
- Configurar `ContentRetriever` no `AiServices`.

Critérios de aceite:

- Perguntas sobre política de combustível usam os documentos.
- Se a informação não existir nos documentos, o assistente diz que não sabe.
- Você consegue explicar a diferença entre prompt fixo e contexto recuperado.

Conceitos aprendidos:

- RAG.
- Document loading.
- Splitting.
- Embeddings.
- `EmbeddingStore`.
- `ContentRetriever`.

## Desafio 7: adicionar citação de fonte no RAG

Depois que o RAG básico funcionar, adicione metadados nos segmentos.

O que implementar:

- Cada documento deve ter `source`, `title` e `category`.
- A resposta deve mencionar a fonte usada quando responder com base nos documentos.

Critérios de aceite:

- Uma resposta sobre seguro informa algo como `Fonte: seguros.md`.
- Segmentos recuperados têm metadados.
- O modelo é instruído a não inventar fonte.

Conceitos aprendidos:

- Metadata em RAG.
- Rastreabilidade.
- Redução de alucinação.

## Desafio 8: trocar `InMemoryEmbeddingStore` por pgvector

`InMemoryEmbeddingStore` é ótimo para aprender, mas perde dados ao reiniciar. Use PostgreSQL com pgvector como segundo passo.

Arquivos prováveis:

- `docker-compose.yml`
- `knowledge/config/VectorStoreConfig.java`
- `application.yaml`

O que implementar:

- Subir PostgreSQL com extensão pgvector.
- Configurar `PgVectorEmbeddingStore`.
- Validar a dimensão do embedding model.
- Criar health check simples para busca vetorial.

Critérios de aceite:

- Os documentos continuam pesquisáveis após reiniciar a aplicação.
- A dimensão configurada no store bate com a dimensão do embedding model.
- Uma query de teste retorna pelo menos um resultado relevante.

Conceitos aprendidos:

- Vector store persistente.
- Dimensão de embeddings.
- Índices vetoriais.
- Health check de infraestrutura de IA.

## Desafio 9: testar sem depender de API real

Testes de unidade não devem chamar Gemini. Separe testes rápidos de testes de integração.

Arquivos prováveis:

- `src/test/java/.../QuotationServiceTest.java`
- `src/test/java/.../AssistantGuardrailServiceTest.java`
- `src/test/java/.../AssistantToolsTest.java`
- Perfil `test`

O que implementar:

- Testar domínio sem Spring.
- Testar guardrails sem LLM.
- Testar tools diretamente.
- Criar um teste de integração separado para o fluxo completo, marcado de forma que rode só quando uma variável de ambiente estiver presente.

Critérios de aceite:

- `./mvnw test` roda sem chave de API.
- Testes de integração com modelo real ficam opt-in.
- Erros de tool têm cobertura.

Conceitos aprendidos:

- Pirâmide de testes em aplicação com LLM.
- Mock de modelo.
- Testes determinísticos.
- Separação entre unitário e integração.

## Desafio 10: observar tool calls e decisões do assistente

Aplicação com LLM precisa de rastreabilidade. Adicione logs controlados.

O que implementar:

- Logar quando uma tool for chamada.
- Logar categoria e dias normalizados, sem dados sensíveis.
- Logar quando guardrail bloquear uma entrada.
- Logar quando RAG recuperar documentos, incluindo `source` e score.

Critérios de aceite:

- Dá para debugar por que uma resposta foi gerada.
- Logs não mostram API key.
- Logs não despejam prompt inteiro sem necessidade.

Conceitos aprendidos:

- Observabilidade.
- Auditoria de tool calling.
- Debug de RAG.

## Desafio 11: criar uma tool com acesso a dados externos internos

Depois da cotação fixa, simule um repositório de veículos disponíveis.

Arquivos prováveis:

- `rental/domain/Vehicle.java`
- `rental/application/VehicleAvailabilityService.java`
- `assistant/tools/VehicleAvailabilityTools.java`

O que implementar:

- Criar lista em memória de veículos por categoria.
- Criar tool `checkAvailability`.
- A tool deve receber categoria e período.
- O modelo deve consultar disponibilidade antes de afirmar que há veículos.

Critérios de aceite:

- Perguntas sobre disponibilidade chamam a nova tool.
- Categoria inválida retorna erro amigável.
- A tool não altera estado.

Conceitos aprendidos:

- Múltiplas tools.
- Tool read-only.
- Separação entre cálculo e consulta.

## Desafio 12: desenhar uma integração MCP conceitual

Não implemente MCP logo no início. Primeiro desenhe uma fronteira clara.

Cenário sugerido:

- Um futuro MCP server expõe ferramentas internas da locadora, como disponibilidade, contratos, filiais e políticas.

O que documentar ou prototipar:

- Quais tools seriam expostas.
- Quais seriam read-only.
- Quais teriam efeito colateral.
- Como filtrar tools perigosas.
- Como aplicar timeout.
- Como registrar falhas sem expor dados sensíveis.

Critérios de aceite:

- Existe uma tabela com nome da tool, entrada, saída, risco e permissão.
- Tools administrativas ficam fora do assistente público.
- Você sabe explicar por que MCP é uma fronteira de confiança.

Conceitos aprendidos:

- MCP.
- Tool provider.
- Segurança em integrações externas.
- Allowlist de capacidades.

## Ordem recomendada

Siga nesta ordem:

1. Java e segredo em configuração.
2. Tool de cotação com validação.
3. Separação de domínio.
4. Contrato HTTP.
5. Memória de conversa.
6. Guardrails.
7. RAG com `InMemoryEmbeddingStore`.
8. Citação de fonte.
9. Testes sem API real.
10. pgvector.
11. Observabilidade.
12. MCP conceitual.

Essa ordem evita pular direto para RAG e vector store antes de ter base de arquitetura, testes e validação.

## Checklist de aprendizado

Ao final da trilha, você deve conseguir explicar:

- O que é um `AiService` no LangChain4j.
- Quando usar `@SystemMessage`, `@UserMessage` e `@MemoryId`.
- Como uma `@Tool` é escolhida e chamada pelo modelo.
- Por que validação dentro da tool é obrigatória.
- O que é RAG e por que ele não é apenas "colocar texto no prompt".
- O que são embeddings e vector stores.
- Por que `InMemoryEmbeddingStore` é bom para estudo e ruim para produção.
- Como testar regras de negócio sem chamar uma LLM.
- Como separar teste unitário, integração e end-to-end.
- Por que guardrails no código são diferentes de instruções no prompt.
- Por que MCP precisa de allowlist, timeout e controle de permissão.

## Sugestão de meta final

Ao terminar, o projeto pode virar um assistente de locadora corporativa com:

- Cotação validada por tool.
- Consulta de disponibilidade por tool.
- Memória por sessão.
- RAG sobre políticas internas.
- Fonte citada nas respostas.
- Guardrails de entrada e saída.
- Testes unitários sem LLM.
- Integração opcional com modelo real.
- Vector store persistente.
- Desenho preparado para MCP.
