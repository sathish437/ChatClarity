import { ArrowUpRight, BookOpen, Code2, Lightbulb } from "lucide-react";

interface EmptyStateProps {
  onPrompt: (prompt: string) => void;
}

const prompts = [
  {
    icon: BookOpen,
    title: "Explain a concept",
    description: "Make a tricky topic feel simple",
    prompt: "Explain how the internet works in simple terms",
  },
  {
    icon: Code2,
    title: "Help me code",
    description: "Debug, learn, or build something",
    prompt: "Show me a simple Java REST API example",
  },
  {
    icon: Lightbulb,
    title: "Think it through",
    description: "Explore ideas and find a clear path",
    prompt: "What are some practical ways to improve my focus?",
  },
];

export function EmptyState({ onPrompt }: EmptyStateProps) {
  return (
    <div className="empty-state">
      <div className="welcome-mark">
        <span className="welcome-mark-inner">
          <span />
          <span />
          <span />
        </span>
      </div>
      <p className="eyebrow">YOUR THOUGHTFUL AI COMPANION</p>
      <h1>
        What would you like
        <br />
        to <span>understand?</span>
      </h1>
      <p className="welcome-copy">
        Ask anything. Get a clear answer, a useful example, or a fresh
        perspective.
      </p>

      <div className="prompt-grid">
        {prompts.map(({ icon: Icon, title, description, prompt }) => (
          <button
            className="prompt-card"
            key={title}
            onClick={() => onPrompt(prompt)}
          >
            <span className="prompt-icon">
              <Icon size={17} />
            </span>
            <span className="prompt-text">
              <strong>{title}</strong>
              <span>{description}</span>
            </span>
            <ArrowUpRight className="prompt-arrow" size={16} />
          </button>
        ))}
      </div>
    </div>
  );
}
