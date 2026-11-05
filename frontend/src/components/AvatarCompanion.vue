<script setup lang="ts">
import { computed, onBeforeUnmount, ref } from 'vue'
import avatarPortrait from '../assets/avatar-guide.png'

const props = defineProps<{
  active?: boolean
}>()

const emit = defineEmits<{
  activate: []
}>()

const gesture = ref('idle')
let gestureTimer: number | undefined

const stateLabel = computed(() => {
  if (props.active) return '空间问答在线'
  if (gesture.value !== 'idle') return '正在回应'
  return '点击开始问询'
})

function playGesture(name: string, durationMs = 1400) {
  window.clearTimeout(gestureTimer)
  gesture.value = name
  gestureTimer = window.setTimeout(() => {
    gesture.value = 'idle'
  }, durationMs)
}

onBeforeUnmount(() => {
  window.clearTimeout(gestureTimer)
})

defineExpose({
  playGesture,
})
</script>

<template>
  <aside
    class="avatar-companion"
    :class="[gesture, { active: props.active }]"
    aria-label="校园空间智能助手入口"
  >
    <div class="avatar-status">
      <span>CAMPUS ASSISTANT</span>
      <strong>{{ stateLabel }}</strong>
    </div>

    <div class="avatar-stage">
      <button
        class="avatar-trigger"
        type="button"
        :aria-label="props.active ? '继续和校园知识库导览员对话' : '打开校园知识库导览员对话'"
        :aria-expanded="props.active"
        @click="emit('activate')"
      >
        <div class="avatar-orbit" aria-hidden="true" />
        <div class="avatar-halo" aria-hidden="true" />
        <div class="avatar-photo-frame">
          <img
            class="avatar-art avatar-photo"
            :src="avatarPortrait"
            alt="蓝发鲸鱼主题二维校园导览员"
            draggable="false"
          />
        </div>
        <span class="avatar-tap-ring" aria-hidden="true" />
      </button>
      <svg v-if="false" class="avatar-art" viewBox="0 0 240 340" aria-hidden="true">
        <defs>
          <linearGradient id="avatar-coat" x1="0" y1="0" x2="1" y2="1">
            <stop offset="0" stop-color="#e4c47d" />
            <stop offset="0.48" stop-color="#c99d4e" />
            <stop offset="1" stop-color="#967035" />
          </linearGradient>
          <linearGradient id="avatar-shirt" x1="0" y1="0" x2="0" y2="1">
            <stop offset="0" stop-color="#244e4c" />
            <stop offset="1" stop-color="#102f31" />
          </linearGradient>
          <linearGradient id="avatar-skin" x1="0" y1="0" x2="0.8" y2="1">
            <stop offset="0" stop-color="#f1d2b4" />
            <stop offset="1" stop-color="#d8a883" />
          </linearGradient>
          <linearGradient id="avatar-hair" x1="0" y1="0" x2="1" y2="1">
            <stop offset="0" stop-color="#173c3d" />
            <stop offset="1" stop-color="#071f24" />
          </linearGradient>
          <radialGradient id="avatar-cheek" cx="50%" cy="50%" r="50%">
            <stop offset="0" stop-color="#df8e7b" stop-opacity=".34" />
            <stop offset="1" stop-color="#df8e7b" stop-opacity="0" />
          </radialGradient>
          <filter id="avatar-soft-shadow" x="-40%" y="-30%" width="180%" height="190%">
            <feDropShadow dx="0" dy="7" stdDeviation="7" flood-color="#001416" flood-opacity=".45" />
          </filter>
        </defs>

        <ellipse cx="120" cy="315" rx="64" ry="14" fill="#48c6bc" opacity=".12" />
        <ellipse cx="120" cy="315" rx="42" ry="8" fill="#f0c56b" opacity=".16" />

        <g class="avatar-legs">
          <path d="M95 264 L92 307 Q93 315 103 315 H110 L112 268Z" fill="#102d31" />
          <path d="M145 264 L148 307 Q147 315 137 315 H130 L128 268Z" fill="#102d31" />
          <path d="M89 308 Q90 320 108 320 H117 Q119 312 111 308Z" fill="#071c20" />
          <path d="M151 308 Q150 320 132 320 H123 Q121 312 129 308Z" fill="#071c20" />
        </g>

        <g class="wave-arm">
          <path d="M66 169 Q47 190 44 228 Q43 239 53 242 Q63 245 68 233 L80 199Z" fill="url(#avatar-coat)" />
          <path d="M46 231 Q35 235 35 245 Q34 258 48 260 Q61 260 64 247 L63 235Z" fill="url(#avatar-skin)" />
          <path d="M54 211 Q64 205 70 212" fill="none" stroke="#f1d59c" stroke-width="3" stroke-linecap="round" opacity=".65" />
        </g>

        <g class="point-arm">
          <path d="M174 169 Q193 190 196 228 Q197 239 187 242 Q177 245 172 233 L160 199Z" fill="url(#avatar-coat)" />
          <path d="M194 231 Q205 235 205 245 Q206 258 192 260 Q179 260 176 247 L177 235Z" fill="url(#avatar-skin)" />
          <path d="M186 211 Q176 205 170 212" fill="none" stroke="#f1d59c" stroke-width="3" stroke-linecap="round" opacity=".65" />
        </g>

        <g filter="url(#avatar-soft-shadow)">
          <path d="M83 166 Q120 149 157 166 L174 262 Q172 281 153 286 H88 Q69 281 67 262Z" fill="url(#avatar-coat)" />
          <path d="M104 158 L120 190 L136 158 Q127 151 120 151 Q113 151 104 158Z" fill="url(#avatar-shirt)" />
          <path d="M120 190 L112 286 H128Z" fill="#f0dfbd" opacity=".92" />
          <path d="M86 180 Q75 206 73 244 Q72 252 80 254 Q87 255 90 245 L98 199Z" fill="url(#avatar-coat)" />
          <path d="M77 243 Q67 247 69 258 Q71 269 83 268 Q93 267 94 255 L92 245Z" fill="url(#avatar-skin)" />
          <path d="M154 180 Q165 206 167 244 Q168 252 160 254 Q153 255 150 245 L142 199Z" fill="url(#avatar-coat)" />
          <path d="M163 243 Q173 247 171 258 Q169 269 157 268 Q147 267 146 255 L148 245Z" fill="url(#avatar-skin)" />
          <path d="M91 174 L102 257" stroke="#f2d99c" stroke-width="4" stroke-linecap="round" opacity=".72" />
          <path d="M149 174 L138 257" stroke="#7e592a" stroke-width="2" stroke-linecap="round" opacity=".44" />
          <rect x="145" y="200" width="20" height="14" rx="3" fill="#173b3b" stroke="#f0c56b" stroke-width="2" />
          <path d="M150 204 H160 M150 209 H157" stroke="#f2d99c" stroke-width="1.5" stroke-linecap="round" />
        </g>

        <path d="M104 145 H136 V171 Q120 181 104 171Z" fill="url(#avatar-skin)" />
        <path d="M105 153 Q120 164 135 153" fill="none" stroke="#ba8569" stroke-width="1.5" opacity=".45" />

        <g class="avatar-head" filter="url(#avatar-soft-shadow)">
          <ellipse cx="120" cy="104" rx="45" ry="53" fill="url(#avatar-skin)" />
          <ellipse cx="76" cy="106" rx="7" ry="12" fill="#d8a883" />
          <ellipse cx="164" cy="106" rx="7" ry="12" fill="#d8a883" />
          <path d="M75 99 Q70 50 112 41 Q165 35 168 91 Q157 69 140 67 Q122 38 99 70 Q86 78 75 99Z" fill="url(#avatar-hair)" />
          <path d="M80 86 Q91 104 85 138 Q72 126 73 107Z" fill="url(#avatar-hair)" />
          <path d="M160 86 Q151 108 157 138 Q169 124 167 106Z" fill="url(#avatar-hair)" />
          <path d="M96 55 Q114 39 139 51 Q128 59 99 72Z" fill="#356066" opacity=".52" />

          <path d="M93 101 Q102 96 111 100" fill="none" stroke="#523934" stroke-width="3.2" stroke-linecap="round" />
          <path d="M129 100 Q138 96 147 101" fill="none" stroke="#523934" stroke-width="3.2" stroke-linecap="round" />
          <ellipse cx="103" cy="111" rx="7.5" ry="5.2" fill="#fff9ee" />
          <ellipse cx="137" cy="111" rx="7.5" ry="5.2" fill="#fff9ee" />
          <ellipse cx="104" cy="111" rx="3.2" ry="4.1" fill="#143e40" />
          <ellipse cx="136" cy="111" rx="3.2" ry="4.1" fill="#143e40" />
          <circle cx="105" cy="109.5" r="1.2" fill="#fff" />
          <circle cx="137" cy="109.5" r="1.2" fill="#fff" />
          <path d="M120 112 Q117 125 121 128" fill="none" stroke="#b77e66" stroke-width="1.8" stroke-linecap="round" />
          <ellipse cx="94" cy="127" rx="13" ry="9" fill="url(#avatar-cheek)" />
          <ellipse cx="146" cy="127" rx="13" ry="9" fill="url(#avatar-cheek)" />
          <ellipse
            class="avatar-mouth"
            cx="120"
            cy="139"
            rx="10"
            ry="4.2"
            fill="#803f46"
          />
          <path d="M113 137 Q120 134 127 137" fill="none" stroke="#f2b2a8" stroke-width="1.5" stroke-linecap="round" opacity=".65" />
        </g>

        <g class="avatar-earpiece">
          <path d="M162 99 Q174 101 173 116 Q172 128 165 131" fill="none" stroke="#e1b963" stroke-width="3" stroke-linecap="round" />
          <circle cx="174" cy="116" r="3.5" fill="#8be0d1" />
          <path d="M173 120 Q183 133 172 145" fill="none" stroke="#e1b963" stroke-width="1.6" opacity=".68" />
        </g>
      </svg>
      <div class="avatar-scan" aria-hidden="true" />
    </div>

    <button class="avatar-prompt" type="button" @click="emit('activate')">
      <span class="prompt-signal" aria-hidden="true" />
      <span>{{ props.active ? '继续问校园' : '点击问校园' }}</span>
      <small>{{ props.active ? 'KNOWLEDGE CHAT' : 'CAMPUS Q&A' }}</small>
    </button>

    <div class="avatar-name">
      <strong>校园空间助手</strong>
      <small>HBEUTC · KNOWLEDGE GUIDE</small>
    </div>
  </aside>
</template>

<style scoped>
.avatar-companion {
  position: absolute;
  z-index: 22;
  right: 1.1rem;
  bottom: 0.7rem;
  width: 13rem;
  height: 18.5rem;
  pointer-events: none;
  filter: drop-shadow(0 18px 28px rgb(0 8 10 / 36%));
}

.avatar-companion.active .avatar-halo {
  border-color: rgb(126 187 224 / 38%);
  box-shadow: 0 0 2.8rem rgb(101 166 211 / 18%);
}

.avatar-stage {
  position: absolute;
  inset: 1.4rem 0 2.3rem;
  display: grid;
  place-items: end center;
}

.avatar-trigger {
  position: absolute;
  inset: 0;
  display: grid;
  width: 100%;
  place-items: end center;
  border: 0;
  padding: 0;
  color: inherit;
  background: transparent;
  cursor: pointer;
  pointer-events: auto;
}

.avatar-trigger:focus-visible {
  outline: 2px solid #d9b465;
  outline-offset: -0.2rem;
}

.avatar-orbit,
.avatar-halo {
  position: absolute;
  left: 50%;
  border-radius: 50%;
  transform: translateX(-50%);
}

.avatar-orbit {
  bottom: 0.5rem;
  width: 7.9rem;
  height: 2rem;
  border: 1px solid rgb(236 193 105 / 54%);
  box-shadow:
    0 0 0 0.45rem rgb(94 205 188 / 4%),
    0 0 2.2rem rgb(89 205 189 / 15%);
}

.avatar-halo {
  bottom: 4rem;
  width: 8.8rem;
  height: 8.8rem;
  border: 1px solid rgb(103 220 203 / 16%);
  background:
    radial-gradient(circle, rgb(77 194 181 / 12%) 0 32%, transparent 66%),
    conic-gradient(from 12deg, transparent, rgb(240 196 104 / 12%), transparent 42%);
  animation: halo-turn 16s linear infinite;
}

.avatar-art {
  position: relative;
  z-index: 2;
  width: 100%;
  height: 100%;
  overflow: visible;
  animation: avatar-float 4.8s ease-in-out infinite;
  transform-origin: center bottom;
}

.avatar-photo-frame {
  position: relative;
  z-index: 2;
  width: 100%;
  height: 100%;
  overflow: visible;
  filter: drop-shadow(0 14px 18px rgb(0 12 15 / 34%));
  transition: filter 180ms ease;
}

.avatar-photo {
  width: 100%;
  height: 100%;
  object-fit: contain;
  transform-origin: center bottom;
}

.avatar-trigger:hover .avatar-photo-frame {
  filter:
    drop-shadow(0 18px 22px rgb(0 12 15 / 40%))
    drop-shadow(0 0 0.8rem rgb(119 192 232 / 24%));
}

.avatar-tap-ring {
  position: absolute;
  z-index: 1;
  right: 50%;
  bottom: 3.55rem;
  width: 7.2rem;
  height: 7.2rem;
  border: 1px solid rgb(129 196 231 / 0%);
  border-radius: 50%;
  transform: translateX(50%);
  transition:
    border-color 180ms ease,
    transform 180ms ease;
}

.avatar-trigger:hover .avatar-tap-ring,
.avatar-companion:not(.active) .avatar-tap-ring {
  border-color: rgb(129 196 231 / 24%);
  animation: tap-breathe 2.8s ease-in-out infinite;
}

.wave .avatar-art {
  animation:
    avatar-float 4.8s ease-in-out infinite,
    photo-wave 700ms ease-in-out infinite;
}

.wave .avatar-photo {
  animation:
    avatar-float 4.8s ease-in-out infinite,
    photo-wave 700ms ease-in-out infinite;
}

.point .avatar-art {
  animation:
    avatar-float 4.8s ease-in-out infinite,
    photo-point 900ms ease-in-out infinite alternate;
}

.point .avatar-photo {
  animation:
    avatar-float 4.8s ease-in-out infinite,
    photo-point 900ms ease-in-out infinite alternate;
}

.wave-arm,
.point-arm {
  transform-box: fill-box;
  transition: transform 220ms ease;
}

.wave-arm {
  transform-origin: 92% 6%;
}

.point-arm {
  transform-origin: 8% 6%;
}

.wave .wave-arm {
  animation: wave 640ms ease-in-out infinite;
}

.point .point-arm {
  transform: rotate(-16deg) translate(3px, -7px);
}

.avatar-scan {
  position: absolute;
  z-index: 3;
  bottom: 1.5rem;
  width: 7.8rem;
  height: 1px;
  background: linear-gradient(90deg, transparent, rgb(116 231 214 / 62%), transparent);
  box-shadow: 0 0 12px rgb(115 220 205 / 50%);
  animation: scan 3.8s ease-in-out infinite;
}

.avatar-status {
  position: absolute;
  z-index: 4;
  top: 0;
  left: 50%;
  display: flex;
  min-width: 9.7rem;
  align-items: center;
  justify-content: space-between;
  gap: 0.55rem;
  border: 1px solid rgb(225 190 111 / 43%);
  padding: 0.35rem 0.5rem;
  color: #e9d9b4;
  background: rgb(5 27 29 / 88%);
  box-shadow: 0 8px 28px rgb(0 12 14 / 35%);
  transform: translateX(-50%);
  backdrop-filter: blur(10px);
}

.avatar-prompt {
  position: absolute;
  z-index: 6;
  top: 9.3rem;
  left: -4.7rem;
  display: grid;
  min-width: 5.3rem;
  justify-items: start;
  gap: 0.12rem;
  border: 1px solid rgb(137 193 221 / 30%);
  padding: 0.52rem 0.65rem 0.48rem;
  color: #edf7f8;
  background:
    linear-gradient(135deg, rgb(19 54 73 / 94%), rgb(5 29 36 / 92%));
  box-shadow:
    0 12px 32px rgb(0 10 14 / 34%),
    inset 0 1px rgb(255 255 255 / 7%);
  text-align: left;
  cursor: pointer;
  pointer-events: auto;
  backdrop-filter: blur(12px);
  animation: prompt-arrive 580ms 220ms both cubic-bezier(0.22, 1, 0.36, 1);
}

.avatar-prompt::after {
  position: absolute;
  top: 50%;
  right: -0.34rem;
  width: 0.62rem;
  height: 0.62rem;
  border-top: 1px solid rgb(137 193 221 / 30%);
  border-right: 1px solid rgb(137 193 221 / 30%);
  background: #0a2731;
  content: '';
  transform: translateY(-50%) rotate(45deg);
}

.avatar-prompt > span:not(.prompt-signal) {
  padding-left: 0.72rem;
  font: 600 0.7rem/1.25 var(--font-display);
  letter-spacing: 0.04em;
}

.avatar-prompt small {
  padding-left: 0.72rem;
  color: rgb(166 207 225 / 68%);
  font: 0.43rem/1.2 var(--font-mono);
  letter-spacing: 0.08em;
}

.avatar-prompt:hover {
  border-color: rgb(152 211 237 / 58%);
  transform: translateY(-1px);
}

.prompt-signal {
  position: absolute;
  top: 0.69rem;
  left: 0.56rem;
  width: 0.36rem;
  height: 0.36rem;
  border-radius: 50%;
  background: #86d5cf;
  box-shadow: 0 0 0 0.22rem rgb(134 213 207 / 12%);
}

.avatar-status span {
  color: var(--amber);
  font: 600 0.51rem/1 var(--font-mono);
  letter-spacing: 0.13em;
}

.avatar-status strong {
  color: #dbece2;
  font-size: 0.58rem;
  font-weight: 500;
}

.avatar-status strong::before {
  display: inline-block;
  width: 0.34rem;
  height: 0.34rem;
  margin-right: 0.3rem;
  border-radius: 50%;
  background: #74c69d;
  content: '';
}

.avatar-name {
  position: absolute;
  right: 0;
  bottom: 0;
  left: 0;
  display: grid;
  justify-items: center;
  gap: 0.15rem;
  padding: 0.48rem;
  border-top: 1px solid rgb(229 192 113 / 28%);
  background: linear-gradient(90deg, transparent, rgb(5 27 29 / 74%), transparent);
}

.avatar-name strong {
  color: #f0e5cd;
  font: 500 0.75rem/1.2 var(--font-display);
  letter-spacing: 0.08em;
}

.avatar-name small {
  color: rgb(212 202 178 / 48%);
  font: 0.42rem/1 var(--font-mono);
  letter-spacing: 0.1em;
}

@keyframes avatar-float {
  0%,
  100% {
    transform: translateY(0);
  }
  50% {
    transform: translateY(-0.25rem);
  }
}

@keyframes halo-turn {
  to {
    transform: translateX(-50%) rotate(360deg);
  }
}

@keyframes scan {
  0%,
  100% {
    opacity: 0;
    transform: translateY(-6.2rem);
  }
  18%,
  82% {
    opacity: 0.62;
  }
  50% {
    transform: translateY(-0.3rem);
  }
}

@keyframes wave {
  0%,
  100% {
    transform: rotate(3deg);
  }
  50% {
    transform: rotate(-28deg) translateY(-8px);
  }
}

@keyframes photo-wave {
  0%,
  100% {
    transform: rotate(-1deg) translateY(0);
  }
  50% {
    transform: rotate(2.2deg) translateY(-0.18rem);
  }
}

@keyframes photo-point {
  to {
    transform: translate(0.18rem, -0.1rem) rotate(1deg);
  }
}

@keyframes status-pulse {
  50% {
    opacity: 0.5;
    transform: scale(0.72);
  }
}

@keyframes tap-breathe {
  0%,
  100% {
    opacity: 0.5;
    transform: translateX(50%) scale(0.96);
  }
  50% {
    opacity: 1;
    transform: translateX(50%) scale(1.04);
  }
}

@keyframes prompt-arrive {
  from {
    opacity: 0;
    transform: translateX(0.7rem);
  }
}

@media (max-width: 1080px) {
  .avatar-companion {
    right: 0.8rem;
    width: 10rem;
    height: 14.5rem;
  }

  .avatar-status {
    min-width: 8.8rem;
  }

  .avatar-prompt {
    left: -4.4rem;
  }
}

@media (max-width: 760px) {
  .avatar-companion {
    right: 0.55rem;
    bottom: 0.7rem;
    width: 7.3rem;
    height: 10.2rem;
  }

  .avatar-companion.active {
    bottom: calc(min(26rem, 48vh) + 0.7rem);
  }

  .avatar-stage {
    inset: 1.1rem 0 1.5rem;
  }

  .avatar-status {
    min-width: 6.8rem;
    gap: 0.25rem;
    padding: 0.28rem 0.35rem;
  }

  .avatar-status span {
    font-size: 0.4rem;
  }

  .avatar-status strong {
    font-size: 0.48rem;
  }

  .avatar-name {
    padding: 0.3rem;
  }

  .avatar-name strong {
    font-size: 0.6rem;
  }

  .avatar-name small {
    display: none;
  }

  .avatar-prompt {
    top: 4.7rem;
    left: -3.9rem;
    min-width: 4.5rem;
    padding: 0.42rem 0.5rem;
  }

  .avatar-prompt > span:not(.prompt-signal) {
    padding-left: 0.6rem;
    font-size: 0.58rem;
  }

  .avatar-prompt small {
    display: none;
  }

  .prompt-signal {
    top: 0.6rem;
    left: 0.45rem;
  }
}

@media (prefers-reduced-motion: reduce) {
  .avatar-art,
  .avatar-halo,
  .avatar-scan,
  .wave .wave-arm,
.wave .avatar-art,
.point .avatar-art,
.wave .avatar-photo,
.point .avatar-photo,
  .avatar-tap-ring,
  .avatar-prompt,
  .avatar-status strong::before {
    animation: none;
  }
}
</style>
