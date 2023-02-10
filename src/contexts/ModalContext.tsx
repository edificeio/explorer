import {
  createContext,
  type PropsWithChildren,
  useCallback,
  useMemo,
} from "react";

import { Modal } from "@ode-react-ui/core";
import { createPortal } from "react-dom";

import useModal from "./ModalReducer";

interface ModalContextProps {
  openModal: ({
    id,
    title,
    subtitle,
    body,
    footer,
  }: {
    id: string;
    title: any;
    subtitle: any;
    body: any;
    footer: any;
  }) => void;
  closeModal: () => void;
}

export const ModalContext = createContext<ModalContextProps | null>(null);

const ModalPortal = ({ state, closeModal }: any) => {
  return (
    state.isVisible &&
    createPortal(
      <Modal id={state.id} isOpen={false} onModalClose={closeModal}>
        <Modal.Header onModalClose={closeModal}>{state.title}</Modal.Header>
        <Modal.Subtitle>{state.subtitle}</Modal.Subtitle>
        <Modal.Body>
          <p>{state.body}</p>
        </Modal.Body>
        <Modal.Footer>{state.footer}</Modal.Footer>
      </Modal>,
      document.getElementById("portal") as HTMLElement,
    )
  );
};

export const ModalProvider = ({ children }: PropsWithChildren) => {
  const [state, dispatch] = useModal();

  const openModal = useCallback(
    ({ id, title, subtitle, body, footer }: any) => {
      dispatch({
        type: "OPEN_MODAL",
        payload: {
          id,
          isVisible: true,
          title,
          subtitle,
          body,
          footer,
        },
      });
    },
    [dispatch],
  );

  const closeModal = useCallback(() => {
    dispatch({
      type: "CLOSE_MODAL",
    });
  }, [dispatch]);

  const store = useMemo(
    () => ({
      openModal,
      closeModal,
    }),
    [openModal, closeModal],
  );

  return (
    <ModalContext.Provider value={store}>
      {children}
      <ModalPortal state={state} closeModal={closeModal} />
    </ModalContext.Provider>
  );
};
