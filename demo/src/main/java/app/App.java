package app;

import static sprout.VNode.div;
import static sprout.VNode.link;
import static sprout.VNode.nav;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

import sprout.Component;
import sprout.Props;
import sprout.Router;
import sprout.VNode;

/** Root component: a nav bar plus the router that swaps pages by URL path. */
public class App extends Component {

    public static VNode of(Props props) {
        return VNode.component(App.class, App::new, props);
    }

    @Override
    public VNode render() {
        Map<String, Function<Props, VNode>> routes = new LinkedHashMap<>();
        routes.put("/", TodoPage::of);
        routes.put("/about", AboutPage::of);
        routes.put("/data", DataPage::of);
        routes.put("/data/:index", FactPage::of);    // ":index" arrives in the page's Props
        routes.put("*", NotFoundPage::of);

        return div(
                nav(
                        link("/", "Todos"),
                        link("/about", "About"),
                        link("/data", "Data")
                ).cls("nav"),
                Router.of(new Props().set("routes", routes))
        ).cls("app");
    }
}
