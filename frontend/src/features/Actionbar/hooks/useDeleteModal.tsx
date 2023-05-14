import { Alert } from "@ode-react-ui/components";
import { useI18n } from "@ode-react-ui/core";
import { useHotToast } from "@ode-react-ui/hooks";
import { useDelete, useTrash } from "@services/queries";
import { useIsTrash } from "@store/store";

interface ModalProps {
  onSuccess?: () => void;
}

export default function useDeleteModal({ onSuccess }: ModalProps) {
  const { i18n } = useI18n();
  const deleteItem = useDelete();
  const trashItem = useTrash();

  // * https://github.com/pmndrs/zustand#fetching-everything
  // ! https://github.com/pmndrs/zustand/discussions/913
  const isTrashFolder = useIsTrash();

  const { hotToast } = useHotToast(Alert);

  // ? We could pass hotToast as argument inside deleteSelection or trashSelection ?
  async function onDelete() {
    try {
      if (isTrashFolder) {
        await deleteItem.mutate();
        // TODO i18n
        hotToast.success("Supprimé de la corbeille");
      } else {
        await trashItem.mutate();
        // TODO i18n
        hotToast.success(i18n("explorer.trash.title"));
      }
      onSuccess?.();
    } catch (e) {
      // TODO display an alert?
      console.error(e);
    }
  }

  return {
    isTrashFolder,
    onDelete,
  };
}
