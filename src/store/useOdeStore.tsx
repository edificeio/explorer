/* eslint-disable @typescript-eslint/no-misused-promises */
import { create } from "zustand";

interface State {
  // idiom: IIdiom;
  // theme: ITheme;
  // app: Pick<IWebApp, "address">;
  // session: ISession;
  previousFolder: string[] | any;
}

interface Action {
  // setIdiom: (Platform: any) => void;
  // setTheme: (Platform: any) => void;
  // setApp: (app: IWebApp) => void;
  // setSession: (session: ISession) => void;
  setPreviousFolder: (previousId: string, previousName: string) => void;
  clearPreviousFolder: () => void;
}

export const useOdeStore = create<State & Action>((set, get) => ({
  // idiom: null!,
  // theme: null!,
  /* app: {
    icon: "blog",
    address: "",
    display: false,
    displayName: "",
    isExternal: false,
    name: "Blog",
    scope: [],
  }, */
  user: null!,
  // session: null!,
  previousFolder: [],
  // setIdiom: (Platform) => set({ idiom: Platform.idiom }),
  /* setTheme: async (Platform) => {
    try {
      const theme = await Platform.theme.onSkinReady();
      console.log(theme.is1D);
      console.log(theme.is2D);

      set({ theme }, true);
    } catch (e) {
      console.log(e);
    }
  }, */
  /* setApp: (app) => set({ app }, true), */
  /* setSession: (session) => {
    set({ session });
  }, */
  setPreviousFolder: (previousId: string, previousName: string) =>
    set((state) => ({
      previousFolder: [
        ...state.previousFolder,
        {
          id: previousId,
          name: previousName,
        },
      ],
    })),
  clearPreviousFolder: () => {
    set((state) => ({ previousFolder: state.previousFolder.slice(0, -1) }));
  },
}));

// export const useIdiom = () => useOdeStore((state) => state.idiom);
// export const useSetIdiom = () => useOdeStore((state) => state.setIdiom);

// export const useTheme = () => useOdeStore((state) => state.theme);
/* export const useSetTheme = () => useOdeStore((state) => state.setTheme); */

// export const useApp = () => useOdeStore((state) => state.app);
// export const useSetApp = () => useOdeStore((state) => state.setApp);

// export const useSession = () => useOdeStore((state) => state.session);
// export const useSetSession = () => useOdeStore((state) => state.setSession);

export const usePreviousFolder = () =>
  useOdeStore((state) => state.previousFolder);
export const useSetPreviousFolder = () =>
  useOdeStore((state) => state.setPreviousFolder);
