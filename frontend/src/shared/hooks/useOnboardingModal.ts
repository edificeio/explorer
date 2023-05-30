import { useEffect, useState } from "react";

import { getOnboardingTrash, saveOnboardingTrash } from "~/services/api";

export const useOnboardingModal = () => {
  const [isOnboardingTrash, setIsOnboardingTrash] = useState(false);
  const [isOpen, setIsOpen] = useState(true);

  useEffect(() => {
    (async () => {
      const response: any = await getOnboardingTrash();
      if (response) {
        setIsOnboardingTrash(JSON.parse(response?.showOnboardingTrash));
        return;
      }
      setIsOnboardingTrash(true);
    })();
  }, []);

  const handleSavePreference = async () => {
    await saveOnboardingTrash({ onSuccess: () => setIsOpen(false) });
  };

  return {
    isOnboardingTrash,
    isOpen,
    handleSavePreference,
    setIsOpen,
  };
};
