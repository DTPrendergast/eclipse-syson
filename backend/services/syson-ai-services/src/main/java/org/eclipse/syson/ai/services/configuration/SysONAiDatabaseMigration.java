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
package org.eclipse.syson.ai.services.configuration;

import javax.sql.DataSource;

import liquibase.integration.spring.SpringLiquibase;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.io.ResourceLoader;

/**
 * Applies this module's Liquibase changelog, which creates the AI proposal tables.
 *
 * <p>
 * <b>This deliberately is not - and must never become - a {@link SpringLiquibase} bean.</b> Spring
 * Boot's {@code LiquibaseAutoConfiguration$LiquibaseConfiguration} is annotated
 * {@code @ConditionalOnMissingBean(SpringLiquibase.class)}, so publishing one of our own would
 * silently switch off the platform's migration and the entire Sirius Web schema would stop being
 * created. The failure would appear at runtime as missing platform tables, not as a build error.
 * Instead we construct a {@link SpringLiquibase} locally and drive it ourselves, which the
 * auto-configuration cannot see.
 * </p>
 *
 * <p>
 * Ordering is expressed by resolving the platform's own {@link SpringLiquibase} bean before running
 * ours: resolving it forces its initialization, and therefore its migration, to complete first. It
 * is requested through an {@link ObjectProvider} rather than injected directly so that a context
 * without Liquibase - a slice test, for instance - does not fail to start.
 * </p>
 *
 * <p>
 * Our changelog tracks itself in its own tables ({@code syson_ai_databasechangelog}), so the two
 * changelogs never contend over a shared tracking table.
 * </p>
 *
 * @author DTPrendergast
 */
public class SysONAiDatabaseMigration implements InitializingBean {

    private static final String CHANGELOG = "classpath:db/syson-ai/db.changelog-master.xml";

    private static final String CHANGELOG_TABLE = "syson_ai_databasechangelog";

    private static final String CHANGELOG_LOCK_TABLE = "syson_ai_databasechangeloglock";

    private final DataSource dataSource;

    private final ResourceLoader resourceLoader;

    private final ObjectProvider<SpringLiquibase> platformLiquibase;

    public SysONAiDatabaseMigration(DataSource dataSource, ResourceLoader resourceLoader, ObjectProvider<SpringLiquibase> platformLiquibase) {
        this.dataSource = dataSource;
        this.resourceLoader = resourceLoader;
        this.platformLiquibase = platformLiquibase;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        // Force the platform's migration to complete before ours, without depending on it existing.
        this.platformLiquibase.getIfAvailable();

        var liquibase = new SpringLiquibase();
        liquibase.setDataSource(this.dataSource);
        liquibase.setResourceLoader(this.resourceLoader);
        liquibase.setChangeLog(CHANGELOG);
        liquibase.setDatabaseChangeLogTable(CHANGELOG_TABLE);
        liquibase.setDatabaseChangeLogLockTable(CHANGELOG_LOCK_TABLE);
        liquibase.afterPropertiesSet();
    }
}
