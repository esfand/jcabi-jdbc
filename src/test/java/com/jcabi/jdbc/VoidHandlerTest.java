package com.jcabi.jdbc;

import java.sql.ResultSet;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.Test;
import org.mockito.Mockito;

/**
 * Test case for {@link VoidHandler}.
 * @author Yegor Bugayenko (yegor@jcabi.com)
 * @version $Id$
 */
public final class VoidHandlerTest {

    /**
     * VoidHandler can always return null.
     * @throws Exception If there is some problem inside
     */
    @Test
    public void returnsNullNoMatterWhatIsTheInput() throws Exception {
        MatcherAssert.assertThat(
            new VoidHandler().handle(Mockito.mock(ResultSet.class)),
            Matchers.nullValue()
        );
    }

}
