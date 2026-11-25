package app;

import sprout.Service;

/** A second @Service, to show the processor scans and provides more than one bean. */
@Service
public class AppInfoService {
    public String name() {
        return "Sprout";
    }

    public String version() {
        return "0.1.0";
    }
}
