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

import org.eclipse.syson.AbstractIntegrationTests;
import org.eclipse.syson.table.requirements.view.services.RTVMutationServices;
import org.eclipse.syson.table.requirements.view.services.RTVQueryServices;
import org.eclipse.syson.table.verification.view.services.VTVMutationServices;
import org.eclipse.syson.table.verification.view.services.VTVQueryServices;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

/**
 * Diagnostic / regression test: confirms that every custom "service class" declared via an
 * {@code IJavaServiceProvider} (and so referenced from AQL expressions in a table/form/diagram description) can
 * actually be instantiated the way Sirius Web instantiates it at runtime --
 * {@code AutowireCapableBeanFactory.createBean(Class)}, per {@code ViewAQLInterpreterFactory} -- rather than
 * only via a hand-called {@code new} in a unit test.
 *
 * <p>
 * A service class with a constructor parameter that isn't itself a registered Spring bean (e.g. a plain utility
 * class not annotated {@code @Service}, such as {@code MetamodelQueryElementService}) compiles fine and passes
 * hand-written unit tests, then fails only when a user actually opens the representation --
 * {@code UnsatisfiedDependencyException}. Exactly the kind of "fails at runtime, not compile time" gotcha
 * CLAUDE.md warns about for AQL expressions in general, one level down: this catches it for the Java services
 * those expressions call into, not just the AQL strings themselves.
 * </p>
 *
 * <p>
 * Add every new module's AQL-callable service classes here as they're built.
 * </p>
 *
 * @author DTPrendergast
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class ServiceClassInstantiationTests extends AbstractIntegrationTests {

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    public void requirementsTableServiceClassesCanBeInstantiatedTheWaySiriusWebInstantiatesThem() {
        this.assertCanBeInstantiatedBySpring(RTVQueryServices.class);
        this.assertCanBeInstantiatedBySpring(RTVMutationServices.class);
    }

    @Test
    public void verificationTableServiceClassesCanBeInstantiatedTheWaySiriusWebInstantiatesThem() {
        this.assertCanBeInstantiatedBySpring(VTVQueryServices.class);
        this.assertCanBeInstantiatedBySpring(VTVMutationServices.class);
    }

    private void assertCanBeInstantiatedBySpring(Class<?> serviceClass) {
        Object instance = this.applicationContext.getAutowireCapableBeanFactory().createBean(serviceClass);

        assertThat(instance).isNotNull();
    }
}
