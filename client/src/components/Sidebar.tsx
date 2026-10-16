import { Clock3, MessageSquareText, Plus, Sparkles, X } from "lucide-react";

interface SidebarProps {
  hasMessages: boolean;
  isBusy: boolean;
  isOpen: boolean;
  onClose: () => void;
  onNewChat: () => void;
}

export function Sidebar({
  hasMessages,
  isBusy,
  isOpen,
  onClose,
  onNewChat,
}: SidebarProps) {
  return (
    <>
      <button
        aria-label="Close sidebar"
        className={`sidebar-scrim${isOpen ? " is-visible" : ""}`}
        onClick={onClose}
        tabIndex={isOpen ? 0 : -1}
      />
      <aside className={`sidebar${isOpen ? " is-open" : ""}`}>
        <div className="sidebar-top">
          <a className="brand" href="/" aria-label="Clarity home">
            <span className="brand-mark">
              <Sparkles size={19} strokeWidth={2.1} />
            </span>
            <span>clarity</span>
          </a>
          <button
            className="icon-button sidebar-close"
            onClick={onClose}
            aria-label="Close sidebar"
          >
            <X size={19} />
          </button>
        </div>

        <button
          className="new-chat-button"
          disabled={isBusy}
          onClick={onNewChat}
        >
          <Plus size={18} strokeWidth={2.2} />
          <span>New chat</span>
        </button>

        <div className="sidebar-section-heading">
          <span>WORKSPACE</span>
        </div>
        <div className="history-list">
          {hasMessages ? (
            <div className="history-item is-active" title="Current conversation">
              <MessageSquareText size={16} />
              <span>Current conversation</span>
              <span className="active-indicator" />
            </div>
          ) : (
            <div className="history-empty">
              <Clock3 size={17} />
              <span>No chats yet</span>
            </div>
          )}
        </div>

        <div className="sidebar-bottom">
          <div className="history-note">
            <div className="history-note-icon">
              <MessageSquareText size={15} />
            </div>
            <p>
              Conversation history is not available from the current backend.
              This chat stays in this tab until you leave or start over.
            </p>
          </div>
        </div>
      </aside>
    </>
  );
}
