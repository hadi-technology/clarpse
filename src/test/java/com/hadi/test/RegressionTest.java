package com.hadi.test;

import com.hadi.clarpse.CommonDir;
import org.junit.Test;

import static org.junit.Assert.assertTrue;


public class RegressionTest {

    @Test
    public void shouldNotArrayOutOfBoundsException() throws Exception {
        assertTrue(new CommonDir("/test/lol/cakes", "/").value().equalsIgnoreCase("/"));
    }

}
