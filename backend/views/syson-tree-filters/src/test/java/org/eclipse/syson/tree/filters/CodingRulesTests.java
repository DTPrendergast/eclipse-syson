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
package org.eclipse.syson.tree.filters;

import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaClasses;

import org.eclipse.syson.tests.architecture.AbstractCodingRulesTests;
import org.junit.jupiter.api.Test;

/**
 * Coding rules tests.
 *
 * @author DTPrendergast
 */
public class CodingRulesTests extends AbstractCodingRulesTests {

    @Override
    protected String getProjectRootPackage() {
        return ArchitectureConstants.SYSON_TREE_FILTERS_ROOT_PACKAGE;
    }

    @Override
    protected JavaClasses getClasses() {
        return ArchitectureConstants.CLASSES;
    }

    /**
     * The root package is a string, so renaming the package silently points every rule below at
     * nothing - and ArchUnit passes on an empty set of classes. This module was renamed from
     * syson-tree-metadata-filter, which is exactly when that happens.
     */
    @Test
    public void theRulesActuallyHaveClassesToCheck() {
        assertThat(ArchitectureConstants.CLASSES).isNotEmpty();
    }
}
