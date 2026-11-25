package app;

import static sprout.VNode.h2;
import static sprout.VNode.link;
import static sprout.VNode.p;
import static sprout.VNode.section;

import java.util.List;

import sprout.Component;
import sprout.Props;
import sprout.VNode;

/** Detail page for /data/:index — reads the route param out of its Props. */
public class FactPage extends Component {

    public static VNode of(Props props) {
        return VNode.component(FactPage.class, FactPage::new, props);
    }

    @Override
    protected void onMount() {
        DataPage.loadOnce();          // deep link straight to /data/3 with no list visited yet
    }

    @Override
    public VNode render() {
        List<String> facts = DataPage.FACTS.get();
        if (facts == null) {
            return section(h2("Fact"), p("Loading…").cls("meta"));
        }
        int index = Integer.parseInt((String) props.get("index"));
        if (index < 0 || index >= facts.size()) {
            return section(h2("Fact"), p("No fact #" + index).cls("meta"), link("/data", "← back"));
        }
        return section(
                h2("Fact #" + index),
                p(facts.get(index)),
                p(link("/data", "← back to all facts")).cls("meta")
        );
    }
}
