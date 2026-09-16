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
import { TreeItemContextMenuComponentProps } from '@eclipse-sirius/sirius-components-trees';
import AutoAwesomeIcon from '@mui/icons-material/AutoAwesome';
import ListItemIcon from '@mui/material/ListItemIcon';
import ListItemText from '@mui/material/ListItemText';
import MenuItem from '@mui/material/MenuItem';
import React, { forwardRef, Fragment, useState } from 'react';
import { SysONAiReviewDialog } from './SysONAiReviewDialog';

/**
 * Builds a context-menu entry that runs one assistant task on the element the menu was opened on.
 *
 * The element comes from the tree item rather than from the explorer selection, which is the whole
 * reason these entries exist: reached from the project menu, the assistant worked on whatever was
 * selected, and a selection can be a document or a representation rather than a model element.
 */
export const aiTaskMenuContribution = (task: string, label: string, dialogTitle: string) =>
  forwardRef(
    (
      { editingContextId, treeId, item, readOnly, onClose }: TreeItemContextMenuComponentProps,
      ref: React.ForwardedRef<HTMLLIElement>
    ) => {
      const [open, setOpen] = useState<boolean>(false);

      if (!treeId.startsWith('explorer://') || readOnly) {
        return null;
      }

      return (
        <Fragment key={`ai-task-${task}`}>
          <MenuItem onClick={() => setOpen(true)} data-testid={`ai-task-${task}`} ref={ref} aria-label={label}>
            <ListItemIcon>
              <AutoAwesomeIcon fontSize="small" />
            </ListItemIcon>
            <ListItemText primary={label} />
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

export const AiDraftNarrativeMenuContribution = aiTaskMenuContribution(
  'draft-narrative',
  'Draft the narrative as prose',
  'Draft the narrative as prose'
);

export const AiDraftUseCasesMenuContribution = aiTaskMenuContribution(
  'draft-use-cases',
  'Draft use cases',
  'Draft use cases from this operational concept'
);
