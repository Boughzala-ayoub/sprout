package sprout;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Route matching is pure string work — no DOM — so it runs on the JVM.
 * These are the cases that decide which page a URL opens.
 */
public class RouterMatchTest {

    @Test
    public void literalPathMatchesItself() {
        assertNotNull(Router.match("/about", "/about"));
        assertNull(Router.match("/about", "/data"));
    }

    @Test
    public void rootMatchesRoot() {
        assertNotNull(Router.match("/", "/"));
        assertNull(Router.match("/", "/about"));
    }

    @Test
    public void paramIsCaptured() {
        Props params = Router.match("/data/:index", "/data/3");
        assertNotNull(params);
        assertEquals("3", params.get("index"));
    }

    @Test
    public void severalParamsAreCaptured() {
        Props params = Router.match("/u/:user/posts/:post", "/u/ada/posts/42");
        assertNotNull(params);
        assertEquals("ada", params.get("user"));
        assertEquals("42", params.get("post"));
    }

    @Test
    public void extraOrMissingSegmentsDoNotMatch() {
        assertNull(Router.match("/data/:index", "/data"));
        assertNull(Router.match("/data/:index", "/data/3/extra"));
    }

    @Test
    public void trailingStarEatsTheRest() {
        assertNotNull(Router.match("/settings/*", "/settings/profile/email"));
        assertNotNull(Router.match("/settings/*", "/settings/x"));
        assertNull(Router.match("/settings/*", "/other/x"));
    }

    @Test
    public void bareStarMatchesAnything() {
        assertNotNull(Router.match("*", "/whatever/deep/path"));
        assertNotNull(Router.match("*", "/"));
    }

    @Test
    public void trailingSlashIsTheSameRoute() {
        assertNotNull(Router.match("/about", "/about/"));
        assertNotNull(Router.match("/data/:index", "/data/3/"));
    }

    @Test
    public void literalBeatsParamBeatsWildcard() {
        assertTrue(Router.specificity("/data/new") > Router.specificity("/data/:index"));
        assertTrue(Router.specificity("/data/:index") > Router.specificity("/data/*"));
        assertTrue(Router.specificity("/data/*") > Router.specificity("*"));
    }
}
