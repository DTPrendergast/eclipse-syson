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
package org.eclipse.syson.ai.services.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.mock;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.sirius.components.emf.services.api.IEMFEditingContext;
import org.eclipse.sirius.components.representations.Message;
import org.eclipse.syson.application.configuration.SysONDefaultLibrariesConfiguration;
import org.eclipse.syson.application.configuration.SysONLoadDefaultLibrariesOnApplicationStartConfiguration;
import org.eclipse.syson.sysml.ASTTransformer;
import org.eclipse.syson.sysml.Element;
import org.eclipse.syson.sysml.IncludeUseCaseUsage;
import org.eclipse.syson.sysml.SysmlToAst;
import org.eclipse.syson.sysml.UseCaseUsage;
import org.eclipse.syson.sysml.services.SysideSysMLTextImporter;
import org.junit.jupiter.api.Test;

/**
 * Whether the details of a use case can be written into a use case that already exists.
 *
 * <p>
 * Every element task so far has <em>created</em> an element under a parent. Detailing a use case is
 * the other shape: the element is already there and what is generated belongs inside it. The
 * importer takes a parent, so the mechanism looks right, but whether body members parse when
 * handed to it on their own - rather than as part of a declaration - is a question only the real
 * parser answers. This grammar has silently dropped things before.
 * </p>
 *
 * @author DTPrendergast
 */
class UseCaseBodyImportTests {

    private static final String MODEL = """
            package Ops {
                part def Aircraft;
                part aircraft : Aircraft;
                use case missionPlanning {
                    doc /* Plan the sortie. */
                }
                use case flyMission;
            }
            """;

    private final SysideSysMLTextImporter importer = new SysideSysMLTextImporter(new SysmlToAst(null));

    /**
     * Body members handed to the importer on their own produce nothing - and say nothing. This is
     * why detailing generates a whole declaration rather than the inside of one: `actor`, `subject`,
     * `objective` and `require` are only grammatical as members of a declaration, and a fragment
     * that is not grammatical here is dropped in silence rather than reported.
     */
    @Test
    void bareBodyMembersDoNotImportOnTheirOwn() {
        UseCaseUsage flyMission = this.useCase("flyMission");
        var messages = new ArrayList<Message>();

        this.importer.importSysMLText(mock(IEMFEditingContext.class), flyMission, """
                subject aircraft;
                actor pilot;
                """, messages);

        assertThat(this.namesUnder(flyMission)).isEmpty();
        assertThat(messages).as("nothing landed and nothing was reported").isEmpty();
    }

    /**
     * The shape detailing has to use: a complete declaration, imported into the package so that
     * names resolve against the real model.
     */
    @Test
    void aFullDeclarationImportsEveryBodyMember() {
        Element pack = this.packageOps();

        this.importer.importSysMLText(mock(IEMFEditingContext.class), pack, """
                use case flyMissionDetailed {
                    subject aircraft;
                    actor pilot;
                    include missionPlanning;
                    action planRoute;
                }
                """, new ArrayList<>());

        UseCaseUsage created = this.useCaseIn(pack, "flyMissionDetailed");
        assertThat(created).isNotNull();
        assertThat(this.typesUnder(created))
                .containsExactlyInAnyOrder("ReferenceUsage", "PartUsage", "IncludeUseCaseUsage", "ActionUsage");
    }

    /**
     * Reuse: a step detailed under one use case and referred to by others becomes an include rather
     * than duplicated prose. Only this form binds.
     */
    @Test
    void theIncludeReferenceFormBindsToTheSiblingUseCase() {
        Element pack = this.packageOps();

        this.importer.importSysMLText(mock(IEMFEditingContext.class), pack,
                "use case f2 { include missionPlanning; }", new ArrayList<>());

        assertThat(this.includedNameIn(pack, "f2")).isEqualTo("missionPlanning");
    }

    /**
     * The trap. `include use case missionPlanning;` reads like the obvious way to include a use
     * case and is what an unguided generator writes. It declares a <em>new nested</em> use case
     * that happens to share the name, binds nothing, and reports no problem - so a candidate
     * carrying it looks correct in review and lands a model whose reuse is a lie. A generated
     * include must be checked for having actually bound, not merely for parsing.
     */
    @Test
    void theIncludeDeclarationFormSilentlyBindsNothing() {
        Element pack = this.packageOps();
        var messages = new ArrayList<Message>();

        this.importer.importSysMLText(mock(IEMFEditingContext.class), pack,
                "use case f1 { include use case missionPlanning; }", messages);

        assertThat(this.includedNameIn(pack, "f1")).isNull();
        assertThat(messages).as("the parser raised nothing about it").isEmpty();
    }

    private String includedNameIn(Element pack, String useCaseName) {
        UseCaseUsage useCase = this.useCaseIn(pack, useCaseName);
        assertThat(useCase).as("use case " + useCaseName + " was not created").isNotNull();
        for (Element owned : useCase.getOwnedElement()) {
            if (owned instanceof IncludeUseCaseUsage include) {
                UseCaseUsage included = include.getUseCaseIncluded();
                if (included != null) {
                    return included.getDeclaredName();
                }
            }
        }
        return null;
    }

    private List<String> namesUnder(Element element) {
        return element.getOwnedElement().stream()
                .map(Element::getDeclaredName)
                .filter(name -> name != null)
                .toList();
    }

    private List<String> typesUnder(Element element) {
        return element.getOwnedElement().stream()
                .map(owned -> owned.eClass().getName())
                .toList();
    }

    private Element packageOps() {
        var iterator = this.parseResource().getAllContents();
        while (iterator.hasNext()) {
            var next = iterator.next();
            if (next instanceof Element element && "Ops".equals(element.getDeclaredName())) {
                return element;
            }
        }
        return fail("No package Ops");
    }

    private UseCaseUsage useCaseIn(Element parent, String declaredName) {
        return parent.getOwnedElement().stream()
                .filter(UseCaseUsage.class::isInstance)
                .map(UseCaseUsage.class::cast)
                .filter(useCase -> declaredName.equals(useCase.getDeclaredName()))
                .findFirst()
                .orElse(null);
    }

    private UseCaseUsage useCase(String declaredName) {
        var iterator = this.parseResource().getAllContents();
        while (iterator.hasNext()) {
            var next = iterator.next();
            if (next instanceof UseCaseUsage useCase && declaredName.equals(useCase.getDeclaredName())) {
                return useCase;
            }
        }
        return fail("No use case named " + declaredName);
    }

    private Resource parseResource() {
        ResourceSet resourceSet = new SysONDefaultLibrariesConfiguration(new SysONLoadDefaultLibrariesOnApplicationStartConfiguration())
                .getLibrariesResourceSet();

        SysmlToAst sysmlToAst = new SysmlToAst(null);
        var astParsingResult = sysmlToAst.convert(new ByteArrayInputStream(MODEL.getBytes()), "sysml");
        if (astParsingResult.ast().isEmpty()) {
            return fail("No AST produced, reports: " + astParsingResult.reports());
        }
        return new ASTTransformer().convertResource(astParsingResult.ast().get(), resourceSet);
    }
}
