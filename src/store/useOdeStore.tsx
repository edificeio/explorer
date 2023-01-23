/* eslint-disable @typescript-eslint/no-misused-promises */
import {
  ConfigurationFrameworkFactory,
  ExplorerFrameworkFactory,
  IIdiom,
  ISession,
  IWebApp,
  NotifyFrameworkFactory,
  SessionFrameworkFactory,
  TransportFrameworkFactory,
} from "ode-ts-client";
import { create } from "zustand";

const { http } = TransportFrameworkFactory.instance();
const configureFramework = ConfigurationFrameworkFactory.instance();
const explorerFramework = ExplorerFrameworkFactory.instance();
const notifyFramework = NotifyFrameworkFactory.instance();
const sessionFramework = SessionFrameworkFactory.instance();

export const { Platform } = configureFramework;

export const basePath = Platform.theme.basePath + "/img/illustrations";

interface State {
  idiom: IIdiom;
  is1d: boolean;
  currentApp: Pick<IWebApp, "address">;
  user: null;
  session: ISession;
  previousFolder: string[] | any;
}

interface Action {
  setIdiom: () => void;
  setIs1d: (response: boolean) => void;
  setCurrentApp: (app: IWebApp) => void;
  setUser: () => void;
  setSession: () => void;
  setPreviousFolder: (previousId: string, previousName: string) => void;
  clearPreviousFolder: () => void;
}

export const useOdeStore = create<State & Action>((set, get) => ({
  idiom: Platform.idiom,
  is1d: false,
  currentApp: {
    icon: "blog",
    address: "",
    display: false,
    displayName: "",
    isExternal: false,
    name: "Blog",
    scope: [],
  },
  user: null!,
  session: null!,
  previousFolder: [],
  setIdiom: () => set({ idiom: Platform.idiom }),
  setIs1d: (response) => set({ is1d: response }),
  setCurrentApp: (app) => set({ currentApp: app }),
  setUser: async () => {
    const response = await fetch("/userbook/api/person");
    const responseJson = await response.json();
    const user = responseJson.result[0];
    set({ user });
  },
  setSession: async () => {
    try {
      await sessionFramework.initialize();
      set({ session: sessionFramework.session });
    } catch (e) {
      console.log(e); // An unrecovable error occured
    }
  },
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

export const useIdiom = () => useOdeStore((state) => state.idiom);

export const useIs1d = () => useOdeStore((state) => state.is1d);
export const useSetIs1d = () => useOdeStore((state) => state.setIs1d);

export const useCurrentApp = () => useOdeStore((state) => state.currentApp);
export const useSetCurrentApp = () =>
  useOdeStore((state) => state.setCurrentApp);

export const useUser = () => useOdeStore((state) => state.user);
export const useSetUser = () => useOdeStore((state) => state.setUser);

export const useSession = () => useOdeStore((state) => state.session);
export const useSetSession = () => useOdeStore((state) => state.setSession);

export const usePreviousFolder = () =>
  useOdeStore((state) => state.previousFolder);
export const useSetPreviousFolder = () =>
  useOdeStore((state) => state.setPreviousFolder);

export {
  http,
  configureFramework,
  explorerFramework,
  notifyFramework,
  sessionFramework,
};
