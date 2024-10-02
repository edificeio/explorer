import { useLibraryUrl, useOdeTheme, usePaths } from "@edifice-ui/react";
import { odeServices } from "edifice-ts-client";

export const useLibrary = () => {
  const { theme } = useOdeTheme();
  const [imagePath] = usePaths();
  const libraryUrl = useLibraryUrl();

  const imageFullURL = `${imagePath}/${theme?.bootstrapVersion}/image-library.svg`;

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
