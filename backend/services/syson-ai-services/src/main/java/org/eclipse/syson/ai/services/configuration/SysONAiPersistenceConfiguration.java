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

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ResourceLoader;
import org.springframework.data.jdbc.repository.config.EnableJdbcRepositories;

/**
 * Persistence wiring for the AI assistant's proposals.
 *
 * <p>
 * The platform's {@code JDBCConfiguration} enables repository scanning for
 * {@code org.eclipse.sirius.web.domain} only, so our repositories in {@code org.eclipse.syson.ai}
 * would never be discovered without this. Unlike the Liquibase case (see
 * {@link SysONAiDatabaseMigration}), a second {@code @EnableJdbcRepositories} is purely additive -
 * it does not switch the platform's off.
 * </p>
 *
 * @author DTPrendergast
 */
@Configuration
@EnableJdbcRepositories(basePackages = "org.eclipse.syson.ai.services")
public class SysONAiPersistenceConfiguration {

    @Bean
    public SysONAiDatabaseMigration sysONAiDatabaseMigration(DataSource dataSource, ResourceLoader resourceLoader, ObjectProvider<SpringLiquibase> platformLiquibase) {
        return new SysONAiDatabaseMigration(dataSource, resourceLoader, platformLiquibase);
    }
}
