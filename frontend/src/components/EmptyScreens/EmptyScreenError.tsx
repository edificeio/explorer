import { EmptyScreen } from '@edifice.io/react';
import illuError from '@images/emptyscreen/illu-error.svg';
import { useTranslation } from 'react-i18next';

export default function EmptyScreenError(): JSX.Element {
  const { t } = useTranslation();

  return (
    <EmptyScreen
      imageSrc={illuError}
      imageAlt={t('explorer.emptyScreen.error.alt')}
      text={'explorer.emptyScreen.error.text'}
    />
  );
}
