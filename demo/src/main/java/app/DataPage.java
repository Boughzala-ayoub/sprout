package app;

import static sprout.VNode.h2;
import static sprout.VNode.li;
import static sprout.VNode.link;
import static sprout.VNode.p;
import static sprout.VNode.section;
import static sprout.VNode.ul;

import java.util.ArrayList;
import java.util.List;

import sprout.Component;
import sprout.Http;
import sprout.Props;
import sprout.Resource;
import sprout.Route;
import sprout.VNode;

/** Loads data over HTTP on mount. Loading and failure states come from Resource. */
@Route("/data")
public class DataPage extends Component {

    public static VNode of(Props props) {
        return VNode.component(DataPage.class, DataPage::new, props);
    }

    /** Shared so the detail page can read the same list without refetching. */
    static final Resource<List<String>> FACTS = new Resource<>();

    static void loadOnce() {
        if (FACTS.get() == null && !FACTS.refreshing()) {
            FACTS.load(Http.get("/data/facts.txt"), response -> {
                List<String> lines = new ArrayList<>();
                for (String line : response.text().split("\n")) {
                    if (!line.trim().isEmpty()) {
                        lines.add(line.trim());
                    }
                }
                return lines;
            });
        }
    }

    @Override
    protected void onMount() {
        loadOnce();
    }

    @Override
    public VNode render() {
        if (FACTS.loading()) {
            return section(h2("Data"), p("Loading…").cls("meta"));
        }
        if (FACTS.failed()) {
            return section(h2("Data"), p("Could not load: " + FACTS.error()).cls("error"));
        }
        List<String> list = FACTS.get();
        List<VNode> items = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            items.add(li(link("/data/" + i, list.get(i))).cls("todo"));
        }
        return section(
                h2("Data"),
                p("Loaded " + list.size() + " facts over HTTP — open one:").cls("meta"),
                ul(items)
        );
    }
}
