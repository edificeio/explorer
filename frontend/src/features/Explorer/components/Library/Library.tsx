import { Library as CoreLibrary } from "@ode-react-ui/components";
import { useOdeClient } from "@ode-react-ui/core";
import { capitalizeFirstLetter } from "@shared/utils/capitalizeFirstLetter";
import { getAppParams } from "@shared/utils/getAppParams";

const Library = () => {
  const params = getAppParams();
  const { i18n, theme } = useOdeClient();

  const LIB_URL = `https://library.opendigitaleducation.com/search/?application%5B0%5D=${capitalizeFirstLetter(
    params.app,
  )}&page=1&sort_field=views&sort_order=desc`;

  return (
    <CoreLibrary
      src={`${theme?.bootstrapPath}/images/image-library.svg`}
      url={LIB_URL}
      alt={i18n("explorer.libray.img.alt")}
      text={i18n("explorer.libray.title")}
      textButton={i18n("explorer.libray.btn")}
    />
  );
};

export default Library;
