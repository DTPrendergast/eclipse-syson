/*******************************************************************************
 * Copyright (c) 2026 Obeo.
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Obeo - initial API and implementation
 *******************************************************************************/
package org.eclipse.syson.application;

import static org.assertj.core.api.Assertions.assertThat;

import javax.sql.DataSource;

import org.eclipse.syson.AbstractIntegrationTests;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Guards a failure mode that no unit test and no compiler can catch: our own Liquibase changelog
 * silently replacing the platform's instead of running alongside it.
 *
 * <p>
 * Spring Boot's {@code LiquibaseAutoConfiguration$LiquibaseConfiguration} is annotated
 * {@code @ConditionalOnMissingBean(SpringLiquibase.class)}. If anyone ever "simplifies"
 * {@code SysONAiDatabaseMigration} into a plain {@code SpringLiquibase} bean, Boot's own migration
 * quietly backs off, the whole Sirius Web schema stops being created, and the first symptom is a
 * missing table at runtime - by which point our AI tables exist and look fine.
 * </p>
 *
 * <p>
 * So this asserts both halves in the real application configuration: the platform's tables and
 * ours, each created by its own changelog, tracked in its own changelog table.
 * </p>
 *
 * <p>
 * This boots the whole application ({@code @SpringBootTest}) rather than using the lighter shared
 * {@code @SpringJUnitConfig} context. That context never loads {@code application.properties}, so
 * {@code spring.liquibase.change-log} is unset there and Boot's Liquibase falls back to a default
 * changelog path that does not exist. Only a full boot exercises the configuration that ships.
 * </p>
 *
 * @author DTPrendergast
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class SysONAiSchemaCoexistenceTests extends AbstractIntegrationTests {

    @Autowired
    private DataSource dataSource;

    @Test
    public void bothChangelogsRun() {
        var jdbcTemplate = new JdbcTemplate(this.dataSource);

        assertThat(this.tableExists(jdbcTemplate, "project")).as("platform table 'project' - if this is missing, our Liquibase bean has disabled the platform's").isTrue();
        assertThat(this.tableExists(jdbcTemplate, "semantic_data")).as("platform table 'semantic_data'").isTrue();
        assertThat(this.tableExists(jdbcTemplate, "ai_proposal")).as("our table 'ai_proposal'").isTrue();
        assertThat(this.tableExists(jdbcTemplate, "ai_proposal_item")).as("our table 'ai_proposal_item'").isTrue();
    }

    @Test
    public void eachChangelogTracksItselfSeparately() {
        var jdbcTemplate = new JdbcTemplate(this.dataSource);

        assertThat(this.tableExists(jdbcTemplate, "databasechangelog")).as("the platform's changelog tracking table").isTrue();
        assertThat(this.tableExists(jdbcTemplate, "syson_ai_databasechangelog")).as("our own changelog tracking table").isTrue();
    }

    private boolean tableExists(JdbcTemplate jdbcTemplate, String tableName) {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = 'public' AND table_name = ?", Integer.class, tableName);
        return count != null && count > 0;
    }
}
