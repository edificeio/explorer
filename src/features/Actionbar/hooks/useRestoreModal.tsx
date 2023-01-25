import { useExplorerContext } from "@contexts/index";
import { FOLDER } from "ode-ts-client";

interface RestoreModalArg {
  onSuccess?: () => void;
}

export default function useRestoreModal({ onSuccess }: RestoreModalArg) {
  const { contextRef, selectedResources, selectedFolders } =
    useExplorerContext();
  const isAlreadyInTrash =
    contextRef.current.getSearchParameters().filters.folder === FOLDER.BIN;
  async function onRestore() {
    try {
      const resourceIds = selectedResources.map((e) => e.id);
      const folderIds = selectedFolders.map((e) => e.id);
      if (isAlreadyInTrash) {
        await contextRef.current.trash(false, resourceIds, folderIds);
      } else {
        throw new Error("Cannot restore untrashed resources");
      }
      onSuccess?.();
    } catch (e) {
      // TODO display an alert?
      console.error(e);
    }
  }

  return {
    isAlreadyInTrash,
    onRestore: () => {
      onRestore();
    },
  };
}
