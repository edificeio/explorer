import { APP } from "edifice-ts-client";

import ShareResourceModalFooterBlog from "./ShareResourceModalFooterBlog";
import { useSearchParams } from "~/store";

export default function ShareResourceModalFooter({
  radioPublicationValue,
  onRadioPublicationChange,
}: any) {
  const { app } = useSearchParams();
  if (app === APP.BLOG) {
    return (
      <ShareResourceModalFooterBlog
        radioPublicationValue={radioPublicationValue}
        onRadioPublicationChange={onRadioPublicationChange}
      />
    );
  }
  return <></>;
}
