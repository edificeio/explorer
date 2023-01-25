import { useExplorerContext } from "@contexts/index";
import { FOLDER } from "ode-ts-client";

interface DeleteModalArg {
  onSuccess?: () => void;
}

export default function useDeleteModal({ onSuccess }: DeleteModalArg) {
  const { contextRef, selectedResources, selectedFolders } =
    useExplorerContext();
  const isAlreadyInTrash =
    contextRef.current.getSearchParameters().filters.folder === FOLDER.BIN;
  async function onDelete() {
    try {
      const resourceIds = selectedResources.map((e) => e.id);
      const folderIds = selectedFolders.map((e) => e.id);
      if (isAlreadyInTrash) {
        await contextRef.current.delete(resourceIds, folderIds);
      } else {
        await contextRef.current.trash(true, resourceIds, folderIds);
      }
      onSuccess?.();
    } catch (e) {
      // TODO display an alert?
      console.error(e);
    }
  }

  return {
    isAlreadyInTrash,
    onDelete: () => {
      onDelete();
    },
  };
}
