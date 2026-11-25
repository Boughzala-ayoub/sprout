package ${package};

import sprout.Props;
import sprout.Sprout;
import sprout.generated.SproutBoot;

public class Main {
    public static void main(String[] args) {
        // SproutBoot is generated at build time (scans @Service / @Autowired).
        Sprout.run(App.of(new Props()), "app", SproutBoot.context());
    }
}
