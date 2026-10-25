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
    <span class="sigil__sheet sigil__sheet--back" />
    <span class="sigil__sheet sigil__sheet--mid" />
    <span class="sigil__sheet sigil__sheet--front">
      <span class="sigil__punch" />
      <Icon :name="icon" :size="24" :stroke-width="1.4" />
      <span class="sigil__slip" />
    </span>
  </div>
</template>

<style scoped>
.sigil {
  --sigil-accent: var(--accent);
  position: relative;
  width: 4.6rem;
  height: 5.2rem;
  flex: none;
  perspective: 520px;
  transform-style: preserve-3d;
}
.sigil--signal {
  --sigil-accent: var(--signal-strong);
}
.sigil--bone {
  --sigil-accent: var(--text);
}
.sigil__sheet {
  position: absolute;
  inset: 4% 8% 8% 4%;
  display: block;
  border: 1px solid var(--line-strong);
  border-radius: 1px;
  background: var(--surface-2);
  clip-path: polygon(0 0, calc(100% - 22%) 0, 100% 22%, 100% 100%, 0 100%);
  transition: transform 420ms var(--ease);
}
.sigil__sheet--back {
  transform: translate3d(14%, 8%, -26px) rotate(-4deg);
  opacity: 0.3;
}
.sigil__sheet--mid {
  transform: translate3d(7%, 4%, -12px) rotate(-2deg);
  background: color-mix(in srgb, var(--sigil-accent) 18%, var(--surface-2));
  opacity: 0.55;
}
.sigil__sheet--front {
  display: grid;
  place-items: center;
  border-color: color-mix(in srgb, var(--sigil-accent) 55%, var(--line-strong));
  background:
    linear-gradient(165deg, rgb(255 255 255 / 0.06), transparent 45%),
    var(--surface);
  color: var(--sigil-accent);
  transform: rotate(-2deg);
  box-shadow: 0 10px 18px -16px rgb(0 0 0 / 0.9);
}
.sigil__punch {
  position: absolute;
  top: 0.5rem;
  left: 0.5rem;
  width: 0.28rem;
  height: 0.28rem;
  border-radius: 50%;
  background: color-mix(in srgb, var(--ink) 80%, transparent);
  box-shadow: inset 0 1px 1px rgb(0 0 0 / 0.5);
}
.sigil__slip {
  position: absolute;
  right: 0.55rem;
  bottom: 0.55rem;
  width: 0.9rem;
  height: 2px;
  background: var(--signal);
  opacity: 0.85;
}
.page-head:hover .sigil__sheet--back {
  transform: translate3d(18%, 11%, -30px) rotate(-7deg);
}
.page-head:hover .sigil__sheet--mid {
  transform: translate3d(9%, 5%, -14px) rotate(-4deg);
}
.page-head:hover .sigil__sheet--front {
  transform: translate3d(-3%, -3%, 8px) rotate(1deg);
}
@media (max-width: 760px) {
  .sigil {
    width: 3.3rem;
    height: 3.8rem;
  }
}
@media (prefers-reduced-motion: reduce) {
  .sigil__sheet {
    transition: none;
  }
}
</style>
