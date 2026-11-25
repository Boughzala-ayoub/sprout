# 🌱 Sprout

[![CI](https://github.com/Boughzala-ayoub/sprout/actions/workflows/ci.yml/badge.svg)](https://github.com/Boughzala-ayoub/sprout/actions/workflows/ci.yml)

A **React-style, Spring-flavored frontend framework** — you write components in
**Java**, [TeaVM](https://teavm.org) compiles them to **WebAssembly**, and they run
in the browser. Built to learn how these frameworks actually work inside.

<p align="center">
  <img src="docs/demo.webp" alt="Sprout demo: React-style, Spring-flavored Java UI compiled to browser WasmGC" width="880" />
</p>

TeaVM is the black box that turns Java bytecode into wasm. Everything *interesting*
— the component model, reactivity, virtual-DOM diffing, DI, routing — is Sprout,
in code you can read.

The build targets **WasmGC** (`targetType=WEBASSEMBLY_GC`), so Java objects are
real wasm heap objects collected by the browser's GC — no bundled GC, no linear-
memory heap. That needs Chrome 119+, Firefox 120+, or Safari 18.4+. Switch back
to JS by dropping `<targetType>` from `demo/pom.xml`.

## Project layout
Sprout is a library you depend on, the way a React app depends on `react`:
```
sprout-core/        the framework — the package your app depends on (like "react")
sprout-processor/   build-time DI codegen (scans @Service/@Autowired)
sprout-archetype/   `mvn archetype:generate` scaffolder (Java's create-react-app)
demo/               an app that DEPENDS ON sprout-core — the template for a new project
```
`sprout-core` and `sprout-processor` are separate modules because a library and
its annotation processor must be built *before* the app that consumes them.

## Scaffold a new app (Java's `create-react-app`)
One-time: install Sprout into your local Maven repo:
```bash
mvn -N install                                                     # parent pom
mvn -DskipTests -pl sprout-core,sprout-processor,sprout-archetype install
```
Then scaffold a fresh app anywhere with one command:
```bash
mvn archetype:generate \
  -DarchetypeGroupId=dev.sprout -DarchetypeArtifactId=sprout-archetype -DarchetypeVersion=0.1.0 \
  -DgroupId=com.example -DartifactId=myapp -Dpackage=com.example.myapp -DinteractiveMode=false

cd myapp && java DevServer.java   # http://localhost:8080 — a running counter app
```
The generated project has a starter component, its own `DevServer.java` (watch +
rebuild), and a `pom.xml` already wired with `sprout-core`, the DI processor, and
the TeaVM plugin. Edit `App.java`, save, refresh.

Prefer to wire it by hand? `demo/pom.xml` is the copy-paste template — add
`dev.sprout:sprout-core` (like adding `react`), the `sprout-processor` (provided),
`teavm-classlib` (provided), and the `teavm-maven-plugin`.

## Feature map

| Concept (React / Spring)         | Sprout                              | File            |
|----------------------------------|-------------------------------------|-----------------|
| Component with `render()`        | `Component` base class              | `Component.java`|
| JSX / `createElement`            | `VNode` + DSL (`div`, `ul`, `input`…) | `VNode.java`  |
| `props`                          | `Props`                             | `Props.java`    |
| `useState` / signals             | `State<T>` (dependency-tracked)     | `State.java`    |
| Fiber tree + keyed reconciler    | `Instance`                          | `Instance.java` |
| `SpringApplication.run(...)`     | `Sprout.run(...)`                   | `Sprout.java`   |
| `ApplicationContext`             | `AppContext`                        | `AppContext.java`|
| `@Service` component scan        | `@Service` + generated `SproutBoot` | `SproutProcessor.java` |
| `@Autowired` field injection     | `@Autowired` (wired at build time)  | `Autowired.java`|
| React Router                     | `Router` (History API, `:params`, guards) | `Router.java` |
| `fetch`                          | `Http.get/post/put/patch/delete`    | `Http.java`     |
| `JSON.parse` / `JSON.stringify`  | `Json` read, `Json.obj()` write     | `Json.java`     |
| React Query's loading/error      | `Resource<T>`                       | `Resource.java` |
| Error boundary                   | built into the reconciler           | `Instance.java` |
| Vite / CRA live reload           | `DevServer` (save → the page reloads) | `DevServer.java` |

## What's implemented
- **Component composition, props, reactive state** — each component keeps its own
  state across re-renders; reading `state.get()` in render subscribes it.
- **Keyed reconciliation** — `.key(...)` gives list items stable identity. The
  demo proves it: mark a todo done, reverse the list, and it stays done at its new
  position because its component instance (and local state) is preserved, not
  rebuilt.
- **Build-time dependency injection** — annotate a service `@Service` and a field
  `@Autowired`; the annotation processor generates `sprout.generated.SproutBoot`,
  which builds the `AppContext` and sets the fields. No runtime reflection (that's
  why it works under TeaVM), no manual `provide()` calls.
- **Batched updates** — two `State.set` calls in one handler repaint once. Writes
  queue a render at the end of the task instead of rendering on the spot.
- **Subscriptions that end** — reading a State subscribes; re-rendering and
  unmounting unsubscribe. A State held by an `@Service` singleton does not keep
  re-rendering pages that are long gone.
- **Error boundaries** — a component whose `render()` or event handler throws is
  replaced in place by a `.sprout-error` node and logged. The rest of the page
  keeps working instead of going blank.
- **Async data loading** — `Http.get(url).send(cb)` with real status/network error
  handling, `Json` over the browser's parser, and `Resource<T>` for the
  loading/loaded/failed triple. JSON to *send* is built with `Json.obj()`, whose
  escaping is the browser's `JSON.stringify`, not hand-rolled string quoting.
- **Controlled inputs and checkboxes** — `value` and `checked` are written as DOM
  *properties* on every patch, compared against the live element rather than the
  previous vnode. That is the difference between a controlled input and one that
  silently keeps a rejected edit on screen.
- **Fragments and lifecycle** (`onMount`/`onUnmount`).

### Views at a glance
```java
form(
    input().attr("value", draft.get()).attr("aria-label", "New todo").onInput(draft::set),
    button("Add").attr("type", "submit")
                 .attr("disabled", draft.get().isBlank())   // boolean: adds or removes it
).onSubmit(this::add)                                       // preventDefault applied; Enter works

span(text).cls("text", done ? "done" : null)                // nulls skipped, calls accumulate
```
`.attr(name, boolean)` exists because `.attr("disabled", "false")` leaves a button
disabled — HTML reads *any* value as true. `.cls(...)` takes varargs and appends,
so a conditional class needs no string surgery at the call site.

### State at a glance
```java
private final State<List<Todo>> todos = new State<>(new ArrayList<>());

todos.set(next);                        // replace — skipped when equal
todos.update(list -> ...);              // replace, computed from the current value
todos.mutate(list -> list.add(todo));   // change in place, then re-render
```
`mutate` exists for a sharp edge: `set` compares with `equals` and bails when
nothing changed, so mutating a list and handing it back to `set` does *nothing
at all*. Without `mutate` every edit copies the whole collection.

### Routing at a glance
```java
Map<String, Function<Props, VNode>> routes = new LinkedHashMap<>();
routes.put("/", TodoPage::of);              // a page's own of(Props) fits as-is
routes.put("/data/:index", FactPage::of);   // ":index" arrives in the page's Props
routes.put("*", NotFoundPage::of);

Router.of(new Props().set("routes", routes)
                     .set("guard", path -> loggedIn() ? null : "/login"));
```
Real URLs via the History API — `/data/3`, not `#/data/3`. `link("/data/3", "…")`
renders a genuine `<a href>` (ctrl-click still opens a tab) and navigates in-page
on a plain click. The most specific pattern wins, so `/data/new` beats
`/data/:index`. Nest by giving a child Router a `base` prop.

### Fetching at a glance
```java
private final Resource<List<Todo>> todos = new Resource<>();

protected void onMount() {
    todos.load(Http.get("/api/todos"), res -> parse(res.json()));
}

// Sending: Json.obj() builds a real JS object, so JSON.stringify does the escaping.
Http.post("/api/todos").json(Json.obj().put("text", text).put("done", false))
    .send(res -> { ... });

public VNode render() {
    if (todos.loading()) return p("Loading…");
    if (todos.failed())  return p(todos.error()).cls("error");
    return ul(...);
}
```

### DI at a glance
```java
@Service public class GreetingService { public String greet(String n){ return "Hi "+n; } }

@Route("/about") public class AboutPage extends Component {
    @Autowired public GreetingService greeting;   // set by generated code, must be public
    public VNode render() { return p(greeting.greet("dev")); }
}

// Main — no manual wiring:
Sprout.run(App.of(new Props()), "app", SproutBoot.context());
```

## Run it (dev mode: watch, rebuild, live reload)
```bash
java DevServer.java          # http://localhost:8080   (pass a port to change it)
```
`DevServer.java` is the whole toolchain: a JDK `HttpServer` that serves
`demo/target/web`, watches `sprout-core` and `demo` sources, reruns Maven+TeaVM
on every save, and **reloads the open page itself** when the build succeeds
(~10s for a source change here). No Node, no npm, no dependencies — the JDK runs
it straight from source. Edit a `.java` file, save, and watch the browser.

The reload is a 500ms poll of `/__sprout/build`, injected into `index.html` *as
it is served*. The built `index.html` never contains it, so production still
ships exactly one line of hand-written JavaScript. A failed build deliberately
does not bump the counter: reloading into the previous build only confuses.
Everything is served `Cache-Control: no-store`, without which a reload happily
serves the `classes.wasm` the build just replaced.

One-off build without the watcher:
```bash
mvn process-classes          # dev build   -> demo/target/web
mvn -Pprod package           # prod build  -> demo/target/web, deployable as-is
```

The demo app routes: **`/`** (keyed list, reorder, controlled input, child
components), **`/about`** (`@Autowired` services + two independent counters),
**`/data`** (HTTP fetch through `Resource`), **`/data/:index`** (a route param),
and anything else (the `*` route).

## Tests
```bash
mvn test                              # 32 tests: JVM + headless Chrome
mvn test -Dsprout.browserTests=false  # JVM only (CI without a browser)
```
Two layers, because the framework spans two runtimes:
- **JVM** — route matching and virtual-tree building are pure logic, so plain JUnit.
- **Browser** — subscriptions, batching, keyed reconciliation, and the error
  boundary are compiled to WasmGC by `teavm-junit` and asserted in headless
  Chrome, on the same target the app ships on. Testing the reconciler anywhere
  else would prove nothing: the focus bug these tests cover only exists in a
  real DOM.

TeaVM launches the browser as bare `chrome`, so it must be on `PATH`; the pom
adds the standard Windows install directory, overridable with `-Dchrome.dir=…`.

## Shipping it
`mvn -Pprod package` turns on TeaVM's `FULL` optimizer and drops debug info,
source maps, and the copied `.java` sources. `demo/target/web` is then a plain
static directory — upload it anywhere:

| file | dev | prod | gzipped |
|------|-----|------|---------|
| `classes.wasm`         | 439 KB | 300 KB | 95 KB |
| `wasm-gc-runtime.js`   | 37 KB  | 37 KB  | 8 KB  |
| whole directory        | 1.6 MB | 342 KB | — |

Your host must serve `.wasm` as `application/wasm` (`WebAssembly.compileStreaming`
rejects anything else) and should have gzip or brotli on — that is ~86–95 KB over the
wire (depending on demo features enabled) for the framework, app, and Java class library together.

### The one line of JavaScript
`wasm-gc-runtime.js` is not optional and not removable: WebAssembly has no direct
DOM access, so every `document.createElement` your Java makes crosses into JS
through TeaVM's bridge. It ships inside `teavm-core.jar` and is unpacked by the
build — you never edit it. Beyond that, the only hand-written JS in this repo is
the boot call in `index.html`:
```html
<body onload="TeaVM.wasmGC.load('/teavm/classes.wasm').then(t => t.exports.main([]))">
```
Routes need every URL to return `index.html`. `DevServer` does that; in
production it is one rewrite rule. Asset paths are absolute (`/teavm/…`) for the
same reason — `/data/3` must not look for `/data/teavm/classes.wasm`.

### Two WasmGC potholes
Both cost real debugging time here, so they are worth knowing before you hit them:
- **Casting between JSO types traps.** `(MouseEvent) event` compiles and then dies
  at runtime with `illegal cast`. Read the fields with a one-line `@JSBody`
  instead — `VNode.isPlainLeftClick` and all of `Json` do exactly that.
- **Passing a null String across the JS boundary traps.** `xhr.send(null)` is a
  crash; call the no-arg overload instead (`Http.send`).

Both surface as an uncatchable `RuntimeError`, not a Java exception — the error
boundary cannot save you from either. Build with debug info and use
`TeaVM.wasmGC.load(url, {stackDeobfuscator: {enabled: true}})` to get Java frames
in the stack trace.

## Deliberately deferred (learning scope)
Each shortcut is marked with a `ponytail:` comment at its spot in the code.
- `@Autowired` fields must be **public** (generated wiring, no reflection). Per-
  package injectors would allow package-private; constructor injection would allow
  services with args. Neither is needed yet.
- Move-minimized (LIS) list diffing — the reconciler only touches children that
  are out of place, which is enough until a list is big enough to measure.
- `Json` maps by hand in both directions — `put("text", t)` going out,
  `get("text").asString()` coming back — because TeaVM has no reflection. A
  record mapper would mean generating one in the annotation processor; hand
  mapping is fine until an API has enough fields to make it tedious.
- One `Json.Builder` type for both objects and arrays, so `put` on an array is
  nonsense the compiler cannot catch. Two types the day that bites.
- Live reload polls every 500ms rather than holding an SSE or websocket open: no
  executor to configure and the client is one `fetch`. It reloads the page rather
  than swapping state in place, which a Java→wasm build cannot do anyway.
- No SSR or prerendering: the page is blank until the wasm loads, so this suits
  apps behind a login, not SEO-facing pages.
