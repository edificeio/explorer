import { odeServices } from '@edifice.io/client';
import { useEdificeTheme, useLibraryUrl } from '@edifice.io/react';
import { useEffect, useState } from 'react';

export const useLibrary = () => {
  const libraryUrl = useLibraryUrl();

  const { theme } = useEdificeTheme();

  const [imageFullURL, setImageFullURL] = useState('');

  useEffect(() => {
    const getImageLibrary = async () => {
      const imageLibrary = await import(
        `@images/${theme?.bootstrapVersion}/image-library.svg`
      );
      setImageFullURL(imageLibrary.default);
    };
    getImageLibrary();
  }, [theme]);

  /**
   * Open Library in new tab and Track access (event: ACCESS_LIBRARY_FROM_EXPLORER).
   * Important: Event "ACCESS_LIBRARY_FROM_EXPLORER" needs to be added in infra module configuration "eventConfig.event-whitelist" in Vertx configuration file.
   */
  const handleClick = () => {
    if (libraryUrl) {
      window.open(libraryUrl, '_blank');
      odeServices.data().trackAccessLibraryFromExplorer();
    }
  };

  return { libraryUrl, imageFullURL, handleClick };
};
