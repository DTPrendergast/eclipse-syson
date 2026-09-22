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
import { fuzzyMatch, PaletteToolOverriddenContributionComponentProps } from '@eclipse-sirius/sirius-components-palette';
import { TreePaletteContext, TreePaletteContextValue } from '@eclipse-sirius/sirius-components-trees';
import AutoAwesomeIcon from '@mui/icons-material/AutoAwesome';
import ListItemIcon from '@mui/material/ListItemIcon';
import ListItemText from '@mui/material/ListItemText';
import MenuItem from '@mui/material/MenuItem';
import React, { forwardRef, Fragment, useContext, useState } from 'react';
import { SysONAiReviewDialog } from './SysONAiReviewDialog';

/**
 * Builds the explorer palette entry that runs one assistant task on the element it was opened on.
 *
 * Registered against `paletteToolOverrideExtensionPoint`, not
 * `treeItemContextMenuEntryOverrideExtensionPoint`. Both exist and SysON registers its own entries
 * on both, but the explorer's menu is rendered by `PaletteToolSection`, which consults only the
 * palette one. Registering on the other produced an entry that drew with its server-provided label
 * and did nothing when clicked, because with no override the default row asks the backend to invoke
 * a tool that does not exist.
 *
 * The element comes from `TreePaletteContext`, so the task runs on the tree item the menu belongs
 * to rather than on whatever happens to be selected in the explorer.
 */
export const aiTaskPaletteContribution = (task: string, dialogTitle: string) =>
  forwardRef(
    (
      { onInvoked, searchedValue, tool }: PaletteToolOverriddenContributionComponentProps,
      ref: React.ForwardedRef<HTMLLIElement>
    ) => {
      const { editingContextId, item, treeId, readOnly, onClose } =
        useContext<TreePaletteContextValue>(TreePaletteContext);
      const [open, setOpen] = useState<boolean>(false);

      if (!treeId.startsWith('explorer://') || readOnly) {
        return null;
      }

      const matchResult = searchedValue ? fuzzyMatch(tool.label, searchedValue) : null;
      if (!!searchedValue && !matchResult?.matches) {
        return null;
      }

      return (
        <Fragment key={`ai-task-${task}`}>
          <MenuItem
            key={`ai-task-menu-${task}`}
            onClick={() => {
              onInvoked();
              setOpen(true);
            }}
            data-testid={`ai-task-${task}`}
            ref={ref}
            aria-label={tool.label}>
            <ListItemIcon>
              <AutoAwesomeIcon fontSize="small" />
            </ListItemIcon>
            <ListItemText primary={tool.label} />
          </MenuItem>
          {open ? (
            <SysONAiReviewDialog
              editingContextId={editingContextId}
              elementId={item.id}
              initialTask={task}
              title={dialogTitle}
              open={open}
              onClose={() => {
                setOpen(false);
                onClose();
              }}
            />
          ) : null}
        </Fragment>
      );
    }
  );

/** On a representation of an operational concept; rewrites that concept's narrative. */
export const AiNarrativeAssistantPaletteContribution = aiTaskPaletteContribution(
  'draft-narrative',
  'Narrative AI Assistant'
);

/**
 * On the operational concept package; writes the use cases from that concept's narrative and files
 * them in a 'Use Cases' package beneath it.
 */
export const AiUseCaseAssistantPaletteContribution = aiTaskPaletteContribution(
  'draft-use-cases',
  'Use Case AI Assistant'
);
