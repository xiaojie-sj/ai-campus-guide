<script setup lang="ts">
import { nextTick, ref, watch } from 'vue'
import type { MediaRef, SourceRef } from '../types'
import AppIcon from './AppIcon.vue'

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
  <section class="chat-console" aria-label="校园空间问答" :aria-busy="props.busy">
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
          <AppIcon name="close" :size="17" />
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
        <span>发送</span>
        <AppIcon name="arrow" :size="17" />
      </button>
    </form>
  </section>
</template>

<style scoped>
.chat-console {
  display: flex;
  min-height: 0;
  flex-direction: column;
  border: 1px solid rgb(120 170 215 / 20%);
  background: rgb(5 20 54 / 90%);
  box-shadow: 0 30px 80px rgb(0 0 0 / 32%);
  backdrop-filter: blur(22px);
}

.console-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 1rem;
  padding: 1rem 1.15rem 0.85rem;
  border-bottom: 1px solid rgb(120 170 215 / 14%);
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
  border: 1px solid rgb(120 170 215 / 16%);
  color: var(--muted);
  background: rgb(255 255 255 / 2%);
  font-size: 1rem;
  line-height: 1;
  cursor: pointer;
}

.console-close:hover {
  color: var(--ivory);
  border-color: rgb(0 220 246 / 42%);
}

.console-state.live::before {
  display: inline-block;
  width: 0.42rem;
  height: 0.42rem;
  margin-right: 0.35rem;
  border-radius: 50%;
  background: #2dd4bf;
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
  color: #dceeff;
  font-size: 0.83rem;
  line-height: 1.75;
  white-space: pre-wrap;
}

.message.visitor p {
  padding: 0.72rem 0.85rem;
  border: 1px solid rgb(120 170 215 / 15%);
  background: rgb(0 220 246 / 10%);
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
  border: 1px solid rgb(120 170 215 / 16%);
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
  color: #7fe7f5;
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
  border-top: 1px solid rgb(120 170 215 / 10%);
}

.suggestion-label {
  grid-column: 1 / -1;
  color: var(--amber);
  font: 0.57rem/1.2 var(--font-mono);
  letter-spacing: 0.08em;
}

.suggestion-strip button {
  border: 0;
  border-bottom: 1px solid rgb(120 170 215 / 26%);
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
  border-bottom: 1px solid rgb(120 170 215 / 30%);
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
  color: rgb(160 195 225 / 52%);
}

.send-button {
  border: 1px solid rgb(120 170 215 / 24%);
  color: var(--ivory);
  background: transparent;
  cursor: pointer;
}

.send-button {
  min-width: 4rem;
  height: 2.75rem;
  padding: 0 0.85rem;
  color: #04101e;
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

/* Cohesive conversation surface */
.chat-console {
  border-color: rgb(138 169 214 / 18%);
  border-radius: 1.25rem;
  background: linear-gradient(160deg, rgb(8 27 54 / 94%), rgb(2 11 25 / 96%));
  box-shadow: 0 30px 90px rgb(0 0 0 / 38%), inset 0 1px 0 rgb(255 255 255 / 3%);
  backdrop-filter: blur(24px);
}

.console-head {
  align-items: center;
  padding: 1.05rem 1.2rem 0.95rem;
  border-bottom-color: rgb(138 169 214 / 13%);
  background: linear-gradient(180deg, rgb(53 215 255 / 4%), transparent);
}

.section-kicker {
  color: #68dff2;
  font-size: 0.62rem;
  letter-spacing: 0.13em;
}

h2 {
  font-size: 1.18rem;
}

.console-state {
  color: #8ca2c0;
  font-size: 0.7rem;
}

.console-close {
  width: 2.75rem;
  height: 2.75rem;
  border-color: rgb(138 169 214 / 16%);
  border-radius: 0.62rem;
  color: #91a6c4;
}

.console-close:hover {
  color: #e8f8ff;
  border-color: rgb(53 215 255 / 34%);
  background: rgb(53 215 255 / 7%);
}

.message-list {
  padding: 1rem 1.15rem;
}

.message {
  max-width: 88%;
  margin-bottom: 1.15rem;
}

.message-meta {
  margin-bottom: 0.38rem;
  color: #8298b8;
  font-size: 0.68rem;
}

.message p {
  color: #e3eeff;
  font-size: 0.89rem;
  line-height: 1.78;
}

.message.visitor p {
  padding: 0.78rem 0.95rem;
  border-color: rgb(53 215 255 / 18%);
  border-radius: 0.95rem 0.95rem 0.25rem 0.95rem;
  background: linear-gradient(135deg, rgb(53 215 255 / 12%), rgb(92 124 250 / 8%));
}

.message.guide p {
  padding: 0.12rem 0;
}

.message-media figure {
  border-color: rgb(138 169 214 / 17%);
  border-radius: 0.8rem;
}

.source-stack summary {
  min-height: 2.3rem;
  border-radius: 0.6rem;
}

.suggestion-strip {
  gap: 0.45rem;
  padding: 0.7rem 1.05rem;
  border-top-color: rgb(138 169 214 / 11%);
}

.suggestion-label {
  color: #7891b1;
  font-size: 0.66rem;
}

.suggestion-strip button {
  min-height: 2.75rem;
  border-color: rgb(138 169 214 / 16%);
  border-radius: 999px;
  padding: 0.4rem 0.7rem;
  color: #b2c3dc;
  background: rgb(138 169 214 / 5%);
  font-size: 0.7rem;
}

.suggestion-strip button:hover:not(:disabled) {
  border-color: rgb(53 215 255 / 30%);
  color: #e4fbff;
  background: rgb(53 215 255 / 8%);
}

.input-row {
  align-items: end;
  gap: 0.55rem;
  padding: 0.85rem 1.05rem 1rem;
  border-top-color: rgb(138 169 214 / 12%);
}

.input-wrap {
  border-color: rgb(138 169 214 / 17%);
  border-radius: 0.8rem;
  background: rgb(2 10 24 / 70%);
}

.input-wrap:focus-within {
  border-color: rgb(53 215 255 / 50%);
  box-shadow: 0 0 0 3px rgb(53 215 255 / 7%);
}

textarea {
  min-height: 2.8rem;
  padding: 0.72rem 0.8rem;
  color: #e8f0ff;
  font-size: 0.88rem;
}

.send-button {
  display: inline-flex;
  min-width: 5.3rem;
  min-height: 2.8rem;
  align-items: center;
  justify-content: center;
  gap: 0.35rem;
  border-radius: 0.8rem;
  font-size: 0.8rem;
}

.thinking-line span {
  background: #56e4f7;
}

</style>
