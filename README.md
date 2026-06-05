# find-dog-ia

This project intent to create AI assistant that helps people to find a dog to adopt.

## Idea

I use mainly integration between java and langchain4j to generate AI models integration.

## Architecture

I use RAG architecture in this project in this way: 
 
```
User Message
     ↓
PreferenceExtractor (LLM)
     ↓
UserProfile Memory
     ↓
DogAssistant (LangChain4j)
     ↓
PersonalizedRetrievalAugmentor
     ↓
Hybrid Retrieval
   ├─ Neo4j Graph Retrieval
   └─ Vector Retrieval (EmbeddingStore)
     ↓
Custom Reranking
     ↓
Structured Context Builder
     ↓
LLM
```

## Local use configuration

### You need have a docker-compose installed

```shell script
docker-compose -f ./config/environment-compose.yaml up -d
```

### User configuration

I've configured a authentication server with keycloak, but you need create a user at:


[Local keycloak](http://localhost:8088/admin/master/console/#/find-dog-ia)

#### obs: to local configuration i've used user and password "admin"


### You need have a local llama.cc instance

```shell Strinm
/opt/llama.cpp/build/bin/llama-server \
  -m ~/models/qwen/Qwen2.5-7B-Instruct-Q4_K_M.gguf \
  --host 127.0.0.1 \
  --port 9999 \
  -c 16384 \
  -t 8 \
  --parallel 4 \
  --cont-batching
```

#### obs: Use LLM model locally can be really slow, to real product you could use a real LLM  provider like 'OpenAI' or 'Deepseek'

### Running the application in dev mode

You can run your application in dev mode that enables live coding using:

```shell script
./gradlew quarkusDev
```

> **_NOTE:_**  Quarkus now ships with a Dev UI, which is available in dev mode only at <http://localhost:8080/q/dev/>.

## Packaging and running the application

The application can be packaged using:

```shell script
./gradlew build
```

It produces the `quarkus-run.jar` file in the `build/quarkus-app/` directory.
Be aware that it’s not an _über-jar_ as the dependencies are copied into the `build/quarkus-app/lib/` directory.

The application is now runnable using `java -jar build/quarkus-app/quarkus-run.jar`.

If you want to build an _über-jar_, execute the following command:

```shell script
./gradlew build -Dquarkus.package.jar.type=uber-jar
```

The application, packaged as an _über-jar_, is now runnable using `java -jar build/*-runner.jar`.

## Creating a native executable

You can create a native executable using:

```shell script
./gradlew build -Dquarkus.native.enabled=true
```

Or, if you don't have GraalVM installed, you can run the native executable build in a container using:

```shell script
./gradlew build -Dquarkus.native.enabled=true -Dquarkus.native.container-build=true
```

You can then execute your native executable with: `./build/find-dog-ia-1.0.0-SNAPSHOT-runner`

If you want to learn more about building native executables, please consult <https://quarkus.io/guides/gradle-tooling>.
''
