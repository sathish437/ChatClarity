import {
  Menu,
  Moon,
  PanelLeftClose,
  ShieldCheck,
  Sparkles,
  Sun,
} from "lucide-react";
import { useCallback, useEffect, useRef, useState } from "react";
import { ChatMessage } from "./components/ChatMessage";
import { EmptyState } from "./components/EmptyState";
import { MessageComposer } from "./components/MessageComposer";
import { Sidebar } from "./components/Sidebar";
import { askAssistant, errorMessage } from "./services/chatApi";
import type { ChatMessage as ChatMessageType } from "./types/chat";

type Theme = "light" | "dark";

function createMessage(
  role: ChatMessageType["role"],
  content: ChatMessageType["content"],
): ChatMessageType {
  return {
    id: globalThis.crypto?.randomUUID?.() ?? `${Date.now()}-${Math.random()}`,
    role,
    content,
  };
}

function initialTheme(): Theme {
  const saved = window.localStorage.getItem("clarity-theme");
  if (saved === "light" || saved === "dark") return saved;
  return window.matchMedia("(prefers-color-scheme: dark)").matches
    ? "dark"
    : "light";
}

export default function App() {
  const [messages, setMessages] = useState<ChatMessageType[]>([]);
  const [draft, setDraft] = useState("");
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [theme, setTheme] = useState<Theme>(initialTheme);
  const [sidebarOpen, setSidebarOpen] = useState(false);
  const [sidebarCollapsed, setSidebarCollapsed] = useState(false);
  const [sessionNotice, setSessionNotice] = useState(false);
  const bottomRef = useRef<HTMLDivElement>(null);
  const requestInProgress = useRef(false);

  useEffect(() => {
    document.documentElement.dataset.theme = theme;
    window.localStorage.setItem("clarity-theme", theme);
  }, [theme]);

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: "smooth", block: "end" });
  }, [messages, isLoading]);

  const sendMessage = useCallback(async (rawMessage: string) => {
    const message = rawMessage.trim();
    if (!message || requestInProgress.current) return;

    requestInProgress.current = true;
    setIsLoading(true);
    setError(null);
    setDraft("");
    setMessages((current) => [...current, createMessage("user", message)]);

    try {
      const answer = await askAssistant(message);
      setMessages((current) => [...current, createMessage("assistant", answer)]);
    } catch (requestError) {
      setError(errorMessage(requestError));
    } finally {
      requestInProgress.current = false;
      setIsLoading(false);
    }
  }, []);

  function startNewChat() {
    if (messages.length > 0) setSessionNotice(true);
    setMessages([]);
    setDraft("");
    setError(null);
    setSidebarOpen(false);
  }

  function toggleTheme() {
    setTheme((current) => (current === "light" ? "dark" : "light"));
  }

  return (
    <div className={`app-shell${sidebarCollapsed ? " sidebar-collapsed" : ""}`}>
      <Sidebar
        hasMessages={messages.length > 0}
        isBusy={isLoading}
        isOpen={sidebarOpen}
        onClose={() => setSidebarOpen(false)}
        onNewChat={startNewChat}
      />

      <main className="main-panel">
        <header className="topbar">
          <div className="topbar-left">
            <button
              aria-label="Open sidebar"
              className="icon-button mobile-menu"
              onClick={() => setSidebarOpen(true)}
            >
              <Menu size={19} />
            </button>
            <button
              aria-label={sidebarCollapsed ? "Show sidebar" : "Hide sidebar"}
              className="icon-button desktop-sidebar-toggle"
              onClick={() => setSidebarCollapsed((collapsed) => !collapsed)}
            >
              <PanelLeftClose size={18} />
            </button>
            <span className="topbar-title">AI conversation</span>
            <span className="topbar-divider">/</span>
            <span className="topbar-context">A little more clarity</span>
          </div>
          <div className="topbar-right">
            <span className="privacy-chip">
              <ShieldCheck size={14} />
              <span>Session memory</span>
            </span>
            <button
              aria-label={`Switch to ${theme === "light" ? "dark" : "light"} mode`}
              className="icon-button theme-toggle"
              onClick={toggleTheme}
              title={`Switch to ${theme === "light" ? "dark" : "light"} mode`}
            >
              {theme === "light" ? <Moon size={18} /> : <Sun size={18} />}
            </button>
          </div>
        </header>

        <div aria-live="polite" className="chat-scroll-area">
          {messages.length === 0 ? (
            <EmptyState onPrompt={sendMessage} />
          ) : (
            <div className="conversation">
              {messages.map((message) => (
                <ChatMessage
                  key={message.id}
                  message={message}
                  onFollowUp={sendMessage}
                />
              ))}
              {isLoading && (
                <div className="message-row assistant-message loading-row">
                  <div className="message-avatar assistant-avatar" aria-hidden="true">
                    <Sparkles size={16} />
                  </div>
                  <div className="message-body">
                    <div className="message-meta">
                      <span>Clarity</span>
                    </div>
                    <div className="typing-indicator" aria-label="Thinking">
                      <span />
                      <span />
                      <span />
                    </div>
                  </div>
                </div>
              )}
              <div ref={bottomRef} />
            </div>
          )}
        </div>

        <div className="composer-area">
          {sessionNotice && (
            <div className="session-notice" role="status">
              This clears the chat on screen only. The backend keeps memory for
              this browser session, so earlier context may still be used.
              <button
                aria-label="Dismiss session notice"
                className="notice-dismiss"
                onClick={() => setSessionNotice(false)}
              >
                ×
              </button>
            </div>
          )}
          {error && (
            <div className="error-notice" role="alert">
              <span>{error}</span>
              <button
                aria-label="Dismiss error"
                className="notice-dismiss"
                onClick={() => setError(null)}
              >
                ×
              </button>
            </div>
          )}
          <MessageComposer
            disabled={isLoading}
            isLoading={isLoading}
            onChange={setDraft}
            onSubmit={() => void sendMessage(draft)}
            value={draft}
          />
          <p className="disclaimer">
            <Sparkles size={12} />
            AI can make mistakes. Check important information.
          </p>
        </div>
      </main>
    </div>
  );
}
