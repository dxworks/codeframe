package org.dxworks.codeframe.analyzer.sql;

import org.dxworks.codeframe.CodeframeConfig;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SqlParserThreadLeakTest {

    // MySQL syntax JSqlParser rejects (spring-petclinic db/mysql/user.sql)
    private static final String UNPARSEABLE_SQL = """
            CREATE DATABASE IF NOT EXISTS petclinic;
            ALTER DATABASE petclinic DEFAULT CHARACTER SET utf8 DEFAULT COLLATE utf8_general_ci;
            GRANT ALL PRIVILEGES ON petclinic.* TO pc@localhost IDENTIFIED BY 'pc';
            """;

    @Test
    void failedParseLeavesNoNonDaemonThread() {
        Set<Thread> before = liveNonDaemonThreads();

        SQLAnalyzer analyzer = new SQLAnalyzer(CodeframeConfig.load());
        for (int i = 0; i < 5; i++) {
            analyzer.analyze("user.sql", UNPARSEABLE_SQL);
        }

        Set<Thread> leaked = liveNonDaemonThreads();
        leaked.removeAll(before);
        assertEquals(Set.of(), leaked.stream().map(Thread::getName).collect(Collectors.toSet()));
    }

    private static Set<Thread> liveNonDaemonThreads() {
        return Thread.getAllStackTraces().keySet().stream()
                .filter(t -> t.isAlive() && !t.isDaemon())
                .collect(Collectors.toSet());
    }
}
