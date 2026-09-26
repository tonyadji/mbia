import { fireEvent, render, screen } from '@testing-library/react';
import { Modal } from './Modal';

describe('Modal keyboard support (design-guidelines.md §9)', () => {
  it('keeps Tab and Shift+Tab inside the open dialog', () => {
    render(
      <>
        <button type="button">Behind</button>
        <Modal message="Remove this link?" onClose={() => undefined}>
          <button type="button">Cancel</button>
          <button type="button">Confirm</button>
        </Modal>
      </>,
    );
    const cancel = screen.getByRole('button', { name: 'Cancel' });
    const confirm = screen.getByRole('button', { name: 'Confirm' });

    confirm.focus();
    fireEvent.keyDown(document, { key: 'Tab' });
    expect(cancel).toHaveFocus();

    fireEvent.keyDown(document, { key: 'Tab', shiftKey: true });
    expect(confirm).toHaveFocus();

    screen.getByRole('button', { name: 'Behind' }).focus();
    fireEvent.keyDown(document, { key: 'Tab' });
    expect(cancel).toHaveFocus();
  });

  it('closes on Escape and gives the focus back', () => {
    const onClose = vi.fn();
    render(<button type="button">Open</button>);
    const opener = screen.getByRole('button', { name: 'Open' });
    opener.focus();
    const { unmount } = render(
      <Modal message="Remove this link?" onClose={onClose}>
        <button type="button">Cancel</button>
      </Modal>,
    );

    fireEvent.keyDown(document, { key: 'Escape' });
    expect(onClose).toHaveBeenCalledOnce();
    unmount();
    expect(opener).toHaveFocus();
  });
});
