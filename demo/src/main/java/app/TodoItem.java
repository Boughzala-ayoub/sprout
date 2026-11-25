package app;

import static sprout.VNode.button;
import static sprout.VNode.input;
import static sprout.VNode.li;
import static sprout.VNode.span;

import sprout.Component;
import sprout.Props;
import sprout.State;
import sprout.VNode;

/**
 * A child component rendered from props. It also holds a bit of *local* state
 * ({@link #done}) — which is the point of the reorder demo: because the parent
 * keys list items by a stable id, reordering keeps each item's own instance,
 * so a checked item stays checked after it moves.
 */
public class TodoItem extends Component {

    public static VNode of(Props props) {
        return VNode.component(TodoItem.class, TodoItem::new, props);
    }

    private final State<Boolean> done = new State<>(false);

    @Override
    public VNode render() {
        String text = props.str("text");
        Runnable onRemove = props.runnable("onRemove");
        Runnable onMoveUp = props.runnable("onMoveUp");
        boolean isDone = done.get();
        return li(
                // A controlled checkbox: `checked` is written as a property on
                // every patch, so the box always shows what the State says.
                input("checkbox").attr("checked", isDone)
                        .attr("aria-label", "Done: " + text)
                        .onChange(done::set).cls("check"),
                span(text).cls("text", isDone ? "done" : null),
                button("↑").onClick(onMoveUp).attr("aria-label", "Move up: " + text).cls("mv"),
                button("✕").onClick(onRemove).attr("aria-label", "Remove: " + text).cls("x")
        ).cls("todo");
    }
}
