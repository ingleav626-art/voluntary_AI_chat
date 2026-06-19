package org.example;

import java.util.logging.Logger;

/**
 * 应用程序入口类
 *
 * @author voluntary-ai-chat
 * @since 1.0.0
 */
public class Main {

    private static final Logger LOGGER = Logger.getLogger(Main.class.getName());

    /** 循环上限 */
    private static final int LOOP_LIMIT = 5;

    /**
     * 主方法 - 程序入口
     *
     * @param args 命令行参数
     */
    public static void main(final String[] args) {
        LOGGER.info("Hello and welcome!");

        for (int i = 1; i <= LOOP_LIMIT; i++) {
            LOGGER.info("i = " + i);
        }
    }
}
