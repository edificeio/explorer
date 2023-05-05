import { Library as CoreLibrary, useOdeClient } from "@ode-react-ui/core";
import { capitalizeFirstLetter } from "@shared/utils/capitalizeFirstLetter";

const Library = () => {
  const { i18n, appCode, getBootstrapTheme } = useOdeClient();

  const LIB_URL = `https://library.opendigitaleducation.com/search/?application%5B0%5D=${capitalizeFirstLetter(
    appCode,
  )}&page=1&sort_field=views&sort_order=desc`;

  return (
    <CoreLibrary
      src={`${getBootstrapTheme()}/images/image-library.svg`}
      alt={i18n("explorer.libray.img.alt")}
      text={i18n("explorer.libray.title")}
      url={LIB_URL}
      textButton={i18n("explorer.libray.btn")}
    />
  );
};

export default Library;
