import { IFolder } from '@edifice.io/client';
import { findNodeById, getAncestors } from '@edifice.io/react';
import { useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';

import { useMoveItem } from '~/services/queries';
import {
  useSelectedFolders,
  useSelectedResources,
  useStoreActions,
  useTreeData,
} from '~/store';

interface ModalProps {
  onSuccess?: () => void;
}

interface SelectedFolder extends IFolder {
  childrenIds?: string[];
}

export function useMoveModal({ onSuccess }: ModalProps) {
  const [selectedFolder, setSelectedFolder] = useState<string | undefined>();

  const queryClient = useQueryClient();
  const moveItem = useMoveItem();

  // * https://github.com/pmndrs/zustand#fetching-everything
  // ! https://github.com/pmndrs/zustand/discussions/913
  const selectedFolders = useSelectedFolders() as SelectedFolder[];
  const selectedResources = useSelectedResources();
  const treeData = useTreeData();

  const { fetchTreeData } = useStoreActions();

  async function onMove() {
    try {
      if (!selectedFolder) throw new Error('explorer.move.selection.empty');

      await moveItem.mutate(selectedFolder);
      await onSuccess?.();
    } catch (e) {
      // TODO display an alert?
      console.error(e);
    }
  }

  const canMove = (destination: string) => {
    const ancestors = getAncestors(treeData, destination);

    for (const selectedFolder of selectedFolders) {
      if (
        destination === selectedFolder.id ||
        destination === selectedFolder.parentId ||
        selectedFolder.childrenIds?.includes(destination) ||
        ancestors.includes(selectedFolder.id)
      ) {
        return false;
      }
    }

    for (const selectedResource of selectedResources) {
      if (
        destination ===
          (selectedResource?.folderIds && selectedResource.folderIds[0]) ||
        (selectedResource?.folderIds?.length === 0 && destination === 'default')
      ) {
        return false;
      }
    }
    return true;
  };

  return {
    treeData,
    disableSubmit: !selectedFolder,
    handleTreeItemSelect: (folderId: string) => {
      if (canMove(folderId)) {
        setSelectedFolder(folderId);
      } else {
        setSelectedFolder(undefined);
      }
    },
    handleOnTreeItemUnfold: (nodeId: string) => {
      const folder = findNodeById(treeData, nodeId);
      const hasSomeChildrenWithChildren = folder?.children?.some(
        (child) => Array.isArray(child?.children) && child.children?.length > 0,
      );

      folder?.children?.forEach((child) => {
        if (hasSomeChildrenWithChildren) return;
        fetchTreeData(child.id as string, queryClient);
      });
    },
    onMove: () => onMove(),
  };
}
