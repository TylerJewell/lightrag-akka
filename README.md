# lightrag-akka

Ask it a question in two halves — the specific words and the broad theme — and it searches
two indexes, one of things and one of connections between things, then interleaves what
each found into a single ordered answer with the passages it came from.

A port of [HKUDS/LightRAG](https://github.com/HKUDS/LightRAG) onto **Akka**, built with
**Akka Specify**.

---

## Where it came from

`HKUDS/LightRAG` is a Python system that builds a graph of things and connections out of
documents, then answers questions from it. This port rebuilds the answering half — the
part that turns a question into an ordered set of things, connections and passages — to
find out how precisely a system has to be written down before it can be rebuilt on a
different stack.

The specifications the port was built from are in `akka-specify-harness` under
`lightrag-port/`. It is private for now.

---

## HKUDS/LightRAG → this port

📉 1,292 Python lines → **621 Java lines**<br>
📁 802 files → **25 files**<br>
🎯 25 of 25 questions answered identically → **25 of 25**<br>
⚡ 978,100 → **250,400** nanoseconds, answering from both indexes<br>
⚡ 870,600 → **113,700** nanoseconds, answering from the things index alone<br>
⚡ 4,154,300 → **3,713,800** nanoseconds, answering over a sixty-thing graph<br>
⚡ 43,200 → **1,400** nanoseconds, refusing a question with no keywords<br>
🧪 not measured → **62** tests<br>
💾 not measured → not measured<br>
🚀 not measured → not measured

The original needs a model account and an embedding account before it will answer
anything, so its memory use and start-up time were never observed.

Every number here is reproduced by `mvn test -Dtest=BenchmarkTest`, which compares this
port's answers against the original's recorded ones and then writes
`target/bench-java.json`.

Full method and the numbers that did *not* make this list: `lightrag-port/bench/REPORT.md`
in the specifications repository.

---

## What it took to build

⏱️ **1.4 hours** from the first command to the published repository, **1.4** of them active<br>
💬 **390** exchanges with the model<br>
✍️ **425,370** tokens written by the model, **103.2M** counting everything sent and re-sent<br>
🙋 **0** questions to a human<br>
🧪 **62** tests

---

## What it does

- **A question arrives already split in two.** The specific words search an index of
  things; the broad theme searches an index of connections. Which of the two searches runs
  is decided by the mode you ask for *and* by whether you gave any words of that kind —
  asking for the specific search with no specific words runs the broad one instead.
- **The two result lists are interleaved, not scored.** One from the first list, one from
  the second, one from the first, skipping anything already taken. Nothing is re-ranked,
  and where the two lists disagree the first one wins the position.
- **A thing found through the connections index is not the same record as the same thing
  found directly.** Only the direct search attaches how well-connected it is.
- **Each list is cut to fit a budget, and only whole records survive.** The cut counts the
  text as it will actually be sent, and a record that would not fit whole is dropped
  rather than shortened.
- **A passage named by several things belongs to the first of them.** How many things
  named it is still counted, and that count moves it to the front of the one list it stays
  in.
- **Each thing is asked for a share of passages that shrinks down the list.** The first is
  asked for five, the last for one, and whatever any of them cannot fill is handed back to
  whoever still has passages left.
- **Citations are numbered by how often a file is quoted**, among the passages that
  survived — so the same passage carries a different citation number under a different
  cut.

---

## Design decisions

**Round robin.** The two searches rank their results by different things — one by how well
the words matched, the other by how many connections a thing has — and there is no honest
way to put two such numbers on one scale. Taking one from each list in turn keeps both
orderings intact instead of inventing a third.

**One algorithm, two settings.** Picking passages from things and picking them from
connections is the same procedure with two small differences, and the original writes it
out twice. Writing it once with those two differences as settings makes it impossible for
one copy to be fixed and the other forgotten.

**Sharded storage that still gives one answer.** Everything one machine holds has a size
limit, so the search index is spread across eight pieces. Every piece is searched in full
and each returns its own best, so the merged result is exactly what one unsplit search
would have given — the number of pieces is about how much fits, never about the answer.

**Names are encoded before they become addresses.** A thing extracted from a document can
be called anything, and one punctuation mark is not allowed in an address here — a name
carrying it makes the request hang for ten seconds and then fail without saying why.
Encoding every name removes the whole problem rather than the one character.

**A full piece says so.** Filling one piece past its limit is refused by the storage
underneath in a way nobody can read, so each piece refuses first, in a message that names
its limit and how far over the request would have gone.

---

## Running it — the short path

You do not need Java, Maven, or the Akka CLI installed. Akka Specify installs them for you.

**1. Install Akka Specify** in Claude Code:

```
/plugin marketplace add akka/ai-marketplace
/plugin install akka@akka-ai-marketplace
```

Restart Claude Code when it asks.

**2. Give it this prompt:**

> Clone https://github.com/TylerJewell/lightrag-akka into a new directory and open it.
> Then run /akka:setup to install everything this project needs, and /akka:build to
> compile it, run the tests, and start it locally.

**3. Ask it something** at http://localhost:9038.

---

## Running it — the developer path

### Requirements

- Java 21 or newer
- Maven 3.9 or newer
- An Akka download token — run `akka code token` once

No model account is needed. Nothing here calls a model.

### Start the service

```bash
mvn compile
akka local run
```

The service starts on **port 9038**.

### Put something in, then ask about it

```bash
curl -X POST localhost:9038/index/chunk -H 'Content-Type: application/json' \
  -d '{"chunkId":"c1","content":"The rocket family carried the crews.","filePath":"history.md"}'

curl -X POST localhost:9038/index/node -H 'Content-Type: application/json' \
  -d '{"entityName":"SATURN","entityType":"THING","description":"a rocket family",
       "filePath":"history.md","sourceIds":["c1"]}'

curl -X POST localhost:9038/index/edge -H 'Content-Type: application/json' \
  -d '{"src":"APOLLO","tgt":"SATURN","weight":2.0,
       "description":"the programme flew on the rocket family",
       "filePath":"history.md","sourceIds":["c1"]}'

curl -X POST localhost:9038/retrieve -H 'Content-Type: application/json' \
  -d '{"query":"what carried the crews","mode":"hybrid",
       "lowLevelKeywords":["rocket family"],"highLevelKeywords":["programme"]}'
```

---

## Configuration

| Variable | Default | Notes |
|---|---|---|
| `HTTP_PORT` | 9038 | set in `application.conf` for local runs |

Everything else is per request. `mode` is one of `local`, `global`, `hybrid`, `mix`;
`topK`, `chunkTopK`, `maxEntityTokens`, `maxRelationTokens`, `chunkTokenBudget` and
`relatedChunkNumber` all default to the original's values and can be sent with any
question.

---

## Where it differs from HKUDS/LightRAG

Everything not listed here behaves the same way on purpose, including the parts that look
like mistakes.

- **Splitting the question into two halves.** LightRAG asks a language model to split a
  question into specific words and a broad theme. This port takes the two lists from the
  caller, which the original also allows, so that the same question always retrieves the
  same things.
- **Writing the answer.** LightRAG hands the retrieved context to a language model and
  returns prose. This port returns the context and stops there, because the part being
  compared is what was retrieved and in what order.
- **Turning text into a vector.** LightRAG calls an embedding service. This port projects
  text onto 128 dimensions by hashing its words, so a question gives the same result on
  every run. It is not a meaning-aware vector and is not offered as one; a caller with a
  real embedding service would replace it.
- **Ranking by similarity.** LightRAG passes this to a separate package outside its own
  repository, so it has no answer of its own for what happens when two things score
  exactly the same. This port ranks by score and then by name, because that is the only
  order that survives the index being split into pieces.
- **Re-ranking passages.** LightRAG will call a second model to re-order passages before
  cutting the list, when one is configured, and then drop anything scoring below a floor.
  This port has no such step. With no re-ranking model configured, which is what a fresh
  install has, the original does not take that path either.
- **Picking passages by similarity.** LightRAG ships two ways of choosing which passages a
  thing contributes: by how often each is quoted, or by how close each is to the question.
  It ships the second as its default and falls back to the first when no embedding service
  is configured — checked by running it, and in that state the two give the identical
  answer. This port implements the first only.
- **Working out the passage budget.** LightRAG subtracts the size of its own instruction
  text, the retrieved things and connections, and the question from a total. This port
  takes the budget as a number with the question, because reproducing the subtraction
  means reproducing the instruction text, which is not retrieval.
- **Storage.** LightRAG supports eleven storage systems. This port has one, and it caps
  each piece of the search index at 64 entries.
- **The size of a thing's connection list.** This port keeps a thing's neighbours in the
  record for that thing, and does not limit how many there can be. A thing with tens of
  thousands of connections would eventually be too large to store. LightRAG has no such
  limit because its graph lives elsewhere. Not checked — no graph that large was built.
- **Who may write.** LightRAG's server can require a key before anything is indexed. This
  port requires nothing, because the slice being ported is the retrieval, not the service
  around it, and the repository is published private.
- **Reading it back as one block.** LightRAG assembles the retrieved context into a single
  block of text with its own headings. This port returns the same content as structured
  fields and does not assemble the block, because the headings are instruction text rather
  than retrieval.

---

## Licence

`HKUDS/LightRAG` is MIT, © 2025 LightRAG Team. This port reimplements the behaviour
without copied source; see `ACKNOWLEDGEMENTS.md`.
