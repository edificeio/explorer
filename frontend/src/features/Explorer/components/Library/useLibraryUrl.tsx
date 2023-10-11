import { useOdeClient } from "@edifice-ui/react";
import { IUserInfo } from "edifice-ts-client";

import { capitalizeFirstLetter } from "~/utils/capitalizeFirstLetter";
import { getAppParams } from "~/utils/getAppParams";

export const useLibraryUrl = () => {
  const { user } = useOdeClient();
  const appName = capitalizeFirstLetter(getAppParams().app);

  // libraryUrl from userInfo.apps is like: https://libraryHost/?platformURL=userPlatformURL
  const libraryUrlSplitted: Array<string> | undefined = (user as IUserInfo).apps
    .find(
      (app) =>
        app.isExternal &&
        app.address.includes("library") &&
        app.name.includes("library"),
    )
    ?.address.split("?");

  let libraryHost = libraryUrlSplitted?.[0];
  if (!libraryHost?.endsWith("/")) {
    libraryHost = `${libraryHost}/`;
  }
  const platformParam = libraryUrlSplitted?.[1];
  const searchParams = `application%5B0%5D=${appName}&page=1&sort_field=views&sort_order=desc`;
  const libraryUrl = `${libraryHost}search/?${platformParam}&${searchParams}`;

  return { libraryUrl };
};
