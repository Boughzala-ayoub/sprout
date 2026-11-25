package ${package};

import static sprout.VNode.button;
import static sprout.VNode.div;
import static sprout.VNode.h1;
import static sprout.VNode.p;
import static sprout.VNode.span;

import sprout.Component;
import sprout.Props;
import sprout.State;
import sprout.VNode;

/** Your app's root component. Edit this and save — the browser live-reloads. */
public class App extends Component {

    public static VNode of(Props props) {
        return VNode.component(App.class, App::new, props);
    }

    private final State<Integer> count = new State<>(0);

    @Override
    public VNode render() {
        return div(
                h1("🌱 Hello from Sprout"),
                p("A component written in Java, running in your browser. Edit App.java."),
                div(
                        button("−1").onClick(() -> count.set(count.get() - 1)),
                        span(" " + count.get() + " ").cls("count"),
                        button("+1").onClick(() -> count.set(count.get() + 1))
                ).cls("counter")
        ).cls("app");
    }
}
