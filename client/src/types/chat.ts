export interface AnswerSection {
  heading?: string | null;
  body?: string | null;
}

export interface CodeExample {
  language?: string | null;
  code?: string | null;
}

export interface AssistantAnswer {
  responseType?: string | null;
  answer: string;
  language?: string | null;
  keyPoints?: Array<string | null> | null;
  sections?: Array<AnswerSection | null> | null;
  examples?: Array<string | null> | null;
  codeExamples?: Array<CodeExample | null> | null;
  conclusion?: string | null;
  followUpQuestions?: Array<string | null> | null;
}

export interface ChatMessage {
  id: string;
  role: "user" | "assistant";
  content: string | AssistantAnswer;
}

export interface ChatTurnRequest {
  message: string;
}

export interface EditTurnRequest {
  message: string;
  keepExchanges: number;
}
