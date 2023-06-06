import { useState } from "react";

import { FOLDER } from "ode-ts-client";

export default function useTreeView() {
  const [isOpenedModal, setOpenedModal] = useState<boolean>(false);
  const onClose = () => {
    setOpenedModal(false);
  };

  const onOpen = () => {
    setOpenedModal(true);
  };

  const onCreateSuccess = () => {
    setOpenedModal(false);
  };

  return {
    trashId: FOLDER.BIN,
    isOpenedModal,
    onOpen,
    onClose,
    onCreateSuccess,
  };
}
