import type {
  AnswerSection,
  AssistantAnswer,
  CodeExample,
} from "../types/chat";

function cleanText(value: unknown): string | undefined {
  if (typeof value !== "string") return undefined;
  const cleaned = value.trim();
  return cleaned || undefined;
}

function comparable(value: string): string {
  return value.replace(/\s+/g, " ").trim().toLocaleLowerCase();
}

function uniqueTexts(values: unknown[] | null | undefined): string[] {
  if (!Array.isArray(values)) return [];
  const seen = new Set<string>();
  const result: string[] = [];

  for (const value of values) {
    const text = cleanText(value);
    if (!text) continue;
    const key = comparable(text);
    if (seen.has(key)) continue;
    seen.add(key);
    result.push(text);
  }
  return result;
}

export function getKeyPoints(answer: AssistantAnswer): string[] {
  return uniqueTexts(answer.keyPoints).filter(
    (point) => comparable(point) !== comparable(answer.answer),
  );
}

export function getSections(answer: AssistantAnswer): AnswerSection[] {
  if (!Array.isArray(answer.sections)) return [];
  const seen = new Set<string>();
  const result: AnswerSection[] = [];

  for (const section of answer.sections) {
    if (!section || typeof section !== "object") continue;
    const heading = cleanText(section.heading);
    const body = cleanText(section.body);
    if (!heading && !body) continue;
    if (body && comparable(body) === comparable(answer.answer)) continue;
    const key = comparable(`${heading ?? ""}\n${body ?? ""}`);
    if (seen.has(key)) continue;
    seen.add(key);
    result.push({ heading, body });
  }
  return result;
}

export function getExamples(answer: AssistantAnswer): string[] {
  return uniqueTexts(answer.examples).filter(
    (example) => comparable(example) !== comparable(answer.answer),
  );
}

export function getCodeExamples(answer: AssistantAnswer): CodeExample[] {
  if (!Array.isArray(answer.codeExamples)) return [];
  const seen = new Set<string>();
  const result: CodeExample[] = [];

  for (const example of answer.codeExamples) {
    if (!example || typeof example !== "object") continue;
    const code = cleanText(example.code);
    if (!code) continue;
    const language = cleanText(example.language);
    const key = comparable(`${language ?? ""}\n${code}`);
    if (seen.has(key)) continue;
    seen.add(key);
    result.push({ language, code });
  }
  return result;
}

export function getConclusion(answer: AssistantAnswer): string | undefined {
  const conclusion = cleanText(answer.conclusion);
  if (!conclusion || comparable(conclusion) === comparable(answer.answer)) {
    return undefined;
  }
  return conclusion;
}

export function getFollowUpQuestions(answer: AssistantAnswer): string[] {
  return uniqueTexts(answer.followUpQuestions);
}
