import { APP } from "ode-ts-client";

import ShareResourceModalFooterBlog from "./ShareResourceModalFooterBlog";
import { useSearchParams } from "~store/store";

export default function ShareResourceModalFooter() {
  const { app } = useSearchParams();
  if (app === APP.BLOG) {
    return <ShareResourceModalFooterBlog />;
  }
  return <></>;
}
