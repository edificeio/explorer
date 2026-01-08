import { odeServices } from '@edifice.io/client';
import { useEdificeTheme, useLibraryUrl } from '@edifice.io/react';
import { useEffect, useState } from 'react';

export const useLibrary = () => {
  const libraryUrl = useLibraryUrl();

  const { theme } = useEdificeTheme();

  const [imageFullURL, setImageFullURL] = useState('');

  useEffect(() => {
    const imageMap: Record<string, string> = {
      neo: '/explorer/public/img/image-library-neo.svg',
      one: '/explorer/public/img/image-library-one.svg',
    };
    const currentTheme =
      theme?.bootstrapVersion?.toString()?.toLowerCase() || 'neo';
    setImageFullURL(
      imageMap[currentTheme] || '/explorer/public/img/image-library.svg',
    );
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
