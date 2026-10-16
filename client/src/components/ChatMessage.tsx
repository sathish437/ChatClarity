import {
  Check,
  Clipboard,
  Code2,
  CornerDownRight,
  Sparkles,
  UserRound,
} from "lucide-react";
import { useState } from "react";
import type { AssistantAnswer, ChatMessage as ChatMessageType } from "../types/chat";
import {
  getCodeExamples,
  getConclusion,
  getExamples,
  getFollowUpQuestions,
  getKeyPoints,
  getSections,
} from "../utils/answerContent";

interface ChatMessageProps {
  message: ChatMessageType;
  onFollowUp: (question: string) => void;
}

function toCopyText(answer: AssistantAnswer): string {
  const parts = [answer.answer];
  const keyPoints = getKeyPoints(answer);
  if (keyPoints.length) parts.push(`Key points:\n${keyPoints.map((point) => `• ${point}`).join("\n")}`);
  for (const section of getSections(answer)) {
    parts.push([section.heading, section.body].filter(Boolean).join("\n"));
  }
  const examples = getExamples(answer);
  if (examples.length) parts.push(`Examples:\n${examples.join("\n")}`);
  for (const example of getCodeExamples(answer)) {
    parts.push(`Code${example.language ? ` (${example.language})` : ""}:\n${example.code}`);
  }
  const conclusion = getConclusion(answer);
  if (conclusion) parts.push(`Conclusion:\n${conclusion}`);
  return parts.filter(Boolean).join("\n\n");
}

export function ChatMessage({ message, onFollowUp }: ChatMessageProps) {
  const [copied, setCopied] = useState(false);
  const [copyFailed, setCopyFailed] = useState(false);

  if (message.role === "user") {
    return (
      <article className="message-row user-message">
        <div className="message-avatar user-avatar" aria-hidden="true">
          <UserRound size={17} />
        </div>
        <div className="message-body">
          <div className="message-meta">
            <span>You</span>
          </div>
          <p className="user-bubble">{message.content as string}</p>
        </div>
      </article>
    );
  }

  const answer = message.content as AssistantAnswer;
  const sections = getSections(answer);
  const keyPoints = getKeyPoints(answer);
  const examples = getExamples(answer);
  const codeExamples = getCodeExamples(answer);
  const conclusion = getConclusion(answer);
  const followUps = getFollowUpQuestions(answer);

  async function copyAnswer() {
    try {
      await navigator.clipboard.writeText(toCopyText(answer));
      setCopied(true);
      setCopyFailed(false);
      window.setTimeout(() => setCopied(false), 1800);
    } catch {
      setCopyFailed(true);
    }
  }

  return (
    <article className="message-row assistant-message">
      <div className="message-avatar assistant-avatar" aria-hidden="true">
        <Sparkles size={16} />
      </div>
      <div className="message-body">
        <div className="message-meta">
          <span>Clarity</span>
          {answer.language && (
            <span className="language-label">{answer.language}</span>
          )}
        </div>
        <div className="answer-content">
          <p className="answer-lead">{answer.answer}</p>

          {keyPoints.length > 0 && (
            <ul className="key-point-list">
              {keyPoints.map((point) => (
                <li key={point}>{point}</li>
              ))}
            </ul>
          )}

          {sections.length > 0 && (
            <div className="answer-sections">
              {sections.map((section, index) => (
                <section className="answer-section" key={`${section.heading ?? ""}-${index}`}>
                  {section.heading && <h3>{section.heading}</h3>}
                  {section.body && <p>{section.body}</p>}
                </section>
              ))}
            </div>
          )}

          {examples.length > 0 && (
            <section className="answer-block">
              <h3>Examples</h3>
              <ul className="example-list">
                {examples.map((example) => (
                  <li key={example}>{example}</li>
                ))}
              </ul>
            </section>
          )}

          {codeExamples.length > 0 && (
            <div className="code-example-list">
              {codeExamples.map((example, index) => (
                <section className="code-example" key={`${example.language ?? "code"}-${index}`}>
                  <div className="code-example-heading">
                    <span>
                      <Code2 size={14} />
                      {example.language || "Code"}
                    </span>
                  </div>
                  <pre>
                    <code>{example.code}</code>
                  </pre>
                </section>
              ))}
            </div>
          )}

          {conclusion && (
            <section className="conclusion">
              <h3>In short</h3>
              <p>{conclusion}</p>
            </section>
          )}
        </div>

        <div className="message-actions">
          <button
            aria-label={copied ? "Response copied" : "Copy response"}
            className="message-action"
            onClick={copyAnswer}
            title="Copy response"
            type="button"
          >
            {copied ? <Check size={15} /> : <Clipboard size={15} />}
            <span>{copied ? "Copied" : "Copy"}</span>
          </button>
          {copyFailed && (
            <span className="copy-error" role="status">
              Couldn’t copy. Please select the response to copy it.
            </span>
          )}
        </div>

        {followUps.length > 0 && (
          <div className="follow-up-area">
            <div className="follow-up-label">
              <CornerDownRight size={14} />
              <span>KEEP EXPLORING</span>
            </div>
            <div className="follow-up-list">
              {followUps.map((question) => (
                <button
                  className="follow-up-button"
                  key={question}
                  onClick={() => onFollowUp(question)}
                  type="button"
                >
                  {question}
                  <span aria-hidden="true">↗</span>
                </button>
              ))}
            </div>
          </div>
        )}
      </div>
    </article>
  );
}
