import { Heading, Radio } from "@ode-react-ui/core";

import useShareResourceModalFooterBlog, {
  type PublicationType,
} from "../hooks/useShareResourceModalFooterBlog";

export default function ShareResourceModalFooterBlog() {
  const { radioPublicationValue, handleRadioPublicationChange } =
    useShareResourceModalFooterBlog();
  return (
    <>
      <hr />

      <Heading headingStyle="h4" level="h3" className="mb-16">
        Circuit de publication des billets
      </Heading>

      <Radio
        label="Publication immédiate"
        id="publication-now"
        name="publication"
        value={"IMMEDIATE" as PublicationType}
        model={radioPublicationValue}
        onChange={async (e) =>
          await handleRadioPublicationChange(e.target.value as PublicationType)
        }
      />
      <Radio
        label="Billets soumis à validation"
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
