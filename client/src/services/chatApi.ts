import type {
  AssistantAnswer,
  ChatTurnRequest,
  EditTurnRequest,
} from "../types/chat";

const apiBaseUrl = (import.meta.env.VITE_API_BASE_URL ?? "").replace(/\/+$/, "");

export class ChatApiError extends Error {
  constructor(
    public readonly kind:
      | "bad-request"
      | "unauthorized"
      | "rate-limit"
      | "server"
      | "unavailable"
      | "invalid-response"
      | "network",
  ) {
    super(kind);
    this.name = "ChatApiError";
  }
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null && !Array.isArray(value);
}

function optionalString(value: unknown): string | undefined {
  return typeof value === "string" && value.trim() ? value.trim() : undefined;
}

function optionalArray<T>(
  value: unknown,
  normalize: (item: unknown) => T | undefined,
): T[] | undefined {
  if (!Array.isArray(value)) return undefined;
  return value.map(normalize).filter((item): item is T => item !== undefined);
}

function normalizeSection(value: unknown) {
  if (!isRecord(value)) return undefined;
  return {
    heading: optionalString(value.heading),
    body: optionalString(value.body),
  };
}

function normalizeCodeExample(value: unknown) {
  if (!isRecord(value)) return undefined;
  return {
    language: optionalString(value.language),
    code: optionalString(value.code),
  };
}

function normalizeAnswer(value: unknown): AssistantAnswer | null {
  if (!isRecord(value)) return null;
  const answer = optionalString(value.answer);
  if (!answer) return null;

  return {
    answer,
    responseType: optionalString(value.responseType),
    language: optionalString(value.language),
    keyPoints: optionalArray(value.keyPoints, optionalString),
    sections: optionalArray(value.sections, normalizeSection),
    examples: optionalArray(value.examples, optionalString),
    codeExamples: optionalArray(value.codeExamples, normalizeCodeExample),
    conclusion: optionalString(value.conclusion),
    followUpQuestions: optionalArray(value.followUpQuestions, optionalString),
  };
}

function errorKind(status: number): ChatApiError["kind"] {
  if (status === 400) return "bad-request";
  if (status === 401 || status === 403) return "unauthorized";
  if (status === 429) return "rate-limit";
  if (status === 503 || status === 504) return "unavailable";
  if (status >= 500) return "server";
  return "server";
}

export function errorMessage(error: unknown): string {
  if (!(error instanceof ChatApiError)) {
    return "Something went wrong. Please try again.";
  }

  switch (error.kind) {
    case "bad-request":
      return "That message could not be accepted. Check it and try again.";
    case "unauthorized":
      return "This chat is not authorized right now. Please refresh the page or try again later.";
    case "rate-limit":
      return "The AI service quota or rate limit may be exhausted. Please try again later.";
    case "server":
      return "The chat service had a problem. Please try again in a little while.";
    case "unavailable":
      return "The AI service is temporarily unavailable. Please try again later.";
    case "invalid-response":
      return "The chat service returned an unreadable response. Please try again.";
    case "network":
      return "Could not connect to the chat service. Check that the backend is running and try again.";
  }
}

export async function askAssistant(message: string): Promise<AssistantAnswer> {
  const request: ChatTurnRequest = { message };
  return postTurn("/api/ai/ask", request);
}

export async function editAssistantMessage(
  message: string,
  keepExchanges: number,
): Promise<AssistantAnswer> {
  const request: EditTurnRequest = { message, keepExchanges };
  return postTurn("/api/ai/edit", request);
}

async function postTurn(
  path: string,
  request: ChatTurnRequest | EditTurnRequest,
): Promise<AssistantAnswer> {
  let response: Response;

  try {
    response = await fetch(`${apiBaseUrl}${path}`, {
      method: "POST",
      credentials: "include",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(request),
    });
  } catch {
    throw new ChatApiError("network");
  }

  if (!response.ok) {
    throw new ChatApiError(errorKind(response.status));
  }

  let payload: unknown;
  try {
    payload = await response.json();
  } catch {
    throw new ChatApiError("invalid-response");
  }

  const answer = normalizeAnswer(payload);
  if (!answer) throw new ChatApiError("invalid-response");
  return answer;
}
