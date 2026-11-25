package app;

import sprout.Service;

/** A plain service, injected through the application context to demonstrate DI. */
@Service
public class GreetingService {
    public String greet(String name) {
        return "Hello, " + name + " 👋";
    }
}
