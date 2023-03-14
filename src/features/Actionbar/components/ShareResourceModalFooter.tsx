import useExplorerStore from "@store/index";
import { APP } from "ode-ts-client";

import ShareResourceModalFooterBlog from "./ShareResourceModalFooterBlog";

export default function ShareResourceModalFooter() {
  const { app } = useExplorerStore((state) => state.searchParams);
  if (app === APP.BLOG) {
    return <ShareResourceModalFooterBlog />;
  }
  return <></>;
}
