package org.apache.roller.weblogger.business;

import static org.junit.jupiter.api.Assertions.*;
import org.apache.roller.weblogger.TestUtils;
import org.apache.roller.weblogger.pojos.RollerStar;
import org.apache.roller.weblogger.pojos.User;
import org.apache.roller.weblogger.pojos.Weblog;
import org.junit.jupiter.api.*;

public class StarServiceTest {

    private User user;
    private Weblog weblog;

    @BeforeEach
    public void setUp() throws Exception {
        TestUtils.setupWeblogger();
        user = TestUtils.setupUser("starTestUser");
        weblog = TestUtils.setupWeblog("starTestWeblog", user);
        TestUtils.endSession(true);
    }

    @AfterEach
    public void tearDown() throws Exception {
        TestUtils.teardownWeblog(weblog.getId());
        TestUtils.teardownUser(user.getUserName());
        TestUtils.endSession(true);
    }

    @Test
    public void testStarUnstarWeblog() throws Exception {
        StarService s = WebloggerFactory.getWeblogger().getStarService();

        s.star(user.getId(), RollerStar.TargetType.WEBLOG, weblog.getId());
        WebloggerFactory.getWeblogger().flush();
        TestUtils.endSession(true);

        assertTrue(s.isStarred(user.getId(), RollerStar.TargetType.WEBLOG, weblog.getId()));

        // idempotent
        s.star(user.getId(), RollerStar.TargetType.WEBLOG, weblog.getId());
        WebloggerFactory.getWeblogger().flush();
        TestUtils.endSession(true);

        s.unstar(user.getId(), RollerStar.TargetType.WEBLOG, weblog.getId());
        WebloggerFactory.getWeblogger().flush();
        TestUtils.endSession(true);

        assertFalse(s.isStarred(user.getId(), RollerStar.TargetType.WEBLOG, weblog.getId()));
    }
}
