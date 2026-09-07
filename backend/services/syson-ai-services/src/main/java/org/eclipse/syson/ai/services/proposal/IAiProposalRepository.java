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
package org.eclipse.syson.ai.services.proposal;

import java.util.List;
import java.util.UUID;

import org.springframework.data.repository.ListCrudRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository for {@link AiProposal}, mirroring the platform's own Spring Data JDBC repositories in
 * {@code org.eclipse.sirius.web.domain}.
 *
 * <p>
 * Note that the platform's {@code @EnableJdbcRepositories} is scoped to its own base package, so
 * this repository is only discovered because {@code SysONAiPersistenceConfiguration} enables
 * scanning of ours.
 * </p>
 *
 * @author DTPrendergast
 */
@Repository
public interface IAiProposalRepository extends ListCrudRepository<AiProposal, UUID> {

    /**
     * Finds the proposals generated against a project, newest first, so a review view can list what
     * is outstanding.
     *
     * @param projectId
     *            the project identifier
     * @return the proposals for that project
     */
    List<AiProposal> findAllByProjectIdOrderByCreatedOnDesc(String projectId);
}
