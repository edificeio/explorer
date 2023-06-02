import { useResourceIsTrash, useStoreActions } from "~/store";

export const useTrashModal = () => {
  const isTrashedModalOpen = useResourceIsTrash();
  const { clearSelectedIds, setResourceIsTrash } = useStoreActions();

  const onTrashedCancel = () => {
    clearSelectedIds();
    setResourceIsTrash(false);
  };

  return {
    isTrashedModalOpen,
    onTrashedCancel,
  };
};
