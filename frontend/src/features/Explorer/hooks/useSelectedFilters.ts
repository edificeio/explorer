import { useState, useEffect } from "react";

import { useCurrentFolder, useStoreActions } from "~/store";

export const useSelectedFilters = () => {
  const [state, setState] = useState<(string | number)[]>([]);

  const currentFolder = useCurrentFolder();
  const { setSearchParams } = useStoreActions();

  useEffect(() => {
    const isOwnerSelected = (): boolean | undefined => {
      return state.includes(1) ? true : undefined;
    };

    const isSharedSelected = (): boolean | undefined => {
      return state.includes(2) ? true : undefined;
    };

    const isPublicSelected = (): boolean | undefined => {
      return state.includes(7) ? true : undefined;
    };

    setSearchParams({
      filters: {
        owner: isOwnerSelected(),
        public: isPublicSelected(),
        shared: isSharedSelected(),
        folder: currentFolder ? currentFolder.id : "default",
      },
    });
  }, [currentFolder, setSearchParams, state]);

  return [state, setState];
};
