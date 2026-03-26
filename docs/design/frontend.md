# Frontend Architecture - Detail Design

## 1. Overview

Single-page React application serving as the chat interface with A2UI dynamic component rendering.

- **Stack**: React 18, Vite 5, TypeScript
- **Dev port**: 3000 (proxied to gateway at 8080)
- **Production**: nginx serving static files, reverse proxy to gateway

---

## 2. Folder Structure

```
frontend/src/
├── main.tsx                    # Entry point
├── App.tsx                     # Root component, routing
├── components/
│   ├── chat/
│   │   ├── ChatPage.tsx        # Main chat page
│   │   ├── ChatSidebar.tsx     # Conversation list
│   │   ├── ChatMessageList.tsx # Scrollable message list
│   │   ├── ChatMessage.tsx     # Single message (text + A2UI)
│   │   └── ChatInput.tsx       # Message input bar
│   ├── a2ui/
│   │   ├── A2UIRenderer.tsx    # Dynamic component renderer
│   │   ├── QuizForm.tsx        # quiz-form component
│   │   ├── QuizResult.tsx      # quiz-result component
│   │   ├── VocabList.tsx       # vocab-list component
│   │   ├── AudioPlayer.tsx     # audio-player component
│   │   ├── VideoPlayer.tsx     # video-player component
│   │   ├── SubtitleOverlay.tsx # subtitle-overlay component
│   │   ├── ProgressChart.tsx   # progress-chart component
│   │   ├── FeedbackCard.tsx    # feedback-card component
│   │   └── FallbackComponent.tsx # Unknown type fallback
│   ├── auth/
│   │   ├── LoginPage.tsx
│   │   └── ProtectedRoute.tsx
│   └── common/
│       ├── Loading.tsx
│       └── ErrorBoundary.tsx
├── hooks/
│   ├── useAuth.ts              # Auth state + token management
│   ├── useChat.ts              # Chat operations
│   ├── useWebSocket.ts         # WebSocket connection
│   └── useA2UIEvent.ts         # A2UI event dispatch
├── services/
│   ├── api.ts                  # Axios instance with interceptors
│   ├── auth.ts                 # Auth API calls
│   ├── chat.ts                 # Chat API calls
│   └── websocket.ts            # WebSocket client
├── stores/
│   ├── authStore.ts            # Zustand: auth state
│   └── chatStore.ts            # Zustand: conversations, messages
├── types/
│   ├── api.ts                  # ApiResponse, PagedResponse
│   ├── auth.ts                 # User, LoginRequest, TokenResponse
│   ├── chat.ts                 # Conversation, Message
│   └── a2ui.ts                 # A2UIComponent, A2UIMessage, event types
└── utils/
    └── token.ts                # In-memory token storage
```

---

## 3. Component Hierarchy

```
App
├── LoginPage (public)
└── ProtectedRoute
    └── ChatPage
        ├── ChatSidebar
        │   └── ConversationItem[]
        ├── ChatMessageList
        │   └── ChatMessage[]
        │       ├── Text content
        │       └── A2UIRenderer
        │           ├── QuizForm
        │           ├── QuizResult
        │           ├── VocabList
        │           ├── AudioPlayer
        │           ├── VideoPlayer
        │           ├── SubtitleOverlay
        │           ├── ProgressChart
        │           ├── FeedbackCard
        │           └── FallbackComponent
        └── ChatInput
```

---

## 4. A2UI Renderer Architecture

### Component Registry Pattern
```typescript
// a2ui/A2UIRenderer.tsx
import { lazy, Suspense } from 'react';
import { A2UIComponent } from '@/types/a2ui';
import ErrorBoundary from '@/components/common/ErrorBoundary';
import FallbackComponent from './FallbackComponent';

const componentRegistry: Record<string, React.LazyExoticComponent<any>> = {
  'quiz-form': lazy(() => import('./QuizForm')),
  'quiz-result': lazy(() => import('./QuizResult')),
  'vocab-list': lazy(() => import('./VocabList')),
  'audio-player': lazy(() => import('./AudioPlayer')),
  'video-player': lazy(() => import('./VideoPlayer')),
  'subtitle-overlay': lazy(() => import('./SubtitleOverlay')),
  'progress-chart': lazy(() => import('./ProgressChart')),
  'feedback-card': lazy(() => import('./FeedbackCard')),
};

export function A2UIRenderer({ component }: { component: A2UIComponent }) {
  const Component = componentRegistry[component.type];

  if (!Component) {
    return <FallbackComponent type={component.type} />;
  }

  return (
    <ErrorBoundary fallback={<FallbackComponent type={component.type} error />}>
      <Suspense fallback={<Loading />}>
        <Component id={component.id} {...component.props} />
      </Suspense>
    </ErrorBoundary>
  );
}
```

---

## 5. State Management (Zustand)

### Auth Store
```typescript
interface AuthState {
  user: User | null;
  isAuthenticated: boolean;
  login: (email: string, password: string) => Promise<void>;
  logout: () => void;
  refreshToken: () => Promise<void>;
}
```

### Chat Store
```typescript
interface ChatState {
  conversations: Conversation[];
  activeConversationId: string | null;
  messages: Record<string, Message[]>;    // keyed by conversationId
  streamingContent: string;               // current streaming response

  loadConversations: () => Promise<void>;
  createConversation: () => Promise<Conversation>;
  sendMessage: (conversationId: string, content: string) => void;
  appendStreamToken: (token: string) => void;
  finalizeMessage: (message: Message) => void;
}
```

---

## 6. WebSocket Client

```typescript
// services/websocket.ts
class ChatWebSocket {
  private ws: WebSocket | null = null;
  private reconnectAttempts = 0;
  private maxReconnectAttempts = 5;

  connect(token: string) {
    this.ws = new WebSocket(`ws://localhost:8080/ws/chat?token=${token}`);

    this.ws.onmessage = (event) => {
      const data = JSON.parse(event.data);
      switch (data.type) {
        case 'STREAM_TOKEN': handleStreamToken(data); break;
        case 'STREAM_END': handleStreamEnd(data); break;
        case 'ERROR': handleError(data); break;
        case 'PONG': break; // heartbeat ack
      }
    };

    this.ws.onclose = () => this.reconnect(token);
  }

  send(message: ClientMessage) {
    this.ws?.send(JSON.stringify(message));
  }

  private reconnect(token: string) {
    if (this.reconnectAttempts < this.maxReconnectAttempts) {
      setTimeout(() => {
        this.reconnectAttempts++;
        this.connect(token);
      }, Math.min(1000 * 2 ** this.reconnectAttempts, 30000));
    }
  }
}
```

---

## 7. Authentication Flow

```
1. User enters email/password on LoginPage
2. POST /api/auth/login → receive accessToken + refreshToken
3. Store tokens in memory (NOT localStorage — XSS protection)
4. Attach accessToken to all API requests via Axios interceptor
5. On 401 response → try refreshToken → retry request
6. On refresh failure → redirect to login
7. Logout → clear tokens from memory → disconnect WebSocket
```

### Token Storage (in-memory)
```typescript
// utils/token.ts
let accessToken: string | null = null;
let refreshToken: string | null = null;

export const tokenStore = {
  getAccessToken: () => accessToken,
  setTokens: (access: string, refresh: string) => {
    accessToken = access;
    refreshToken = refresh;
  },
  clear: () => { accessToken = null; refreshToken = null; },
};
```

---

## 8. API Client

```typescript
// services/api.ts
import axios from 'axios';
import { tokenStore } from '@/utils/token';

const api = axios.create({ baseURL: '/api' });

api.interceptors.request.use((config) => {
  const token = tokenStore.getAccessToken();
  if (token) config.headers.Authorization = `Bearer ${token}`;
  return config;
});

api.interceptors.response.use(
  (response) => response,
  async (error) => {
    if (error.response?.status === 401) {
      // Try refresh, retry request, or redirect to login
    }
    return Promise.reject(error);
  }
);

export default api;
```

---

## 9. Routing

```typescript
// App.tsx
<BrowserRouter>
  <Routes>
    <Route path="/login" element={<LoginPage />} />
    <Route element={<ProtectedRoute />}>
      <Route path="/chat" element={<ChatPage />} />
      <Route path="/chat/:conversationId" element={<ChatPage />} />
    </Route>
    <Route path="*" element={<Navigate to="/chat" />} />
  </Routes>
</BrowserRouter>
```

---

## 10. Key TypeScript Types

```typescript
// types/chat.ts
interface Conversation {
  id: string;
  title: string;
  createdAt: string;
  updatedAt: string;
}

interface Message {
  id: string;
  conversationId: string;
  role: 'USER' | 'ASSISTANT' | 'SYSTEM';
  content: string;
  a2uiPayload?: A2UIMessage;
  createdAt: string;
}

// types/a2ui.ts
interface A2UIMessage {
  components: A2UIComponent[];
}

interface A2UIComponent {
  type: string;
  id: string;
  props: Record<string, unknown>;
  children?: A2UIComponent[];
}

interface A2UIEvent {
  componentId: string;
  eventType: string;
  payload: Record<string, unknown>;
}

// types/auth.ts
interface User {
  id: string;
  email: string;
  name: string;
  role: string;
  provider: string;
}
```
