import { useState } from "react";

import { Modal, Button, Image, usePaths } from "@edifice-ui/react";
import { createPortal } from "react-dom";
import { useTranslation } from "react-i18next";
import { Pagination } from "swiper/modules";
import { Swiper, SwiperSlide } from "swiper/react";

import "swiper/css";
import "swiper/css/pagination";

export default function OnboardingTrash({
  isOpen,
  setIsOpen,
  handleSavePreference,
}: {
  isOpen: boolean;
  setIsOpen: (bool: boolean) => void;
  handleSavePreference: () => void;
}): JSX.Element | null {
  const [imagePath] = usePaths();
  const { t } = useTranslation();
  const [swiperInstance, setSwiperInstance] = useState<any>();
  const [swiperProgress, setSwiperprogress] = useState<number>(0);

  return isOpen
    ? createPortal(
        <Modal
          id="TrashModal"
          onModalClose={() => setIsOpen(false)}
          size="md"
          isOpen={isOpen}
          focusId="nextButtonId"
        >
          <Modal.Header onModalClose={() => setIsOpen(false)}>
            {t("explorer.modal.onboarding.trash.title")}
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
                  src={`${imagePath}/onboarding/illu-trash-menu.svg`}
                  alt={t("explorer.modal.onboarding.trash.screen1.alt")}
                />
                <p>{t("explorer.modal.onboarding.trash.screen1.title")}</p>
              </SwiperSlide>
              <SwiperSlide>
                <Image
                  width="270"
                  height="140"
                  className="mx-auto"
                  loading="lazy"
                  src={`${imagePath}/onboarding/illu-trash-notif.svg`}
                  alt={t("explorer.modal.onboarding.trash.screen2.alt")}
                />
                <p>{t("explorer.modal.onboarding.trash.screen2.title")}</p>
              </SwiperSlide>
              <SwiperSlide>
                <Image
                  width="270"
                  height="140"
                  className="mx-auto"
                  loading="lazy"
                  src={`${imagePath}/onboarding/illu-trash-delete.svg`}
                  alt={t("explorer.modal.onboarding.trash.screen3.alt")}
                />
                <p>{t("explorer.modal.onboarding.trash.screen3.title")}</p>
              </SwiperSlide>
            </Swiper>
          </Modal.Body>
          <Modal.Footer>
            <Button
              type="button"
              color="tertiary"
              variant="ghost"
              onClick={() => setIsOpen(false)}
            >
              {t("explorer.modal.onboarding.trash.later")}
            </Button>

            {swiperProgress > 0 && (
              <Button
                type="button"
                color="primary"
                variant="outline"
                onClick={() => swiperInstance.slidePrev()}
              >
                {t("explorer.modal.onboarding.trash.prev")}
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
                {t("explorer.modal.onboarding.trash.next")}
              </Button>
            )}
            {swiperProgress === 1 && (
              <Button
                type="button"
                color="primary"
                variant="filled"
                onClick={handleSavePreference}
              >
                {t("explorer.modal.onboarding.trash.close")}
              </Button>
            )}
          </Modal.Footer>
        </Modal>,
        document.getElementById("portal") as HTMLElement,
      )
    : null;
}
