import { Alert } from "@ode-react-ui/components";
import { useHotToast } from "@ode-react-ui/hooks";
import { useTranslation } from "react-i18next";

import { useDelete, useTrash } from "~/services/queries";
import { useIsTrash } from "~/store";

interface ModalProps {
  onSuccess?: () => void;
}

export default function useDeleteModal({ onSuccess }: ModalProps) {
  const { t } = useTranslation();
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
        hotToast.success(t("explorer.removed.from.trash"));
      } else {
        await trashItem.mutate();
        hotToast.success(t("explorer.trash.title"));
      }
      onSuccess?.();
    } catch (e) {
      console.error(e);
    }
  }

  return {
    isTrashFolder,
    onDelete,
  };
}
