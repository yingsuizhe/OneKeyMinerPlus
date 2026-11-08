package com.ying;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public final class ExampleMod {
    public static final String MOD_ID = "onekeyminerplus";

    private static final Logger log = LoggerFactory.getLogger(ExampleMod.class);

    public static void init() {
        // Write common init code here.

        log.info("附属{}已启动", MOD_ID);
    }
}
