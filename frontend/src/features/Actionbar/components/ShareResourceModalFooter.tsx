import { useSearchParams } from "@store/store";
import { APP } from "ode-ts-client";

import ShareResourceModalFooterBlog from "./ShareResourceModalFooterBlog";

export default function ShareResourceModalFooter() {
  const { app } = useSearchParams();
  if (app === APP.BLOG) {
    return <ShareResourceModalFooterBlog />;
  }
  return <></>;
}
