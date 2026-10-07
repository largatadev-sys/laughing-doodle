// Copy text on the web, including where the async Clipboard API doesn't exist.
//
// `navigator.clipboard` is only exposed in a secure context (HTTPS or localhost), so the app
// served over a plain-http LAN address — the local gate, opened from a phone — has no API at
// all. There the older selection + `execCommand('copy')` route still works in every current
// browser, iOS Safari included, as long as it runs synchronously inside the tap; so it goes
// first when the context isn't secure, and is only a second try after a refused API call.

export async function copyText(text: string): Promise<boolean> {
  if (typeof window === 'undefined' || typeof document === 'undefined') return false;
  if (!window.isSecureContext || !navigator.clipboard) return copyBySelection(text);
  try {
    await navigator.clipboard.writeText(text);
    return true;
  } catch {
    // A refused permission or a lost focus; the gesture may be spent by now, so this can fail.
    return copyBySelection(text);
  }
}

// Select the text in an off-screen element and ask the browser to copy the selection. A span
// with a Range (rather than a <textarea>) avoids focusing an input, which on iOS would flash
// the keyboard and can scroll the page. The user's own selection is restored afterwards.
function copyBySelection(text: string): boolean {
  const selection = window.getSelection();
  if (!selection) return false;
  const saved = selection.rangeCount > 0 ? selection.getRangeAt(0) : null;

  const holder = document.createElement('span');
  holder.textContent = text;
  holder.style.whiteSpace = 'pre';
  holder.style.userSelect = 'text';
  holder.style.webkitUserSelect = 'text';
  holder.style.position = 'fixed';
  holder.style.top = '0';
  holder.style.left = '0';
  holder.style.opacity = '0';
  holder.style.pointerEvents = 'none';
  document.body.appendChild(holder);

  // The copy event fires from execCommand before the browser serialises the selection; writing
  // the payload here makes what lands on the clipboard exactly `text` (newlines and Markdown
  // indentation included), and only marks success if the event actually reached us.
  let delivered = false;
  const onCopy = (event: ClipboardEvent) => {
    if (!event.clipboardData) return;
    event.clipboardData.setData('text/plain', text);
    event.preventDefault();
    delivered = true;
  };
  document.addEventListener('copy', onCopy, true);

  let copied = false;
  try {
    const range = document.createRange();
    range.selectNodeContents(holder);
    selection.removeAllRanges();
    selection.addRange(range);
    copied = document.execCommand('copy');
  } catch {
    copied = false;
  } finally {
    document.removeEventListener('copy', onCopy, true);
    selection.removeAllRanges();
    if (saved) selection.addRange(saved);
    holder.remove();
  }
  // Either the browser copied our selection, or it handed us the event and we wrote the text.
  return copied || delivered;
}
