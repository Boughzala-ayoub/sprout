package app;

import static sprout.VNode.button;
import static sprout.VNode.form;
import static sprout.VNode.h2;
import static sprout.VNode.input;
import static sprout.VNode.p;
import static sprout.VNode.section;
import static sprout.VNode.ul;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import sprout.Component;
import sprout.Props;
import sprout.Route;
import sprout.State;
import sprout.VNode;

/** The todo page: controlled input, keyed list, child components, reorder, conditional rendering. */
@Route("/")
public class TodoPage extends Component {

    public static VNode of(Props props) {
        return VNode.component(TodoPage.class, TodoPage::new, props);
    }

    /** A todo with a stable id, so list children keep identity across reorders/removals. */
    private static final class Todo {
        final int id;
        final String text;
        Todo(int id, String text) {
            this.id = id;
            this.text = text;
        }
    }

    private final State<List<Todo>> todos = new State<>(new ArrayList<>());
    private final State<String> draft = new State<>("");
    private int nextId = 1;

    // State.mutate edits the list in place and re-renders. State.set would not:
    // it skips equal values, and a list is equal to itself after you change it.
    private void add() {
        String text = draft.get().trim();
        if (text.isEmpty()) {
            return;
        }
        todos.mutate(list -> list.add(new Todo(nextId++, text)));
        draft.set("");
    }

    private void remove(int id) {
        todos.mutate(list -> list.removeIf(t -> t.id == id));
    }

    private void moveUp(int id) {
        todos.mutate(list -> {
            for (int i = 1; i < list.size(); i++) {
                if (list.get(i).id == id) {
                    Collections.swap(list, i, i - 1);
                    return;
                }
            }
        });
    }

    private void reverse() {
        todos.mutate(Collections::reverse);
    }

    @Override
    public VNode render() {
        List<Todo> list = todos.get();

        List<VNode> items = new ArrayList<>();
        for (Todo t : list) {
            final int id = t.id;
            items.add(TodoItem.of(new Props()
                    .set("text", t.text)
                    .set("onRemove", (Runnable) () -> remove(id))
                    .set("onMoveUp", (Runnable) () -> moveUp(id)))
                    .key(String.valueOf(id)));            // keyed by stable id
        }

        VNode listView = list.isEmpty()
                ? p("Nothing yet — add one above.").cls("empty")
                : ul(items);

        return section(
                h2("Todos"),
                // A real <form>, so the Enter key submits — onSubmit already
                // called preventDefault, so the page never reloads.
                form(
                        input().attr("placeholder", "What needs doing?")
                                .attr("value", draft.get())
                                .attr("aria-label", "New todo")
                                .onInput(draft::set),
                        button("Add").attr("type", "submit")
                                .attr("disabled", draft.get().trim().isEmpty())
                ).cls("row").onSubmit(this::add),
                list.size() > 1 ? button("Reverse order").onClick(this::reverse).cls("reverse") : null,
                listView,
                p(list.size() + " item(s)").cls("meta")
        );
    }
}
