package app;

import static sprout.VNode.button;
import static sprout.VNode.div;
import static sprout.VNode.span;

import sprout.Component;
import sprout.Props;
import sprout.State;
import sprout.VNode;

/** A reusable, self-contained component with its own local state. */
public class Counter extends Component {

    public static VNode of(Props props) {
        return VNode.component(Counter.class, Counter::new, props);
    }

    private final State<Integer> count = new State<>(0);

    @Override
    public VNode render() {
        return div(
                button("−1").onClick(() -> count.set(count.get() - 1)),
                span(" " + count.get() + " ").cls("count"),
                button("+1").onClick(() -> count.set(count.get() + 1))
        ).cls("counter");
    }
}
