import { useExplorerContext } from "@contexts/index";

interface DeleteModalArg {
  onSuccess?: () => void;
}

export default function useDeleteModal({ onSuccess }: DeleteModalArg) {
  const { contextRef, selectedResources, selectedFolders } =
    useExplorerContext();
  const isTrashResource =
    selectedResources.filter((e) => !e.trashed).length > 0;
  const isTrashFolder = selectedFolders.filter((e) => !e.trashed).length > 0;
  const isTrash = isTrashFolder || isTrashResource;
  async function onDelete() {
    try {
      const resourceIds = selectedResources.map((e) => e.id);
      const folderIds = selectedFolders.map((e) => e.id);
      if (isTrash) {
        await contextRef.current.trash(true, resourceIds, folderIds);
      } else {
        await contextRef.current.delete(resourceIds, folderIds);
      }
      onSuccess?.();
    } catch (e) {
      // TODO display an alert?
      console.error(e);
    }
  }

  return {
    isTrash,
    onDelete: () => {
      onDelete();
    },
  };
}
