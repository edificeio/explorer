import {
  ExplorerFrameworkFactory,
  ConfigurationFrameworkFactory,
  NotifyFrameworkFactory,
  SessionFrameworkFactory,
  TransportFrameworkFactory,
} from "ode-ts-client";

export const sessionFramework = SessionFrameworkFactory.instance();
export const configurationFramework = ConfigurationFrameworkFactory.instance();
export const notifyFramework = NotifyFrameworkFactory.instance();
export const explorerFramework = ExplorerFrameworkFactory.instance();
export const { http } = TransportFrameworkFactory.instance();
