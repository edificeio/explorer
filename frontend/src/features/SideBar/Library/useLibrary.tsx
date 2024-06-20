import {
  useLibraryUrl,
  useOdeClient,
  useOdeTheme,
  usePaths,
} from "@edifice-ui/react";
import { odeServices } from "edifice-ts-client";

export const useLibrary = () => {
  const { currentLanguage } = useOdeClient();
  const { theme } = useOdeTheme();
  const [imagePath] = usePaths();
  const libraryUrl = useLibraryUrl();

  // #WB2-1689: add end of year Library gif only for FR users
  const imageFilename =
    currentLanguage === "fr"
      ? "image-library-year-end.gif"
      : "image-library.svg";
  const imageFullURL = `${imagePath}/${theme?.bootstrapVersion}/${imageFilename}`;

  /**
   * Open Library in new tab and Track access (event: ACCESS_LIBRARY_FROM_EXPLORER).
   * Important: Event "ACCESS_LIBRARY_FROM_EXPLORER" needs to be added in infra module configuration "eventConfig.event-whitelist" in Vertx configuration file.
   */
  const handleClick = () => {
    if (libraryUrl) {
      window.open(libraryUrl, "_blank");
      odeServices.data().trackAccessLibraryFromExplorer();
    }
  };

  return { libraryUrl, imageFullURL, handleClick };
};
