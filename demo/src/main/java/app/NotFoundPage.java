package app;

import static sprout.VNode.h2;
import static sprout.VNode.link;
import static sprout.VNode.p;
import static sprout.VNode.section;

import sprout.Component;
import sprout.Props;
import sprout.VNode;

/** The "*" route: whatever the URL was, the app still renders. */
public class NotFoundPage extends Component {

    public static VNode of(Props props) {
        return VNode.component(NotFoundPage.class, NotFoundPage::new, props);
    }

    @Override
    public VNode render() {
        return section(
                h2("404"),
                p("Nothing lives at " + props.get("path")).cls("meta"),
                p(link("/", "← back to the todos"))
        );
    }
}
