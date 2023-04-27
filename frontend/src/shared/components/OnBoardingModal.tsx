import { Suspense, useEffect, useState } from "react";

import {
  Modal,
  Button,
  Image,
  useOdeClient,
  LoadingScreen,
} from "@ode-react-ui/core";
import { useToggle } from "@ode-react-ui/hooks";
import { getOnboardingTrash, saveOnboardingTrash } from "@services/api";
import { imageBootstrap } from "@shared/constants";
import { createPortal } from "react-dom";
import { Pagination } from "swiper";
import { Swiper, SwiperSlide } from "swiper/react";
import "swiper/css";
import "swiper/css/pagination";

export default function OnBoardingTrash(): JSX.Element | null {
  const { i18n } = useOdeClient();
  const [swiperInstance, setSwiperInstance] = useState<any>();
  const [swiperProgress, setSwiperprogress] = useState<number>(0);
  const [isOnboardingTrash, setIsOnboardingTrash] = useState(false);
  const [isOpen, toggle] = useToggle(true);

  useEffect(() => {
    (async () => {
      const response: any = await getOnboardingTrash();
      if (response) {
        setIsOnboardingTrash(JSON.parse(response?.showOnboardingTrash));
        return;
      }
      setIsOnboardingTrash(true);
    })();
  }, []);

  const handleSavePreference = async () => {
    await saveOnboardingTrash({ onSuccess: toggle() });
  };

  const handleCloseModal = () => {
    toggle();
  };

  return isOnboardingTrash && isOpen
    ? createPortal(
        <Suspense fallback={<LoadingScreen />}>
          <Modal
            id="TrashModal"
            onModalClose={handleCloseModal}
            size="md"
            isOpen={isOpen}
            focusId="nextButtonId"
          >
            <Modal.Header onModalClose={handleCloseModal}>
              {i18n("explorer.modal.onboarding.trash.title")}
            </Modal.Header>
            <Modal.Body>
              <Swiper
                modules={[Pagination]}
                onSwiper={(swiper) => {
                  setSwiperInstance(swiper);
                }}
                onSlideChange={(swiper) => {
                  setSwiperprogress(swiper.progress);
                }}
                pagination={{
                  clickable: true,
                }}
              >
                <SwiperSlide>
                  <Image
                    width="270"
                    height="140"
                    className="mx-auto my-12"
                    loading="lazy"
                    src={`${imageBootstrap}/onboarding/corbeille-menu.svg`}
                    alt={i18n("explorer.modal.onboarding.trash.screen1.alt")}
                  />
                  <p>{i18n("explorer.modal.onboarding.trash.screen1.title")}</p>
                </SwiperSlide>
                <SwiperSlide>
                  <Image
                    width="270"
                    height="140"
                    className="mx-auto"
                    loading="lazy"
                    src={`${imageBootstrap}/onboarding/corbeille-notif.svg`}
                    alt={i18n("explorer.modal.onboarding.trash.screen2.alt")}
                  />
                  <p>{i18n("explorer.modal.onboarding.trash.screen2.title")}</p>
                </SwiperSlide>
                <SwiperSlide>
                  <Image
                    width="270"
                    height="140"
                    className="mx-auto"
                    loading="lazy"
                    src={`${imageBootstrap}/onboarding/corbeille-delete.svg`}
                    alt={i18n("explorer.modal.onboarding.trash.screen3.alt")}
                  />
                  <p>{i18n("explorer.modal.onboarding.trash.screen3.title")}</p>
                </SwiperSlide>
              </Swiper>
            </Modal.Body>
            <Modal.Footer>
              <Button
                type="button"
                color="tertiary"
                variant="ghost"
                onClick={handleCloseModal}
              >
                {i18n("explorer.modal.onboarding.trash.later")}
              </Button>

              {swiperProgress > 0 && (
                <Button
                  type="button"
                  color="primary"
                  variant="outline"
                  onClick={() => swiperInstance.slidePrev()}
                >
                  {i18n("explorer.modal.onboarding.trash.prev")}
                </Button>
              )}
              {swiperProgress < 1 && (
                <Button
                  id="nextButtonId"
                  type="button"
                  color="primary"
                  variant="filled"
                  onClick={() => swiperInstance.slideNext()}
                >
                  {i18n("explorer.modal.onboarding.trash.next")}
                </Button>
              )}
              {swiperProgress === 1 && (
                <Button
                  type="button"
                  color="primary"
                  variant="filled"
                  onClick={handleSavePreference}
                >
                  {i18n("explorer.modal.onboarding.trash.close")}
                </Button>
              )}
            </Modal.Footer>
          </Modal>
        </Suspense>,
        document.getElementById("portal") as HTMLElement,
      )
    : null;
}
