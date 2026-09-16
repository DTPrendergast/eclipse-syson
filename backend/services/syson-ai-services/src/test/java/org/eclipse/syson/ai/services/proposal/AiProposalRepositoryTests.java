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

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.eclipse.syson.ai.services.configuration.SysONAiPersistenceConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Exercises the proposal store against a real PostgreSQL, which is the only way to check the three
 * things that actually carry risk here: that our Liquibase changelog applies, that the Spring Data
 * JDBC mapping matches the columns it creates, and that the item child-collection round-trips.
 *
 * @author DTPrendergast
 */
// This boots a full servlet environment for what is only a persistence test, and that is forced by
// the module's test classpath rather than by anything here. The scope tests need
// syson-application-configuration (they parse against the real default libraries), which puts
// upstream's whole web stack on the classpath, and Boot's auto-configuration then insists on
// standing it up: WebEnvironment.NONE leaves SpringWebMvcConfigurer without WebProperties, and MOCK
// leaves WebSocketConfiguration without a real ServerContainer. RANDOM_PORT is the same recipe
// SysONAiSchemaCoexistenceTests uses, for the same reason.
//
// The property is upstream's too: ImagesController maps "/api/images/**/*", which Spring 7's
// default pattern parser rejects. The real application sets this in application.properties, which
// is not on this module's classpath.
@SpringBootTest(classes = { AiProposalRepositoryTests.TestConfiguration.class },
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.mvc.pathmatch.matching-strategy=ANT_PATH_MATCHER")
class AiProposalRepositoryTests {

    private static final String MODEL_ID = "claude-opus-5";

    private static final String AUTHOR = "dtprendergast";

    private static final String TASK_KIND = "DERIVED_REQUIREMENTS";

    private static final PostgreSQLContainer<?> POSTGRESQL_CONTAINER;

    static {
        POSTGRESQL_CONTAINER = new PostgreSQLContainer<>("postgres:latest").withReuse(true);
        POSTGRESQL_CONTAINER.start();
    }

    @Autowired
    private IAiProposalRepository proposalRepository;

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRESQL_CONTAINER::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRESQL_CONTAINER::getUsername);
        registry.add("spring.datasource.password", POSTGRESQL_CONTAINER::getPassword);
        // This context has no platform changelog, so Boot's own Liquibase has nothing to run.
        registry.add("spring.liquibase.enabled", () -> "false");
    }

    @Test
    void persistsAProposalWithItsItems() {
        var proposal = new AiProposal("project-1", TASK_KIND, "Derive requirements for the power subsystem", MODEL_ID, AUTHOR, List.of("seed-1"),
                List.of(new AiProposalItem("REQ-010", "requirement req10 : R1;"), new AiProposalItem("REQ-011", "requirement req11 : R1;")));

        this.proposalRepository.save(proposal);

        var reloaded = this.proposalRepository.findById(proposal.getId()).orElseThrow();
        assertThat(reloaded.getProjectId()).isEqualTo("project-1");
        assertThat(reloaded.getStatus()).isEqualTo(AiProposalStatus.PENDING_REVIEW);
        assertThat(reloaded.getPrompt()).isEqualTo("Derive requirements for the power subsystem");
        assertThat(reloaded.getModelId()).isEqualTo(MODEL_ID);
        assertThat(reloaded.getCreatedBy()).isEqualTo(AUTHOR);
        assertThat(reloaded.getCreatedOn()).isNotNull();
        assertThat(reloaded.getItems()).hasSize(2);
        assertThat(reloaded.getItems()).allSatisfy(item -> assertThat(item.getStatus()).isEqualTo(AiProposalItemStatus.PENDING));
        assertThat(reloaded.getItems()).extracting(AiProposalItem::getLabel).containsExactly("REQ-010", "REQ-011");
    }

    @Test
    void keepsPerItemDecisionsAndDerivesTheProposalStatus() {
        var proposal = new AiProposal("project-2", TASK_KIND, "Derive requirements", MODEL_ID, AUTHOR, List.of("seed-1"),
                List.of(new AiProposalItem("kept", "requirement kept : R1;"), new AiProposalItem("dropped", "requirement dropped : R1;")));
        this.proposalRepository.save(proposal);

        var toReview = this.proposalRepository.findById(proposal.getId()).orElseThrow();
        toReview.getItems().get(0).applied("element-42");
        toReview.getItems().get(1).reject();
        toReview.reviewCompleted();
        this.proposalRepository.save(toReview);

        var reloaded = this.proposalRepository.findById(proposal.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(AiProposalStatus.APPLIED);
        assertThat(reloaded.getItems().get(0).getStatus()).isEqualTo(AiProposalItemStatus.APPLIED);
        assertThat(reloaded.getItems().get(0).getAppliedElementId()).contains("element-42");
        assertThat(reloaded.getItems().get(1).getStatus()).isEqualTo(AiProposalItemStatus.REJECTED);
        assertThat(reloaded.getItems().get(1).getAppliedElementId()).isEmpty();
    }

    @Test
    void rejectingEveryItemLeavesNothingApplied() {
        var proposal = new AiProposal("project-3", TASK_KIND, "Derive requirements", MODEL_ID, AUTHOR, List.of("seed-1"),
                List.of(new AiProposalItem("dropped", "requirement dropped : R1;")));
        this.proposalRepository.save(proposal);

        var toReview = this.proposalRepository.findById(proposal.getId()).orElseThrow();
        toReview.getItems().get(0).reject();
        toReview.reviewCompleted();
        this.proposalRepository.save(toReview);

        assertThat(this.proposalRepository.findById(proposal.getId()).orElseThrow().getStatus()).isEqualTo(AiProposalStatus.REJECTED);
    }

    @Test
    void findsTheProposalsOfAProjectNewestFirst() {
        var older = new AiProposal("project-4", TASK_KIND, "first", MODEL_ID, AUTHOR, List.of("seed-1"), List.of());
        this.proposalRepository.save(older);
        var newer = new AiProposal("project-4", TASK_KIND, "second", MODEL_ID, AUTHOR, List.of("seed-1"), List.of());
        this.proposalRepository.save(newer);

        var found = this.proposalRepository.findAllByProjectIdOrderByCreatedOnDesc("project-4");

        assertThat(found).extracting(AiProposal::getPrompt).containsExactly("second", "first");
    }

    /**
     * Minimal context: auto-configuration for the datasource plus our own persistence wiring.
     */
    @Configuration
    @EnableAutoConfiguration
    @org.springframework.context.annotation.Import(SysONAiPersistenceConfiguration.class)
    static class TestConfiguration {
    }
}
