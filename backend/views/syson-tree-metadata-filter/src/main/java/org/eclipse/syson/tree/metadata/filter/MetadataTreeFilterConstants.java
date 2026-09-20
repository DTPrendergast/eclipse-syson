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

import java.util.UUID;

/**
 * Identifier of the filter that hides metadata usages.
 *
 * <p>
 * Derived from a stable name the same way SysON derives its own filter ids, so it survives
 * restarts and does not collide with theirs.
 * </p>
 *
 * @author DTPrendergast
 */
public final class MetadataTreeFilterConstants {

    public static final String HIDE_METADATA_USAGES_TREE_FILTER_ID = UUID.nameUUIDFromBytes("SysONTreeMetadataUsagesFilter".getBytes()).toString();

    private MetadataTreeFilterConstants() {
        // Prevent instantiation
    }
}
