package com.pischule.mentionbot.util;

import liquibase.Scope;
import liquibase.command.CommandScope;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LiquibaseRunner {
    private static final Logger logger = LoggerFactory.getLogger(LiquibaseRunner.class);

    private final String jdbcUrl;
    private final String changelogFile;

    public LiquibaseRunner(String jdbcUrl, String changelogFile) {
        this.jdbcUrl = jdbcUrl;
        this.changelogFile = changelogFile;
    }

    public void update() throws Exception {
        logger.atInfo().log("Running liquibase");

        Scope.child(Scope.Attr.resourceAccessor, new ClassLoaderResourceAccessor(), () -> {
            CommandScope update = new CommandScope("update");
            update.addArgumentValue("url", jdbcUrl);
            update.addArgumentValue("changelogFile", changelogFile);
            update.execute();
        });

        logger.atInfo().log("Successfully ran liquibase update");
    }
}
