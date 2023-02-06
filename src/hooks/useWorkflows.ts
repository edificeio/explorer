import { type ISession } from "ode-ts-client";

export default function useHasWorkflow(session: ISession) {
  const workflows: Record<string, Record<string, boolean> | boolean> = {
    conversation: {
      view: session?.hasWorkflow(
        "org.entcore.conversation.controllers.ConversationController|view",
      ),
    },
    community: session?.hasWorkflow(
      "net.atos.entng.community.controllers.CommunityController|view",
    ),
    search: session?.hasWorkflow(
      "fr.openent.searchengine.controllers.SearchEngineController|view",
    ),
    zimbra: {
      view: session?.hasWorkflow(
        "fr.openent.zimbra.controllers.ZimbraController|view",
      ),
      preauth: session?.hasWorkflow(
        "fr.openent.zimbra.controllers.ZimbraController|preauth",
      ),
    },
  };

  return { workflows };
}
