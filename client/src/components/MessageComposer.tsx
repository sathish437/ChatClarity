import { ArrowUp, LoaderCircle } from "lucide-react";
import {
  useEffect,
  useRef,
  type ChangeEvent,
  type FormEvent,
  type KeyboardEvent,
} from "react";

interface MessageComposerProps {
  disabled: boolean;
  isLoading: boolean;
  value: string;
  onChange: (value: string) => void;
  onSubmit: () => void;
}

export function MessageComposer({
  disabled,
  isLoading,
  value,
  onChange,
  onSubmit,
}: MessageComposerProps) {
  const textareaRef = useRef<HTMLTextAreaElement>(null);

  useEffect(() => {
    const textarea = textareaRef.current;
    if (!textarea) return;
    textarea.style.height = "auto";
    textarea.style.height = `${Math.min(textarea.scrollHeight, 180)}px`;
  }, [value]);

  function handleKeyDown(event: KeyboardEvent<HTMLTextAreaElement>) {
    if (event.key === "Enter" && !event.shiftKey && !event.nativeEvent.isComposing) {
      event.preventDefault();
      onSubmit();
    }
  }

  function handleChange(event: ChangeEvent<HTMLTextAreaElement>) {
    onChange(event.target.value);
  }

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    onSubmit();
  }

  return (
    <form className="composer" onSubmit={handleSubmit}>
      <label className="sr-only" htmlFor="message-input">
        Message the assistant
      </label>
      <textarea
        autoComplete="off"
        id="message-input"
        maxLength={12000}
        onChange={handleChange}
        onKeyDown={handleKeyDown}
        placeholder="Ask anything..."
        ref={textareaRef}
        rows={1}
        value={value}
      />
      <div className="composer-controls">
        <span className="composer-hint">
          <kbd>Enter</kbd> to send <span className="hint-separator">·</span>{" "}
          <kbd>Shift + Enter</kbd> for a new line
        </span>
        <button
          aria-label={isLoading ? "Waiting for response" : "Send message"}
          className="send-button"
          disabled={disabled || !value.trim()}
          type="submit"
        >
          {isLoading ? (
            <LoaderCircle className="spin" size={18} />
          ) : (
            <ArrowUp size={19} strokeWidth={2.4} />
          )}
        </button>
      </div>
    </form>
  );
}
