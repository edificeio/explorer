export const refScrollTo = () => {
  const refToTop = document.querySelector('html');

  const scrollToTop = () => {
    if (refToTop) {
      refToTop.scrollIntoView();
    }
  };
  return scrollToTop;
};
