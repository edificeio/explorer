import { Heading, Radio } from "@ode-react-ui/components";
import { useI18n } from "@ode-react-ui/core";

import useShareResourceModalFooterBlog, {
  type PublicationType,
} from "../hooks/useShareResourceModalFooterBlog";

export default function ShareResourceModalFooterBlog() {
  const { i18n } = useI18n();
  const { radioPublicationValue, handleRadioPublicationChange } =
    useShareResourceModalFooterBlog();
  return (
    <>
      <hr />

      <Heading headingStyle="h4" level="h3" className="mb-16">
        {i18n("explorer.publication.steps")}
      </Heading>

      <Radio
        label={i18n("explorer.immediat.publication")}
        id="publication-now"
        name="publication"
        value={"IMMEDIATE" as PublicationType}
        model={radioPublicationValue}
        onChange={async (e) =>
          await handleRadioPublicationChange(e.target.value as PublicationType)
        }
      />
      <Radio
        label={i18n("explorer.validate.publication")}
        id="publication-validate"
        name="publication"
        value={"RESTRAINT" as PublicationType}
        model={radioPublicationValue}
        onChange={async (e) =>
          await handleRadioPublicationChange(e.target.value as PublicationType)
        }
      />
    </>
  );
}
