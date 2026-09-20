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
package org.eclipse.syson.tree.metadata.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.eclipse.sirius.components.trees.description.TreeDescription;
import org.eclipse.syson.tree.explorer.filters.SysONTreeFilterConstants;
import org.eclipse.syson.tree.explorer.view.SysONExplorerTreeDescriptionProvider;
import org.junit.jupiter.api.Test;

/**
 * Tests of the "Hide Textual Representations" explorer filter.
 *
 * @author DTPrendergast
 */
class TextualRepresentationTreeFilterTests {

    private final TextualRepresentationTreeFilterProvider filterProvider = new TextualRepresentationTreeFilterProvider();

    @Test
    void offersTheFilterOnTheSysonExplorerAndDefaultsItOn() {
        var filters = this.filterProvider.get("editing-context", this.treeDescription(SysONExplorerTreeDescriptionProvider.SYSON_EXPLORER));

        assertThat(filters).hasSize(1);
        assertThat(filters.get(0).label()).isEqualTo("Hide Textual Representations");
        // On by default: the text is authored through its own view, so the element in the tree is
        // the document's storage rather than a way in to it.
        assertThat(filters.get(0).defaultState()).isTrue();
    }

    /**
     * The id has to be SysON's own: the hiding is done by SysONExplorerFilterService, which matches
     * on the ids in its own constants. A locally invented id produces a checkbox that toggles
     * nothing, which is exactly how the metadata filter first shipped.
     */
    @Test
    void usesTheFilterIdTheExplorerServiceMatchesOn() {
        var filters = this.filterProvider.get("editing-context", this.treeDescription(SysONExplorerTreeDescriptionProvider.SYSON_EXPLORER));

        assertThat(filters.get(0).id()).isEqualTo(SysONTreeFilterConstants.HIDE_TEXTUAL_REPRESENTATIONS_TREE_FILTER_ID);
    }

    @Test
    void leavesOtherTreesAlone() {
        assertThat(this.filterProvider.get("editing-context", this.treeDescription("Some Other Explorer"))).isEmpty();
    }

    private TreeDescription treeDescription(String label) {
        // Mocked rather than built: the filter reads only the label, and TreeDescription's builder
        // demands a dozen providers that would say nothing about what is being tested.
        TreeDescription treeDescription = mock(TreeDescription.class);
        when(treeDescription.getLabel()).thenReturn(label);
        return treeDescription;
    }
}
