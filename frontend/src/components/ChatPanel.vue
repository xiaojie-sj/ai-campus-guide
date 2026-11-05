<script setup lang="ts">
import { nextTick, ref, watch } from 'vue'
import type { MediaRef, SourceRef } from '../types'

export interface ChatMessage {
  id: string
  role: 'visitor' | 'guide'
  text: string
  media?: MediaRef[]
  sources?: SourceRef[]
  status?: string
  pending?: boolean
}

const props = defineProps<{
  messages: ChatMessage[]
  suggestions: string[]
  busy: boolean
}>()

const emit = defineEmits<{
  send: [value: string]
  close: []
}>()

const input = ref('')
const list = ref<HTMLElement>()
const inputElement = ref<HTMLTextAreaElement>()

function submit() {
  const value = input.value.trim()
  if (!value || props.busy) return
  emit('send', value)
  input.value = ''
}

function useSuggestion(value: string) {
  if (props.busy) return
  emit('send', value)
}

watch(
  () => props.messages.length,
  async () => {
    await nextTick()
    if (list.value) list.value.scrollTop = list.value.scrollHeight
  },
)

defineExpose({
  focusInput: () => inputElement.value?.focus(),
})
</script>

<template>
  <section class="chat-console" aria-label="校园空间问答">
    <header class="console-head">
      <div>
        <span class="section-kicker">GUIDE CHANNEL</span>
        <h2>校园知识库</h2>
      </div>
      <div class="console-tools">
        <span class="console-state" :class="{ live: !props.busy }">
          {{ props.busy ? '正在检索' : '知识库在线' }}
        </span>
        <button
          class="console-close"
          type="button"
          aria-label="收起校园知识库对话"
          @click="emit('close')"
        >
          ×
        </button>
      </div>
    </header>

    <div ref="list" class="message-list" aria-live="polite">
      <article
        v-for="message in messages"
        :key="message.id"
        class="message"
        :class="[message.role, { 'has-media': message.media?.length }]"
      >
        <div class="message-meta">
          <span>{{ message.role === 'visitor' ? '访客' : '新院导览员' }}</span>
          <span v-if="message.status">{{ message.status }}</span>
        </div>
        <p>{{ message.text }}</p>
        <div v-if="message.media?.length" class="message-media">
          <a
            v-for="media in message.media"
            :key="media.id"
            :href="media.sourceUrl || media.url"
            target="_blank"
            rel="noreferrer"
          >
            <figure>
              <img :src="media.url" :alt="media.altText || media.title" loading="lazy" />
              <figcaption>
                <strong>{{ media.title }}</strong>
                <small v-if="media.sourceTitle">{{ media.sourceTitle }}</small>
              </figcaption>
            </figure>
          </a>
        </div>
        <div v-if="message.sources?.length" class="source-stack">
          <details v-for="(source, index) in message.sources" :key="`${source.title}-${index}`">
            <summary>
              <span>S{{ index + 1 }}</span>
              {{ source.title }}
              <small v-if="source.effectiveYear">{{ source.effectiveYear }}</small>
            </summary>
            <p v-if="source.excerpt">{{ source.excerpt }}</p>
            <a v-if="source.url" :href="source.url" target="_blank" rel="noreferrer">打开来源</a>
          </details>
        </div>
      </article>
      <article v-if="props.busy" class="message guide pending">
        <div class="thinking-line">
          <span />
          <span />
          <span />
        </div>
        <p>正在核对已审核校园资料与空间信息。</p>
      </article>
    </div>

    <div class="suggestion-strip" aria-label="建议问题">
      <span class="suggestion-label">常见问题</span>
      <button
        v-for="suggestion in props.suggestions"
        :key="suggestion"
        type="button"
        :disabled="props.busy"
        @click="useSuggestion(suggestion)"
      >
        {{ suggestion }}
      </button>
    </div>

    <form class="input-row" @submit.prevent="submit">
      <label class="input-wrap">
        <span class="sr-only">输入校园导览问题</span>
        <textarea
          ref="inputElement"
          v-model="input"
          rows="1"
          maxlength="500"
          placeholder="输入问题，例如：学校大门在哪里？"
          @keydown.enter.exact.prevent="submit"
        />
      </label>
      <button class="send-button" type="submit" :disabled="props.busy || !input.trim()">
        发送
      </button>
    </form>
  </section>
</template>

<style scoped>
.chat-console {
  display: flex;
  min-height: 0;
  flex-direction: column;
  border: 1px solid rgb(226 213 178 / 22%);
  background: rgb(5 25 27 / 88%);
  box-shadow: 0 30px 80px rgb(0 0 0 / 32%);
  backdrop-filter: blur(22px);
}

.console-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 1rem;
  padding: 1rem 1.15rem 0.85rem;
  border-bottom: 1px solid rgb(226 213 178 / 14%);
}

.section-kicker {
  display: block;
  margin-bottom: 0.3rem;
  color: var(--amber);
  font: 600 0.62rem/1.2 var(--font-mono);
  letter-spacing: 0.16em;
}

h2 {
  margin: 0;
  font: 600 1.15rem/1.1 var(--font-display);
  color: var(--ivory);
}

.console-state {
  color: var(--muted);
  font: 0.66rem/1.4 var(--font-mono);
  text-align: right;
}

.console-tools {
  display: flex;
  align-items: center;
  gap: 0.55rem;
}

.console-close {
  display: grid;
  width: 1.75rem;
  height: 1.75rem;
  place-items: center;
  border: 1px solid rgb(226 213 178 / 18%);
  color: var(--muted);
  background: rgb(255 255 255 / 2%);
  font-size: 1rem;
  line-height: 1;
  cursor: pointer;
}

.console-close:hover {
  color: var(--ivory);
  border-color: rgb(226 213 178 / 45%);
}

.console-state.live::before {
  display: inline-block;
  width: 0.42rem;
  height: 0.42rem;
  margin-right: 0.35rem;
  border-radius: 50%;
  background: #74c69d;
  content: '';
}

.message-list {
  min-height: 0;
  flex: 1;
  overflow: auto;
  padding: 0.85rem 1.05rem;
  scroll-behavior: smooth;
}

.message {
  max-width: 92%;
  margin-bottom: 1rem;
}

.message.visitor {
  margin-left: auto;
}

.message.has-media {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 7.2rem;
  column-gap: 0.65rem;
}

.message.has-media .message-meta,
.message.has-media .source-stack {
  grid-column: 1 / -1;
}

.message.has-media > p {
  grid-column: 1;
}

.message.has-media .message-media {
  grid-column: 2;
  grid-row: 2;
  align-self: start;
  margin-top: 0;
}

.message-meta {
  display: flex;
  justify-content: space-between;
  gap: 1rem;
  margin-bottom: 0.3rem;
  color: var(--muted);
  font: 0.62rem/1.2 var(--font-mono);
}

.message.visitor .message-meta {
  flex-direction: row-reverse;
}

.message p {
  margin: 0;
  color: #e8e3d3;
  font-size: 0.83rem;
  line-height: 1.75;
  white-space: pre-wrap;
}

.message.visitor p {
  padding: 0.72rem 0.85rem;
  border: 1px solid rgb(226 213 178 / 16%);
  background: rgb(217 181 101 / 11%);
}

.message-media {
  display: grid;
  grid-template-columns: minmax(0, 1fr);
  justify-content: start;
  gap: 0.45rem;
  margin-top: 0.65rem;
}

.message-media a {
  color: inherit;
  text-decoration: none;
}

.message-media figure {
  margin: 0;
  overflow: hidden;
  border: 1px solid rgb(226 213 178 / 18%);
  background: rgb(255 255 255 / 3%);
}

.message-media img {
  display: block;
  width: 100%;
  aspect-ratio: 16 / 10;
  max-height: 5.5rem;
  object-fit: cover;
  transition: transform 280ms ease;
}

.message-media a:hover img {
  transform: scale(1.035);
}

.message-media figcaption {
  display: grid;
  gap: 0.15rem;
  padding: 0.45rem 0.55rem 0.5rem;
}

.message-media strong {
  color: var(--ivory);
  font-size: 0.68rem;
  font-weight: 500;
}

.message-media small {
  color: var(--muted);
  font: 0.52rem/1.3 var(--font-mono);
}

.source-stack {
  margin-top: 0.65rem;
}

.source-stack details {
  margin-top: 0.3rem;
  color: var(--muted);
  font-size: 0.7rem;
}

.source-stack summary {
  cursor: pointer;
}

.source-stack summary span {
  margin-right: 0.35rem;
  color: var(--amber);
  font-family: var(--font-mono);
}

.source-stack summary small {
  margin-left: 0.4rem;
}

.source-stack a {
  color: #d8c58e;
}

.thinking-line {
  display: flex;
  gap: 0.25rem;
  margin-bottom: 0.55rem;
}

.thinking-line span {
  width: 0.32rem;
  height: 0.32rem;
  border-radius: 50%;
  background: var(--amber);
  animation: pulse 1.2s infinite ease-in-out;
}

.thinking-line span:nth-child(2) {
  animation-delay: 0.15s;
}

.thinking-line span:nth-child(3) {
  animation-delay: 0.3s;
}

.suggestion-strip {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0.3rem 0.65rem;
  padding: 0.6rem 1rem;
  border-top: 1px solid rgb(226 213 178 / 10%);
}

.suggestion-label {
  grid-column: 1 / -1;
  color: var(--amber);
  font: 0.57rem/1.2 var(--font-mono);
  letter-spacing: 0.08em;
}

.suggestion-strip button {
  border: 0;
  border-bottom: 1px solid rgb(226 213 178 / 28%);
  padding: 0.22rem 0;
  color: var(--muted);
  background: transparent;
  font-size: 0.68rem;
  line-height: 1.5;
  text-align: left;
}

.suggestion-strip button:hover {
  color: var(--ivory);
  border-color: var(--amber);
}

.input-row {
  display: grid;
  grid-template-columns: 1fr auto;
  gap: 0.55rem;
  align-items: end;
  padding: 0.75rem 1rem 1rem;
}

.input-wrap {
  border-bottom: 1px solid rgb(226 213 178 / 32%);
  background: rgb(255 255 255 / 3%);
}

textarea {
  display: block;
  width: 100%;
  min-height: 2.65rem;
  max-height: 7rem;
  resize: vertical;
  border: 0;
  outline: 0;
  padding: 0.7rem 0.75rem;
  color: var(--ivory);
  background: transparent;
  font: 0.8rem/1.55 var(--font-body);
}

textarea::placeholder {
  color: rgb(222 217 199 / 48%);
}

.send-button {
  border: 1px solid rgb(226 213 178 / 25%);
  color: var(--ivory);
  background: transparent;
  cursor: pointer;
}

.send-button {
  min-width: 4rem;
  height: 2.75rem;
  padding: 0 0.85rem;
  color: #092326;
  background: var(--amber);
  font-weight: 700;
}

button:disabled {
  cursor: not-allowed;
  opacity: 0.42;
}

@keyframes pulse {
  0%,
  80%,
  100% {
    transform: translateY(0);
    opacity: 0.3;
  }
  40% {
    transform: translateY(-0.25rem);
    opacity: 1;
  }
}
</style>
