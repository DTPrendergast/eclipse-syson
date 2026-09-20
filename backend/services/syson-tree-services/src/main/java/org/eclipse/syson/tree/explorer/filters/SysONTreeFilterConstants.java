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
package org.eclipse.syson.tree.explorer.filters;

import java.util.UUID;

/**
 * SysON tree filter constants.
 *
 * @author frouene
 */
public class SysONTreeFilterConstants {

    /**
     * Hides applied metadata. Added by this fork: a metadata usage shows in the tree as an element
     * of its own, which is structurally correct and reads as a stray object next to the content it
     * annotates.
     */
    public static final String HIDE_METADATA_USAGES_TREE_FILTER_ID = UUID.nameUUIDFromBytes("SysONTreeMetadataUsagesFilter".getBytes()).toString();

    /**
     * Hides textual representations. Added by this fork: a {@code rep} holds the text of a
     * document that is authored through a view of its own, so the element in the tree is the
     * document's storage rather than something anyone edits there.
     */
    public static final String HIDE_TEXTUAL_REPRESENTATIONS_TREE_FILTER_ID = UUID.nameUUIDFromBytes("SysONTreeTextualRepresentationsFilter".getBytes()).toString();

    public static final String HIDE_MEMBERSHIPS_TREE_ITEM_FILTER_ID = UUID.nameUUIDFromBytes("SysONTreeItemMembershipsFilter".getBytes()).toString();

    public static final String HIDE_KERML_STANDARD_LIBRARIES_TREE_FILTER_ID = UUID.nameUUIDFromBytes("SysONTreeKerMLStandardLibrariesFilter".getBytes()).toString();

    public static final String HIDE_SYSML_STANDARD_LIBRARIES_TREE_FILTER_ID = UUID.nameUUIDFromBytes("SysONTreeSysMLStandardLibrariesFilter".getBytes()).toString();

    public static final String HIDE_USER_LIBRARIES_TREE_FILTER_ID = UUID.nameUUIDFromBytes("SysONTreeUserLibrariesFilter".getBytes()).toString();

    public static final String HIDE_ROOT_NAMESPACES_ID = UUID.nameUUIDFromBytes("SysONTreeRootNamespacesFilter".getBytes()).toString();

    public static final String HIDE_EXPOSE_ELEMENTS_TREE_ITEM_FILTER_ID = UUID.nameUUIDFromBytes("SysONTreeExposeElementsFilter".getBytes()).toString();

    public static final String HIDE_EXPRESSION_INTERNALS_ID = UUID.nameUUIDFromBytes("SysONTreeExpressionInternalsFilter".getBytes()).toString();
}
