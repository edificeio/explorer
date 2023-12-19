import { useEffect, useState } from "react";

import { getOnboardingTrash, saveOnboardingTrash } from "~/services/api";

export const useOnboardingModal = (id: string) => {
  const [isOpen, setIsOpen] = useState(true);
  const [isOnboardingTrash, setIsOnboardingTrash] = useState(false);

  useEffect(() => {
    (async () => {
      const response: { showOnboardingTrash: boolean } =
        await getOnboardingTrash(id);

      if (response) {
        const { showOnboardingTrash } = response;
        setIsOnboardingTrash(showOnboardingTrash);
        return;
      }
      setIsOnboardingTrash(true);
    })();
  }, [id]);

  useEffect(() => {
    if (isOnboardingTrash) console.log(isOnboardingTrash);
  }, [isOnboardingTrash]);

  const handleSavePreference = async () => {
    await saveOnboardingTrash(id);
    setIsOpen(false);
  };

  return {
    isOpen,
    setIsOpen,
    isOnboardingTrash,
    handleSavePreference,
  };
};
