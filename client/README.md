# Clarity chat client

React, TypeScript, and Vite frontend for the Spring Boot chat service.

## Development

Start the Spring Boot backend on `http://localhost:8080`, then run:

```sh
npm install
npm run dev
```

Vite proxies `/api` to the backend by default, allowing the browser to send and
retain the backend's HTTP session cookie. To use another backend origin, set
`VITE_API_BASE_URL` in a local `.env` file. Cross-origin deployments must also
configure the backend to allow the frontend origin and credentialed requests.

## Conversation history limitation

The backend currently exposes `POST /api/ai/ask` only. It uses an HTTP session
cookie for model memory and stores exchanges server-side, but it has no API to
list or retrieve saved conversations and no API to start a separate/reset
conversation. The sidebar therefore shows only the current in-tab conversation.
“New chat” clears the visible transcript; it cannot clear or replace the
backend's session memory. The frontend does not claim that browser state is
saved to Neon.
