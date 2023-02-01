import { useExplorerContext } from "@contexts/index";

interface DeleteModalArg {
  onSuccess?: () => void;
}

export default function useDeleteModal({ onSuccess }: DeleteModalArg) {
  const { getIsTrashSelected, deleteSelection, trashSelection } =
    useExplorerContext();
  const isAlreadyInTrash = getIsTrashSelected();
  async function onDelete() {
    try {
      if (isAlreadyInTrash) {
        await deleteSelection();
      } else {
        await trashSelection();
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
