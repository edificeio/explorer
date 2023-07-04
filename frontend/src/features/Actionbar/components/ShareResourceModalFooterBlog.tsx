import { Heading, Radio } from "@ode-react-ui/components";
import { useOdeClient } from "@ode-react-ui/core";
import { useTranslation } from "react-i18next";

import { type PublicationType } from "../hooks/useShareResourceModalFooterBlog";

export interface ShareResourceModalFooterBlogProps {
  radioPublicationValue: PublicationType | string;
  onRadioPublicationChange: (event: PublicationType) => void;
}

export default function ShareResourceModalFooterBlog({
  radioPublicationValue,
  onRadioPublicationChange,
}: ShareResourceModalFooterBlogProps) {
  const { appCode } = useOdeClient();
  const { t } = useTranslation(appCode);
  return (
    <>
      <hr />

      <Heading headingStyle="h4" level="h3" className="mb-16">
        {t("explorer.publication.steps")}
      </Heading>

      <Radio
        label={t("explorer.immediat.publication")}
        id="publication-now"
        name="publication"
        value={"IMMEDIATE" as PublicationType}
        model={radioPublicationValue as string}
        onChange={(e) =>
          onRadioPublicationChange(e.target.value as PublicationType)
        }
      />
      <Radio
        label={t("explorer.validate.publication")}
        id="publication-validate"
        name="publication"
        value={"RESTRAINT" as PublicationType}
        model={radioPublicationValue as string}
        onChange={(e) =>
          onRadioPublicationChange(e.target.value as PublicationType)
        }
      />
    </>
  );
}
