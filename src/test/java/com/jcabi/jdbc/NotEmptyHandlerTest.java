package com.jcabi.jdbc;

import java.sql.ResultSet;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.Test;
import org.mockito.Mockito;

/**
 * Test case for {@link NotEmptyHandler}.
 * @author Yegor Bugayenko (yegor@jcabi.com)
 * @version $Id$
 */
public final class NotEmptyHandlerTest {

    /**
     * NotEmptyHandler can return TRUE if result set is not empty.
     * @throws Exception If there is some problem inside
     */
    @Test
    @SuppressWarnings("PMD.CloseResource")
    public void returnsTrueIfResultSetIsNotEmpty() throws Exception {
        final ResultSet rset = Mockito.mock(ResultSet.class);
        Mockito.doReturn(true).when(rset).next();
        MatcherAssert.assertThat(
            new NotEmptyHandler().handle(rset),
            Matchers.is(true)
        );
    }

    /**
     * NotEmptyHandler can return FALSE if result set is empty.
     * @throws Exception If there is some problem inside
     */
    @Test
    @SuppressWarnings("PMD.CloseResource")
    public void returnsFalseIfResultSetIsEmpty() throws Exception {
        final ResultSet rset = Mockito.mock(ResultSet.class);
        Mockito.doReturn(false).when(rset).next();
        MatcherAssert.assertThat(
            new NotEmptyHandler().handle(rset),
            Matchers.is(false)
        );
    }

}
