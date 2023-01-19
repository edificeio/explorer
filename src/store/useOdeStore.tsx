import {
  ConfigurationFrameworkFactory,
  ExplorerFrameworkFactory,
  IIdiom,
  IWebApp,
  NotifyFrameworkFactory,
  SessionFrameworkFactory,
  TransportFrameworkFactory,
} from "ode-ts-client";
import { create } from "zustand";

const session = SessionFrameworkFactory.instance();
const configure = ConfigurationFrameworkFactory.instance();
const explorer = ExplorerFrameworkFactory.instance();
const notif = NotifyFrameworkFactory.instance();
const { http } = TransportFrameworkFactory.instance();

export const { Platform } = configure;

interface State {
  idiom: IIdiom;
  is1d: boolean;
  currentApp: IWebApp;
  setIdiom: () => void;
  set1d: (response: boolean) => void;
  setCurrentApp: (app: IWebApp) => void;
}

export const useOdeStore = create<State>()((set, get) => ({
  idiom: Platform.idiom,
  is1d: false,
  currentApp: null!,
  setIdiom: () => set(() => ({ idiom: Platform.idiom })),
  set1d: (response) => set(() => ({ is1d: response })),
  setCurrentApp: (app) => set(() => ({ currentApp: app })),
}));

export const useIdiom = () => useOdeStore((state) => state.idiom);
export const useIs1d = () => useOdeStore((state) => state.is1d);
export const useSet1d = () => useOdeStore((state) => state.set1d);
export const useCurrentApp = () => useOdeStore((state) => state.currentApp);
export const useSetCurrentApp = () =>
  useOdeStore((state) => state.setCurrentApp);

export { session, explorer, notif, http };
