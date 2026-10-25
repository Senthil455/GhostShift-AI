<script setup lang="ts">
import Icon from './Icon.vue'

withDefaults(
  defineProps<{
    icon: string
    order?: string
    tone?: 'cobalt' | 'signal' | 'bone'
  }>(),
  { order: '00', tone: 'cobalt' },
)
</script>

<template>
  <div class="sigil" :class="`sigil--${tone}`" aria-hidden="true">
    <span class="sigil__plate sigil__plate--back" />
    <span class="sigil__plate sigil__plate--mid" />
    <span class="sigil__plate sigil__plate--front">
      <span class="sigil__index">{{ order }}</span>
      <Icon :name="icon" :size="30" :stroke-width="1.35" />
      <span class="sigil__tick" />
    </span>
  </div>
</template>

<style scoped>
.sigil {
  --sigil-accent: var(--accent);
  position: relative;
  width: 9.2rem;
  height: 9.2rem;
  flex: none;
  perspective: 720px;
  transform-style: preserve-3d;
}
.sigil--signal {
  --sigil-accent: var(--signal);
}
.sigil--bone {
  --sigil-accent: var(--text);
}
.sigil__plate {
  position: absolute;
  inset: 12%;
  display: block;
  border: 1px solid var(--line-strong);
  background: var(--surface-2);
  clip-path: polygon(0 0, calc(100% - 18%) 0, 100% 18%, 100% 100%, 0 100%);
  transform-style: preserve-3d;
  transition: transform 420ms var(--ease);
}
.sigil__plate--back {
  transform: translate3d(16%, -12%, -40px) rotateY(-18deg) rotateX(6deg);
  opacity: 0.35;
}
.sigil__plate--mid {
  transform: translate3d(8%, -6%, -20px) rotateY(-18deg) rotateX(6deg);
  background: color-mix(in srgb, var(--sigil-accent) 16%, var(--surface-2));
  opacity: 0.6;
}
.sigil__plate--front {
  display: flex;
  align-items: center;
  justify-content: center;
  inset: 4% 16% 16% 4%;
  border-color: var(--sigil-accent);
  background: var(--surface);
  color: var(--sigil-accent);
  transform: rotateY(-18deg) rotateX(6deg);
  box-shadow: 6px 6px 0 color-mix(in srgb, var(--sigil-accent) 18%, transparent);
}
.sigil__index {
  position: absolute;
  top: 0.7rem;
  left: 0.75rem;
  font-size: 0.68rem;
  font-weight: 700;
  letter-spacing: 0.02em;
  color: var(--text-faint);
}
.sigil__tick {
  position: absolute;
  right: 0.75rem;
  bottom: 0.7rem;
  width: 0.42rem;
  height: 0.42rem;
  background: var(--signal);
}
.page-head:hover .sigil__plate--back {
  transform: translate3d(22%, -17%, -48px) rotateY(-22deg) rotateX(8deg);
}
.page-head:hover .sigil__plate--mid {
  transform: translate3d(12%, -9%, -24px) rotateY(-22deg) rotateX(8deg);
}
.page-head:hover .sigil__plate--front {
  transform: translate3d(-2%, 2%, 8px) rotateY(-12deg) rotateX(4deg);
}
@media (max-width: 760px) {
  .sigil {
    width: 5.4rem;
    height: 5.4rem;
  }
  .sigil__index {
    top: 0.35rem;
    left: 0.4rem;
    font-size: 0.55rem;
  }
  .sigil__tick {
    right: 0.4rem;
    bottom: 0.35rem;
    width: 0.28rem;
    height: 0.28rem;
  }
}
@media (prefers-reduced-motion: reduce) {
  .sigil__plate {
    transition: none;
  }
}
</style>
