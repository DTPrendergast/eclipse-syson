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
import { EditProjectNavbarMenuEntryProps, useCurrentProject } from '@eclipse-sirius/sirius-web-application';
import AutoAwesomeIcon from '@mui/icons-material/AutoAwesome';
import ListItemIcon from '@mui/material/ListItemIcon';
import ListItemText from '@mui/material/ListItemText';
import MenuItem from '@mui/material/MenuItem';
import { useState } from 'react';
import { SysONAiReviewDialog } from './SysONAiReviewDialog';

/**
 * Opens the AI review dialog from the project menu.
 *
 * The project menu is where this lives because the extension points available in
 * sirius-web-application do not include a workbench side panel, and adding one would mean changing
 * the platform - which CLAUDE.md rules out without a human decision.
 */
export const SysONAiAssistantMenuEntry = ({ onCloseContextMenu }: EditProjectNavbarMenuEntryProps) => {
  const [open, setOpen] = useState<boolean>(false);
  const { project } = useCurrentProject();
  const editingContextId: string | null = project?.currentEditingContext?.id ?? null;

  if (editingContextId === null) {
    return null;
  }

  return (
    <>
      <MenuItem
        onClick={() => setOpen(true)}
        data-testid="ai-assistant-menu-entry"
        aria-label="propose derived requirements">
        <ListItemIcon>
          <AutoAwesomeIcon fontSize="small" />
        </ListItemIcon>
        <ListItemText primary="Propose derived requirements" />
      </MenuItem>
      <SysONAiReviewDialog
        editingContextId={editingContextId}
        open={open}
        onClose={() => {
          setOpen(false);
          onCloseContextMenu();
        }}
      />
    </>
  );
};
