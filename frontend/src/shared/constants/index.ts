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

// translate method
export const translate = configurationFramework.Platform.idiom.translate;

// Ode Bootstrap
export const imageBootstrap = "/assets/themes/ode-bootstrap/images";
