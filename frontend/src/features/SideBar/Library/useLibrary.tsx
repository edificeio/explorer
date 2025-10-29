import { odeServices } from '@edifice.io/client';
import { useEdificeTheme, useLibraryUrl } from '@edifice.io/react';
import { useEffect, useState } from 'react';
import imageLibraryNeo from '@images/neo/image-library.svg';
import imageLibraryOne from '@images/one/image-library.svg';

export const useLibrary = () => {
  const libraryUrl = useLibraryUrl();

  const { theme } = useEdificeTheme();

  const [imageFullURL, setImageFullURL] = useState('');

  useEffect(() => {
    const imageMap: Record<string, string> = {
      neo: imageLibraryNeo,
      one: imageLibraryOne,
    };
    const currentTheme =
      theme?.bootstrapVersion?.toString()?.toLowerCase() || 'neo';
    setImageFullURL(imageMap[currentTheme] || imageLibraryNeo);
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
