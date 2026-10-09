# Virtual threads contra pool de plataforma, medidos

Este projeto é a mesma aplicação rodando de duas formas — com o pool de threads
de plataforma do Tomcat e com virtual threads — sob a mesma carga, para que a
diferença entre elas seja um número e não uma opinião. A troca é uma linha de
configuração: `spring.threads.virtual.enabled`.

O resultado curto, para quem não vai ler o resto: **virtual threads não deixam
nada mais rápido.** Elas removem um limite. Abaixo desse limite as duas
configurações são indistinguíveis; acima dele, a de plataforma enfileira e a
latência explode. E existe um caso em que virtual threads deixam a aplicação
**muito pior** que o pool que elas substituíram — o `synchronized` que prende o
carrier. Esse caso também está medido aqui.

## As cinco cargas

Cada endpoint isola um tipo de trabalho, para que uma diferença entre duas
corridas possa ser atribuída a algo:

| Carga | O que é | O que se espera |
| --- | --- | --- |
| `/work/downstream` | espera num serviço lento, nada mais escasso | onde virtual threads ganham |
| `/work/db` | espera dentro do pool de conexões | o pool é o teto, não as threads |
| `/work/cpu` | hashing em laço, nunca bloqueia | nenhuma diferença |
| `/work/pinned` | a espera dentro de `synchronized` | prende o carrier |
| `/work/unpinned` | a mesma espera sob `ReentrantLock` | o controle do caso acima |

## Tecnologias e bibliotecas

| Tecnologia | Para que serve aqui |
| --- | --- |
| Java 21 | virtual threads e `StructuredTaskScope` (esta em preview) |
| Spring Boot 3.5 | aplicação web; a troca de modelo de thread é configuração dele |
| Gatling 3.14 | gerador de carga, em modelo aberto (taxa de chegada fixa) |
| JFR (`jdk.jfr.consumer`) | detecção de pinning em tempo de execução, por evento |
| PostgreSQL 16 + HikariCP | a carga cujo teto é o pool, não a thread |
| Micrometer + Actuator | métricas e histograma de latência do lado do servidor |
| Testcontainers | PostgreSQL real nos testes de integração |
| JUnit 5 + AssertJ + Awaitility | testes, inclusive o de pinning |

## Pré-requisitos

- Java 21
- Docker e Docker Compose

## Como rodar

Suba o PostgreSQL:

```bash
docker compose up -d
```

Na configuração de plataforma, que é o padrão:

```bash
./gradlew bootRun
```

Com virtual threads:

```bash
./gradlew bootRun --args='--spring.profiles.active=virtual'
```

**Se for rodar o jar diretamente, o `--enable-preview` é obrigatório** — o
projeto usa `StructuredTaskScope`, que é preview no Java 21:

```bash
java --enable-preview -jar build/libs/virtual-threads-benchmark-1.0.0.jar
```

Para derrubar tudo:

```bash
docker compose down -v
```

## Endpoints

| Rota | O que faz |
| --- | --- |
| `GET /ping` | diz em que thread a request chegou — a prova de que o switch pegou |
| `GET /work/downstream?delayMs=500` | espera num serviço lento |
| `GET /work/db?delayMs=50` | espera dentro do pool de conexões |
| `GET /work/cpu?rounds=50000` | trabalho de CPU |
| `GET /work/pinned?delayMs=500` | espera dentro de `synchronized` |
| `GET /work/unpinned?delayMs=500` | a mesma espera sob `ReentrantLock` |
| `GET /work/fanout?legs=4&delayMs=50` | fan-out com structured concurrency |
| `GET /work/fanout-failure` | uma perna falha, as irmãs são canceladas |
| `GET /bench/runtime` | modelo ativo, carriers, threads de SO, heap, pool |
| `GET /bench/pinning` | quantos eventos de pinning, e em que linha do código |
| `POST /bench/pinning/reset` | zera os contadores entre corridas |

## Exemplos de uso

Confirmar qual modelo está ativo:

```bash
curl -s http://localhost:8080/ping
```

Com virtual threads a resposta traz
`VirtualThread[#56,tomcat-handler-3]/runnable@ForkJoinPool-1-worker-1`; com o
pool de plataforma, `Thread[#43,http-nio-8080-exec-2,5,main]`.

O fan-out — quatro pernas de 100 ms levam 100 ms, não 400:

```bash
curl -s 'http://localhost:8080/work/fanout?legs=4&delayMs=100'
```

Rodar uma carga e ver o que pinou:

```bash
curl -s -X POST http://localhost:8080/bench/pinning/reset
./gradlew gatlingRun --simulation=simulations.PinnedSimulation \
  -Pbench.rate=40 -Pbench.duration=20 -Pbench.delayMs=500
curl -s http://localhost:8080/bench/pinning
```

## Números medidos

Máquina local com 12 núcleos, PostgreSQL em container, Gatling na mesma
máquina. Modelo aberto — taxa de chegada fixa, não número fixo de usuários —
porque com usuários fixos a carga se autolimita e um servidor saturado fica
parecido com um rápido. 20 s por corrida, aquecimento descartado.

Como o gerador de carga divide a máquina com a aplicação, o valor absoluto é
suspeito; o que é comparável é o mesmo gerador contra as duas configurações.

Latências em milissegundos, `p50 / p95 / p99`:

| Cenário | Plataforma (200 threads) | Virtual (12 carriers) |
| --- | --- | --- |
| downstream 500 ms a 300/s | 501 / 502 / 503 | 501 / 502 / 507 |
| downstream 500 ms a 600/s | **5465 / 9989 / 10373** | **501 / 502 / 517** |
| `synchronized` 500 ms a 40/s | 502 / 503 / 507 | **7134 / 13163 / 13760** |
| `ReentrantLock` 500 ms a 40/s | 501 / 503 / 508 | 502 / 504 / 507 |
| cpu 50 mil rodadas a 200/s | 11 / 17 / 21 | 10 / 16 / 20 |
| db 50 ms a 300/s | 53 / 56 / 65 | 53 / 57 / 64 |

Threads de sistema operacional em repouso: **218 na plataforma, 30 com virtual
threads.**

### Abaixo do teto, nada muda

A 300 requisições por segundo as duas configurações respondem em 501 ms — o
tempo do próprio downstream. Vale dizer isso antes de qualquer outra coisa: a
maioria dos serviços nunca chega perto do seu teto, e para eles trocar o modelo
de thread não muda nenhum número.

O teto do pool de plataforma é `threads / tempo de bloqueio`: 200 threads contra
uma espera de 500 ms dão 400 req/s.

### Acima do teto, 10x na mediana e 20x na cauda

A 600/s a plataforma **não falha** — ela enfileira. Zero erros, 12 mil
requisições atendidas, e a mediana sobe de 501 ms para 5465 ms. O p99 vai a
10373 ms contra 517 ms. É o modo de falha mais desagradável que existe, porque
nenhum log registra nada.

### O pinning inverte o resultado

A mesma espera, tomada dentro de um bloco `synchronized`: no Java 21 a thread
virtual não pode ser desmontada enquanto segura um monitor, então ela **prende o
carrier**. Com 12 carriers e 500 ms de espera o teto cai para 24 req/s.

Medido: a 40/s a configuração virtual entregou **24 req/s** com mediana de
7134 ms, enquanto o pool de plataforma entregou 38 req/s a 502 ms. Virtual
threads deixaram a aplicação **14 vezes pior** que a configuração que elas
substituíram. Trocar `synchronized` por `ReentrantLock`, sem mudar mais nada,
devolve os 502 ms e zera os eventos de pinning.

O `GET /bench/pinning` contou **800 eventos em 800 requisições** — um por
requisição — e apontou a linha: `PinningWorkload.sleep:75`.

### O teto é o pool de carriers

Subindo os carriers com `-Djdk.virtualThreadScheduler.parallelism`, com a mesma
carga pinada:

| Carriers | Taxa alvo | Obtido | p50 | Teto calculado |
| --- | --- | --- | --- | --- |
| 12 | 40/s | 23–24/s | 5495–7134 ms | 24/s |
| 48 | 40/s | 38/s | 504 ms | 96/s |
| 48 | 150/s | 94/s | 4684 ms | 96/s |

Os valores obtidos batem com `carriers / tempo de bloqueio` com 2% de erro, o
que confirma o mecanismo. Mas note a segunda linha: **a 40/s com 48 carriers
houve 600 eventos de pinning e nenhum dano.** Pinning por si não é o problema —
pinning acima do teto de carriers é. E subir carriers devolve threads de sistema
operacional, de 30 para 44–66: o paliativo tem o custo exato da vantagem que
virtual threads traziam.

### Limites que continuam valendo

Na carga de CPU as duas configurações são iguais (10 e 11 ms de mediana). Uma
thread que nunca bloqueia nunca cede o carrier, então o teto é o número de
núcleos nos dois casos. Ter um milhão de threads não muda isso.

Na carga de banco, também iguais (53 ms nas duas). O teto ali é o pool de 20
conexões, e virtual threads não criam conexões. A requisição que antes esperava
por uma thread do Tomcat agora espera por uma conexão: a fila mudou de lugar,
não desapareceu.

## Structured concurrency

`StructuredTaskScope` dá o que um executor cru não dá: o escopo não pode ser
abandonado antes das tarefas filhas terminarem, a primeira falha cancela as
irmãs, e o prazo vale para o fan-out inteiro em vez de valer por perna. Medido
nos endpoints: quatro pernas de 100 ms levam 100 ms, e uma falha com três pernas
de 1 s retorna em 14 ms em vez de esperar o segundo inteiro.

O custo é real e está declarado: é API em preview no Java 21, exige
`--enable-preview` para compilar e para rodar, e não compila no Java 24 ou
superior, onde a API foi redesenhada. A toolchain fixada em 21 é o que impede
isso de ser uma surpresa.

## Testes

15 testes, dos quais 6 não precisam de nenhuma infraestrutura.

```bash
./gradlew test
```

O teste central é o de pinning: 16 threads virtuais bloqueando dentro de
`synchronized` produzem exatamente 16 eventos JFR, e as mesmas 16 sob
`ReentrantLock` produzem zero. Os eventos são filtrados por stack trace, porque
outros pontos da JVM de teste também pinam e a contagem precisa ser exata.

Há também um par de testes que verifica que a troca de configuração realmente
troca algo — um benchmark que comparasse duas configurações secretamente iguais
produziria resultados consistentes e inúteis.

Build completo:

```bash
./gradlew clean build
```
