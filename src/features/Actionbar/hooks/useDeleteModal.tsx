import { useExplorerContext } from "@contexts/index";

interface DeleteModalArg {
  onSuccess?: () => void;
}

export default function useDeleteModal({ onSuccess }: DeleteModalArg) {
  const { context, selectedResources, selectedFolders } = useExplorerContext();

  async function onDelete() {
    try {
      const resourceIds = selectedResources.map((e) => e.id);
      const folderIds = selectedFolders.map((e) => e.id);
      await context.delete(resourceIds, folderIds);
      onSuccess?.();
    } catch (e) {
      // TODO display an alert?
      console.error(e);
    }
  }

  return {
    onDelete: () => {
      onDelete();
    },
  };
}
