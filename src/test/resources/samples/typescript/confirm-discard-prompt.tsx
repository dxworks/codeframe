import type React from 'react';
import { useCallback, useEffect, useRef, useState } from 'react';
// Generic sample: local no-op stubs for modal and navigation
const APP_BASE = '/app';
function showModal(_id: string, _props: any, onClosed?: () => void) {
  // Immediately invoke onClosed in this sample; return a disposer
  if (onClosed) onClosed();
  return () => {};
}
function navigate(_opts: { to: string }) {
  // no-op in sample
}
import { useTranslation } from 'react-i18next';

function stripAppBaseFromUrl(url: string) {
  return url.split(APP_BASE)?.[1];
}

interface ConfirmDiscardPromptProps {
  when: boolean;
  redirect?: string;
}

const ConfirmDiscardPrompt: React.FC<ConfirmDiscardPromptProps> = ({ when, redirect }) => {
  const { t } = useTranslation();
  const ref = useRef<boolean>(false);
  const [localTarget, setTarget] = useState<string | undefined>();
  const target = localTarget || redirect;
  const cancelUnload = useCallback(
    (e: BeforeUnloadEvent) => {
      const message = t('discardModalBody', 'You have unsaved changes. Discard them?');
      e.preventDefault();
      e.returnValue = message;
      return message;
    },
    [t],
  );

  const cancelNavigation = useCallback((evt: CustomEvent) => {
    if (!evt.detail.navigationIsCanceled && !ref.current) {
      ref.current = true;
      evt.detail.cancelNavigation();
      const dispose = showModal(
        'confirm-discard-modal',
        {
          onConfirm: () => {
            setTarget(evt.detail.newUrl);
            dispose();
          },
        },
        () => {
          ref.current = false;
        },
      );
    }
  }, []);

  useEffect(() => {
    if (when && typeof target === 'undefined') {
      window.addEventListener('single-spa:before-routing-event', cancelNavigation);
      window.addEventListener('beforeunload', cancelUnload);

      return () => {
        window.removeEventListener('beforeunload', cancelUnload);
        window.removeEventListener('single-spa:before-routing-event', cancelNavigation);
      };
    }
  }, [target, when, cancelUnload, cancelNavigation]);

  useEffect(() => {
    if (typeof target === 'string') {
      navigate({ to: `${APP_BASE}/${stripAppBaseFromUrl(target)}` });
    }
  }, [target]);

  return null;
};
export default ConfirmDiscardPrompt;
