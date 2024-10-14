import { useResourceActionDisable, useStoreActions } from '~/store';

export const useDisableModal = () => {
  const isActionDisableModalOpen = useResourceActionDisable();

  const { clearSelectedIds, setResourceActionDisable, clearSelectedItems } =
    useStoreActions();

  const onActionDisableCancel = () => {
    clearSelectedIds();
    clearSelectedItems();
    setResourceActionDisable(false);
  };

  return {
    isActionDisableModalOpen,
    onActionDisableCancel,
  };
};
