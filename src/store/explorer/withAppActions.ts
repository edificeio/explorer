import { IBus } from "ode-ts-client";

import { GetExplorerStateFunction, SetExplorerStateFunction } from "./types";
export const withAppActions = ({
  bus,
  toastDelay,
  get,
  set,
}: {
  bus: IBus;
  toastDelay: number;
  get: GetExplorerStateFunction;
  set: SetExplorerStateFunction;
}) => ({
  popNotifications: () => {
    const { notifications } = get();
    set((state) => ({ ...state, notifications: [] }));
    return notifications;
  },
});
