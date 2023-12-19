import { useEffect, useState } from "react";

import { getOnboardingTrash, saveOnboardingTrash } from "~/services/api";

export const useOnboardingModal = (
  value: string,
  { onSuccess }: { onSuccess: () => void },
) => {
  const [isOnboardingTrash, setIsOnboardingTrash] = useState(false);
  const [isOpen, setIsOpen] = useState(true);

  useEffect(() => {
    (async () => {
      const response: any = await getOnboardingTrash(value);
      if (response) {
        setIsOnboardingTrash(JSON.parse(response?.showOnboardingTrash));
        return;
      }
      setIsOnboardingTrash(true);
    })();
  }, []);

  const handleSavePreference = async () => {
    await saveOnboardingTrash({ value, onSuccess });
  };

  return {
    isOnboardingTrash,
    isOpen,
    handleSavePreference,
    setIsOpen,
  };
};
