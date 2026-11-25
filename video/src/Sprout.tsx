import React from "react";
import {
  AbsoluteFill,
  Sequence,
  interpolate,
  spring,
  useCurrentFrame,
  useVideoConfig,
} from "remotion";

/* ---------------------------------------------------------------- theme */

const BG = "#06090b";
const PANEL = "#0d1317";
const LINE = "#1b262b";
const INK = "#e9f2ef";
const MUTED = "#7c908a";
const GREEN = "#5ef2a4";
const AMBER = "#f0a63a";
const VIOLET = "#c792ea";
const SKY = "#7dd3fc";

const SANS =
  '"Segoe UI Variable Display", "Segoe UI", Inter, system-ui, sans-serif';
const MONO = '"Cascadia Code", "Consolas", "JetBrains Mono", monospace';

/* ------------------------------------------------------------- helpers */

/** Fade + rise entrance, delayed by `delay` frames. */
const useRise = (delay: number, distance = 26) => {
  const frame = useCurrentFrame();
  const { fps } = useVideoConfig();
  const s = spring({
    frame: frame - delay,
    fps,
    config: { damping: 200, mass: 0.6 },
    durationInFrames: 24,
  });
  return {
    opacity: s,
    transform: `translateY(${(1 - s) * distance}px)`,
  } as const;
};

/** Crossfaded scene shell: fades in over 12f, out over the last 12f. */
const Scene: React.FC<{ dur: number; children: React.ReactNode }> = ({
  dur,
  children,
}) => {
  const frame = useCurrentFrame();
  const opacity = interpolate(
    frame,
    [0, 12, dur - 12, dur],
    [0, 1, 1, 0],
    { extrapolateLeft: "clamp", extrapolateRight: "clamp" }
  );
  return (
    <AbsoluteFill
      style={{
        opacity,
        padding: "96px 128px",
        justifyContent: "center",
        fontFamily: SANS,
        color: INK,
      }}
    >
      {children}
    </AbsoluteFill>
  );
};

const Kicker: React.FC<{ children: React.ReactNode; delay?: number }> = ({
  children,
  delay = 0,
}) => (
  <div
    style={{
      ...useRise(delay, 12),
      fontSize: 22,
      letterSpacing: "0.36em",
      textTransform: "uppercase",
      color: GREEN,
      fontWeight: 600,
      marginBottom: 28,
    }}
  >
    {children}
  </div>
);

/* --------------------------------------------------- java syntax colors */

const TOKEN =
  /(\/\/[^\n]*)|("(?:[^"\\]|\\.)*")|(@\w+)|\b(public|private|protected|final|static|class|extends|implements|return|new|void|null|true|false|this|import|package)\b|\b([A-Z]\w*)\b|\b(\d+)\b/g;

const COLORS = [MUTED, AMBER, VIOLET, SKY, GREEN, AMBER];

/** Highlight Java, revealing only the first `chars` characters (typewriter). */
const highlight = (code: string, chars: number): React.ReactNode[] => {
  const out: React.ReactNode[] = [];
  let last = 0;
  let used = 0;
  let key = 0;

  const push = (text: string, color?: string) => {
    if (used >= chars) return;
    const slice = text.slice(0, chars - used);
    used += slice.length;
    out.push(
      <span key={key++} style={color ? { color } : undefined}>
        {slice}
      </span>
    );
  };

  TOKEN.lastIndex = 0;
  let m: RegExpExecArray | null;
  while ((m = TOKEN.exec(code))) {
    push(code.slice(last, m.index));
    const groupIndex = m.slice(1).findIndex((g) => g !== undefined);
    push(m[0], COLORS[groupIndex]);
    last = m.index + m[0].length;
  }
  push(code.slice(last));
  return out;
};

/* -------------------------------------------------------------- scene 1 */

const Hook: React.FC = () => {
  const line1 = useRise(10);
  const line2 = useRise(20);
  const sub = useRise(38);
  return (
    <Scene dur={S1}>
      <Kicker>🌱 Sprout</Kicker>
      <div style={{ fontSize: 118, fontWeight: 800, letterSpacing: "-0.045em", lineHeight: 1.02 }}>
        <div style={line1}>Write your frontend</div>
        <div style={line2}>
          in <span style={{ color: GREEN }}>Java</span>.
        </div>
      </div>
      <div style={{ ...sub, marginTop: 40, fontSize: 34, color: MUTED, letterSpacing: "-0.01em" }}>
        React's component model. Spring's wiring. Compiled to WebAssembly.
      </div>
    </Scene>
  );
};

/* -------------------------------------------------------------- scene 2 */

const CODE = `public class Counter extends Component {

    private final State<Integer> count = new State<>(0);

    @Override
    public VNode render() {
        return div(
            button("−1").onClick(() -> count.set(count.get() - 1)),
            span(" " + count.get() + " ").cls("count"),
            button("+1").onClick(() -> count.set(count.get() + 1))
        ).cls("counter");
    }
}`;

/** frame -> [value, which button flashes] for the live demo panel. */
const CLICKS = [
  { at: 148, delta: 1 },
  { at: 164, delta: 1 },
  { at: 180, delta: -1 },
  { at: 196, delta: 1 },
];

const CodeScene: React.FC = () => {
  const frame = useCurrentFrame();
  const typed = Math.floor(
    interpolate(frame, [16, 128], [0, CODE.length], {
      extrapolateLeft: "clamp",
      extrapolateRight: "clamp",
    })
  );
  const done = typed >= CODE.length;

  const fired = CLICKS.filter((c) => frame >= c.at);
  const value = fired.reduce((n, c) => n + c.delta, 0);
  const last = fired[fired.length - 1];
  const flash = last && frame - last.at < 7 ? Math.sign(last.delta) : 0;

  const card = useRise(8, 34);
  const panel = useRise(120, 34);

  return (
    <Scene dur={S2}>
      <Kicker>A component is just a class</Kicker>
      <div style={{ display: "flex", gap: 56, alignItems: "stretch" }}>
        <div
          style={{
            ...card,
            flex: 1,
            minWidth: 0,
            background: PANEL,
            border: `1px solid ${LINE}`,
            borderRadius: 18,
            padding: "34px 40px",
            boxShadow: "0 40px 90px -40px #000",
          }}
        >
          <div style={{ fontFamily: MONO, fontSize: 17, color: MUTED, marginBottom: 22 }}>
            Counter.java
          </div>
          <pre
            style={{
              margin: 0,
              fontFamily: MONO,
              fontSize: 21,
              lineHeight: 1.72,
              whiteSpace: "pre",
              color: INK,
            }}
          >
            {highlight(CODE, typed)}
            {!done && frame % 16 < 9 ? (
              <span style={{ color: GREEN }}>▍</span>
            ) : null}
          </pre>
        </div>

        <div
          style={{
            ...panel,
            flex: "0 0 470px",
            background: PANEL,
            border: `1px solid ${LINE}`,
            borderRadius: 18,
            display: "flex",
            flexDirection: "column",
            overflow: "hidden",
          }}
        >
          <div
            style={{
              padding: "18px 24px",
              borderBottom: `1px solid ${LINE}`,
              display: "flex",
              gap: 10,
              alignItems: "center",
            }}
          >
            {["#ff5f57", "#febc2e", "#28c840"].map((c) => (
              <div key={c} style={{ width: 13, height: 13, borderRadius: 99, background: c }} />
            ))}
            <div style={{ fontFamily: MONO, fontSize: 16, color: MUTED, marginLeft: 14 }}>
              localhost:8080
            </div>
          </div>
          <div
            style={{
              flex: 1,
              display: "flex",
              flexDirection: "column",
              alignItems: "center",
              justifyContent: "center",
              gap: 30,
            }}
          >
            <div style={{ display: "flex", alignItems: "center", gap: 26 }}>
              {[-1, 1].map((sign) => (
                <div
                  key={sign}
                  style={{
                    order: sign < 0 ? 0 : 2,
                    width: 78,
                    height: 78,
                    borderRadius: 16,
                    border: `1px solid ${flash === sign ? GREEN : LINE}`,
                    background: flash === sign ? "#12251c" : "#121a1e",
                    color: flash === sign ? GREEN : INK,
                    fontSize: 34,
                    display: "flex",
                    alignItems: "center",
                    justifyContent: "center",
                    transform: `scale(${flash === sign ? 0.94 : 1})`,
                  }}
                >
                  {sign < 0 ? "−1" : "+1"}
                </div>
              ))}
              <div
                style={{
                  order: 1,
                  minWidth: 100,
                  textAlign: "center",
                  fontSize: 74,
                  fontWeight: 800,
                  fontVariantNumeric: "tabular-nums",
                  color: GREEN,
                }}
              >
                {value}
              </div>
            </div>
            <div style={{ fontSize: 20, color: MUTED }}>
              running as WebAssembly
            </div>
          </div>
        </div>
      </div>
    </Scene>
  );
};

/* -------------------------------------------------------------- scene 3 */

const STEPS = [
  { label: "Counter.java", sub: "you write this", color: INK },
  { label: "javac", sub: "JVM bytecode", color: MUTED },
  { label: "TeaVM", sub: "bytecode → wasm", color: VIOLET },
  { label: "classes.wasm", sub: "WasmGC in the browser", color: GREEN },
];

const Pipeline: React.FC = () => {
  const frame = useCurrentFrame();
  const foot = useRise(96);
  return (
    <Scene dur={S3}>
      <Kicker>Java in. WebAssembly out.</Kicker>
      <div style={{ display: "flex", alignItems: "center", gap: 8, marginTop: 18 }}>
        {STEPS.map((s, i) => {
          const style = useRise(16 + i * 16, 18);
          const arrow = interpolate(frame, [26 + i * 16, 40 + i * 16], [0, 1], {
            extrapolateLeft: "clamp",
            extrapolateRight: "clamp",
          });
          return (
            <React.Fragment key={s.label}>
              {i > 0 && (
                <div style={{ flex: "0 0 62px", height: 2, background: LINE, position: "relative" }}>
                  <div
                    style={{
                      position: "absolute",
                      inset: 0,
                      background: GREEN,
                      transformOrigin: "left",
                      transform: `scaleX(${arrow})`,
                      opacity: 0.7,
                    }}
                  />
                </div>
              )}
              <div
                style={{
                  ...style,
                  flex: 1,
                  background: PANEL,
                  border: `1px solid ${LINE}`,
                  borderRadius: 16,
                  padding: "34px 30px",
                  textAlign: "center",
                }}
              >
                <div
                  style={{
                    fontFamily: MONO,
                    fontSize: 30,
                    fontWeight: 600,
                    color: s.color,
                    letterSpacing: "-0.02em",
                  }}
                >
                  {s.label}
                </div>
                <div style={{ marginTop: 12, fontSize: 19, color: MUTED }}>{s.sub}</div>
              </div>
            </React.Fragment>
          );
        })}
      </div>
      <div style={{ ...foot, marginTop: 56, fontSize: 30, color: MUTED, maxWidth: 1260, lineHeight: 1.45 }}>
        WasmGC means your Java objects are{" "}
        <span style={{ color: INK }}>real browser heap objects</span> — no bundled
        garbage collector, no linear-memory heap.
      </div>
    </Scene>
  );
};

/* -------------------------------------------------------------- scene 4 */

const ROWS: [string, string][] = [
  ["useState", "State<T> — dependency-tracked, batched"],
  ["JSX / createElement", "VNode DSL — div, ul, input…"],
  ["Fiber + keyed reconciler", "Instance — list items keep identity"],
  ["React Router", "Router — History API, :params, guards"],
  ["React Query", "Resource<T> — loading / loaded / failed"],
  ["@Service / @Autowired", "build-time DI — zero reflection"],
];

const FeatureMap: React.FC = () => (
  <Scene dur={S4}>
    <Kicker>Everything you already know</Kicker>
    <div style={{ display: "flex", flexDirection: "column", gap: 6 }}>
      {ROWS.map(([left, right], i) => {
        const style = useRise(14 + i * 11, 22);
        return (
          <div
            key={left}
            style={{
              ...style,
              display: "flex",
              alignItems: "center",
              gap: 40,
              padding: "22px 4px",
              borderBottom: `1px solid ${LINE}`,
            }}
          >
            <div style={{ flex: "0 0 33%", fontSize: 33, color: MUTED, letterSpacing: "-0.02em" }}>
              {left}
            </div>
            <div style={{ color: GREEN, fontSize: 30 }}>→</div>
            <div
              style={{
                flex: 1,
                fontSize: 33,
                fontWeight: 600,
                letterSpacing: "-0.02em",
              }}
            >
              {right}
            </div>
          </div>
        );
      })}
    </div>
  </Scene>
);

/* -------------------------------------------------------------- scene 5 */

const Numbers: React.FC = () => {
  const frame = useCurrentFrame();
  const kb = Math.round(
    interpolate(frame, [18, 62], [0, 86], {
      extrapolateLeft: "clamp",
      extrapolateRight: "clamp",
    })
  );
  const stats: [string, string][] = [
    [`${kb} KB`, "gzipped wasm — framework, app, and Java class library"],
    ["0", "npm dependencies. The JDK runs the dev server from source."],
    ["1", "line of JavaScript you write: the boot call in index.html"],
  ];
  return (
    <Scene dur={S5}>
      <Kicker>Ships as a static directory</Kicker>
      <div style={{ display: "flex", gap: 34, marginTop: 20 }}>
        {stats.map(([big, small], i) => {
          const style = useRise(16 + i * 14, 26);
          return (
            <div
              key={small}
              style={{
                ...style,
                flex: 1,
                background: PANEL,
                border: `1px solid ${LINE}`,
                borderRadius: 18,
                padding: "44px 38px",
              }}
            >
              <div
                style={{
                  fontSize: 88,
                  fontWeight: 800,
                  letterSpacing: "-0.05em",
                  color: GREEN,
                  fontVariantNumeric: "tabular-nums",
                }}
              >
                {big}
              </div>
              <div style={{ marginTop: 18, fontSize: 25, color: MUTED, lineHeight: 1.4 }}>
                {small}
              </div>
            </div>
          );
        })}
      </div>
    </Scene>
  );
};

/* -------------------------------------------------------------- scene 6 */

const Cta: React.FC = () => {
  const term = useRise(14, 30);
  const sign = useRise(64, 22);
  const tag = useRise(76, 18);
  const frame = useCurrentFrame();
  return (
    <Scene dur={S6}>
      <Kicker>One command to a running app</Kicker>
      <div
        style={{
          ...term,
          background: PANEL,
          border: `1px solid ${LINE}`,
          borderRadius: 18,
          padding: "40px 46px",
          fontFamily: MONO,
          fontSize: 27,
          lineHeight: 2,
        }}
      >
        <div>
          <span style={{ color: GREEN }}>$ </span>
          mvn archetype:generate -DarchetypeArtifactId=
          <span style={{ color: AMBER }}>sprout-archetype</span>
        </div>
        <div style={{ opacity: frame > 40 ? 1 : 0 }}>
          <span style={{ color: GREEN }}>$ </span>cd myapp && java DevServer.java
        </div>
        <div style={{ opacity: frame > 58 ? 1 : 0, color: MUTED }}>
          → http://localhost:8080 — watch, rebuild, refresh.
        </div>
      </div>
      <div style={{ ...sign, marginTop: 66, fontSize: 74, fontWeight: 800, letterSpacing: "-0.04em" }}>
        🌱 <span style={{ color: GREEN }}>Sprout</span>
      </div>
      <div style={{ ...tag, marginTop: 16, fontSize: 31, color: MUTED }}>
        React-style. Spring-flavored. Java all the way down.
      </div>
    </Scene>
  );
};

/* ------------------------------------------------------------- assembly */

const S1 = 118;
const S2 = 236;
const S3 = 160;
const S4 = 178;
const S5 = 132;
const S6 = 176;
const OVERLAP = 10;

const SCENES: [React.FC, number][] = [
  [Hook, S1],
  [CodeScene, S2],
  [Pipeline, S3],
  [FeatureMap, S4],
  [Numbers, S5],
  [Cta, S6],
];

export const TOTAL =
  SCENES.reduce((n, [, d]) => n + d, 0) - OVERLAP * (SCENES.length - 1);

export const Sprout: React.FC = () => {
  let at = 0;
  return (
    <AbsoluteFill style={{ background: BG }}>
      {/* static backdrop: keeps crossfades from flashing the void */}
      <AbsoluteFill
        style={{
          backgroundImage: `radial-gradient(1200px 700px at 18% -10%, #10241b 0%, transparent 60%),
             radial-gradient(900px 600px at 105% 110%, #141a2e 0%, transparent 60%),
             repeating-linear-gradient(0deg, #0c1114 0 1px, transparent 1px 64px),
             repeating-linear-gradient(90deg, #0c1114 0 1px, transparent 1px 64px)`,
        }}
      />
      {SCENES.map(([Comp, dur], i) => {
        const from = at;
        at += dur - OVERLAP;
        return (
          <Sequence key={i} from={from} durationInFrames={dur} layout="none">
            <Comp />
          </Sequence>
        );
      })}
    </AbsoluteFill>
  );
};
