package com.evilnanan.leigodtoken;

import org.junit.Test;
import static org.junit.Assert.*;

public class TokenPolicyTest {
    @Test public void onlyTheOfficialSecureOriginIsAccepted() {
        assertTrue(TokenPolicy.isOfficialPage("https://www.leigod.com/activitys/leigodpass2"));
        assertTrue(TokenPolicy.isOfficialPage("https://www.leigod.com:443/user"));
        for (String bad : new String[] {null, "", "http://www.leigod.com/", "https://www.leigod.com.evil.test/",
                "https://www.leigod.com@evil.test/", "https://evil.test@www.leigod.com/",
                "https://www.leigod.com:444/", "file:///data/local/tmp/token.html", "javascript:alert(1)"}) {
            assertFalse(String.valueOf(bad), TokenPolicy.isOfficialPage(bad));
        }
    }

    @Test public void repositoriesCannotChangeTheDestinationHost() {
        assertTrue(TokenPolicy.validRepository("Evilnanan/leigod-auto-pause"));
        assertFalse(TokenPolicy.validRepository("https://evil.test"));
        assertFalse(TokenPolicy.validRepository("owner/repo?next=evil.test"));
        assertFalse(TokenPolicy.validRepository("owner/repo/extra"));
    }
}
