package app;

import static sprout.VNode.h2;
import static sprout.VNode.p;
import static sprout.VNode.section;

import sprout.Autowired;
import sprout.Component;
import sprout.Props;
import sprout.Route;
import sprout.VNode;

/** The about page: services arrive via @Autowired (wired by the build-time processor). */
@Route("/about")
public class AboutPage extends Component {

    public static VNode of(Props props) {
        return VNode.component(AboutPage.class, AboutPage::new, props);
    }

    // Injected from the AppContext — no manual wiring. Must be public (the
    // generated wiring lives in another package and sets these directly).
    @Autowired
    public GreetingService greeting;

    @Autowired
    public AppInfoService appInfo;

    @Override
    public VNode render() {
        return section(
                h2("About"),
                p(greeting.greet("Sprout dev")),
                p("Running " + appInfo.name() + " v" + appInfo.version()
                        + " — services injected via @Autowired."),
                p("Two independent Counter components, each with its own state:"),
                Counter.of(new Props()),
                Counter.of(new Props())
        );
    }
}
