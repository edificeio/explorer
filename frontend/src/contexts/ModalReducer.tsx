import { useReducer } from "react";

const initialState = {
  id: "modal",
  isVisible: false,
  title: "",
  subtitle: "",
  body: null,
  footer: null,
};

const OPEN_MODAL = "OPEN_MODAL";
const CLOSE_MODAL = "CLOSE_MODAL";

const reducer = (state: any = initialState, action: any) => {
  switch (action.type) {
    case OPEN_MODAL: {
      const {
        payload: { id, title, subtitle, body, footer },
      } = action;

      return {
        ...state,
        id,
        isVisible: true,
        title,
        subtitle,
        body,
        footer,
      };
    }
    case CLOSE_MODAL: {
      return {
        isVisible: false,
        title: "",
        subtitle: "",
        body: "",
        footer: "",
      };
    }
    default:
      throw new Error();
  }
};

export default function useModal() {
  return useReducer(reducer, {
    id: "modal",
    isVisible: false,
    title: "",
    subtitle: "",
    body: null,
    footer: null,
  });
}
