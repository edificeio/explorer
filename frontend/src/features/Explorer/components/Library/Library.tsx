import { Library as CoreLibrary } from "@ode-react-ui/components";
import { useOdeClient } from "@ode-react-ui/core";

import { useLibraryUrl } from "./useLibraryUrl";

const Library = () => {
  const { i18n, theme } = useOdeClient();
  const { libraryUrl } = useLibraryUrl();

  return (
    <CoreLibrary
      src={`${theme?.bootstrapPath}/images/image-library.svg`}
      url={libraryUrl}
      alt={i18n("explorer.libray.img.alt")}
      text={i18n("explorer.libray.title")}
      textButton={i18n("explorer.libray.btn")}
    />
  );
};

export default Library;
