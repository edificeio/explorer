import { useEffect, useState } from "react";

import { Modal, Button, Image, useOdeClient } from "@ode-react-ui/core";
import { useToggle } from "@ode-react-ui/hooks";
import { imageBootstrap } from "@shared/constants";
import { odeServices } from "ode-ts-client";
import { Pagination } from "swiper";
import { Swiper, SwiperSlide } from "swiper/react";

// Import Swiper styles
import "swiper/css";
import "swiper/css/pagination";

export function OnBoardingTrash(): JSX.Element | null {
  const { i18n } = useOdeClient();
  const [swiperInstance, setSwiperInstance] = useState<any>();
  const [swiperProgress, setSwiperprogress] = useState<number>(0);
  const [isOnboardingTrash, setIsOnboardingTrash] = useState(false);
  const [isOpen, toggle] = useToggle(true);

  useEffect(() => {
    (async () => {
      const response: any = await getOnboardingTrash();
      if (!response) {
        setIsOnboardingTrash(true);
      } else {
        // si la response n'est pas undefined, alors on set
        setIsOnboardingTrash(JSON.parse(response?.showOnboardingTrash));
      }
    })();
  }, []);

  async function getOnboardingTrash() {
    const res = await odeServices
      .conf()
      .getPreference<{ showOnboardingTrash: boolean }>("showOnboardingTrash");
    return res;
  }

  async function setOnboardingTrash() {
    const result = await odeServices
      .conf()
      .savePreference(
        "showOnboardingTrash",
        JSON.stringify({ showOnboardingTrash: false }),
      );
    toggle(false);
    return result;
  }

  const handleCloseModal = () => {
    toggle(false);
  };

  return isOnboardingTrash ? (
    <Modal
      id="TrashModal"
      onModalClose={handleCloseModal}
      size="md"
      isOpen={isOpen}
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
            <Modal.Subtitle>
              {i18n("explorer.modal.onboarding.trash.screen1.title")}
            </Modal.Subtitle>
            <Image
              width="270"
              height="140"
              className="mx-auto"
              loading="lazy"
              src={`${imageBootstrap}/onboarding/corbeille-notif.svg`}
              alt={i18n("explorer.modal.onboarding.trash.screen1.alt")}
            />
            <p>{i18n("explorer.modal.onboarding.trash.screen1.subtitle")}</p>
          </SwiperSlide>
          <SwiperSlide>
            <Modal.Subtitle>
              {i18n("explorer.modal.onboarding.trash.screen2.title")}
            </Modal.Subtitle>
            <Image
              width="270"
              height="140"
              className="mx-auto"
              loading="lazy"
              src={`${imageBootstrap}/onboarding/corbeille-delete.svg`}
              alt={i18n("explorer.modal.onboarding.trash.screen2.alt")}
            />
            <p>{i18n("explorer.modal.onboarding.trash.screen2.subtitle")}</p>
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
            onClick={setOnboardingTrash}
          >
            {i18n("explorer.modal.onboarding.trash.close")}
          </Button>
        )}
      </Modal.Footer>
    </Modal>
  ) : null;
}
