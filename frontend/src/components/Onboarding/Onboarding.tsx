import { useState } from "react";

import { Modal, Button, Image, usePaths } from "@edifice-ui/react";
import { createPortal } from "react-dom";
import { useTranslation } from "react-i18next";
import { Pagination } from "swiper/modules";
import { Swiper, SwiperSlide } from "swiper/react";

import "swiper/css";
import "swiper/css/pagination";
import { useOnboardingModal } from "./useOnboardingModal";

interface ModalItemsProps {
  /**
   * /onboarding/*.svg
   */
  src: string;
  /**
   * Image Companion text
   */
  alt: string;
  /**
   * Text below image
   */
  text: string;
}

interface ModalOptionsProps {
  /**
   * Modal title
   */
  title: string;
  /**
   * Cancel button text
   */
  cancelText: string;
  /**
   * Next button text
   */
  nextText: string;
  /**
   * Close button text
   */
  closeText: string;
}
interface OnboardingProps {
  isOpen: boolean;
  value: string;
  items: ModalItemsProps[];
  modalOptions: ModalOptionsProps;
  onSuccess: () => void;
  onCancel: () => void;
}

export default function Onboarding({
  isOpen,
  value,
  items,
  modalOptions,
  onSuccess,
  onCancel,
}: OnboardingProps): JSX.Element | null {
  const [imagePath] = usePaths();
  const [swiperInstance, setSwiperInstance] = useState<any>();
  const [swiperProgress, setSwiperprogress] = useState<number>(0);

  const { handleSavePreference } = useOnboardingModal(value, { onSuccess });

  const { t } = useTranslation();

  const { title, cancelText, closeText, nextText } = modalOptions;

  return createPortal(
    <Modal
      id="onboarding-modal"
      size="md"
      isOpen={isOpen}
      focusId="nextButtonId"
      onModalClose={onCancel}
    >
      <Modal.Header onModalClose={onCancel}>
        {t(title || "explorer.modal.onboarding.trash.title")}
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
          {items.map((item) => {
            return (
              <SwiperSlide>
                <Image
                  width="270"
                  height="140"
                  className="mx-auto my-12"
                  loading="lazy"
                  src={`${imagePath}/${item.src}`}
                  alt={t(item.alt)}
                />
                <p>{t(item.text)}</p>
              </SwiperSlide>
            );
          })}
        </Swiper>
      </Modal.Body>
      <Modal.Footer>
        <Button
          type="button"
          color="tertiary"
          variant="ghost"
          onClick={onCancel}
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
            {t(cancelText || "explorer.modal.onboarding.trash.prev")}
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
            {t(nextText || "explorer.modal.onboarding.trash.next")}
          </Button>
        )}
        {swiperProgress === 1 && (
          <Button
            type="button"
            color="primary"
            variant="filled"
            onClick={handleSavePreference}
          >
            {t(closeText || "explorer.modal.onboarding.trash.close")}
          </Button>
        )}
      </Modal.Footer>
    </Modal>,
    document.getElementById("portal") as HTMLElement,
  );
}