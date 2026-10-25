<script setup lang="ts">
withDefaults(defineProps<{ size?: 'sm' | 'md' | 'lg' }>(), { size: 'md' })
</script>

<template>
  <span class="shift-mark" :class="`shift-mark--${size}`" aria-hidden="true">
    <i class="shift-mark__sheet shift-mark__sheet--back" />
    <i class="shift-mark__sheet shift-mark__sheet--mid" />
    <i class="shift-mark__sheet shift-mark__sheet--front">
      <span class="shift-mark__punch" />
      <span class="shift-mark__line" />
      <span class="shift-mark__line shift-mark__line--short" />
    </i>
  </span>
</template>

<style scoped>
.shift-mark {
  --mark-size: 2.3rem;
  position: relative;
  display: inline-block;
  width: var(--mark-size);
  height: var(--mark-size);
  flex: none;
  perspective: 220px;
  transform-style: preserve-3d;
}
.shift-mark--sm {
  --mark-size: 1.65rem;
}
.shift-mark--lg {
  --mark-size: 4.2rem;
}
.shift-mark__sheet {
  position: absolute;
  inset: 6%;
  display: block;
  border: 1px solid var(--line-strong);
  border-radius: 1px;
  background: var(--surface-2);
  clip-path: polygon(0 0, calc(100% - 26%) 0, 100% 26%, 100% 100%, 0 100%);
  transform-origin: 40% 90%;
  transition:
    transform 340ms var(--ease),
    background 200ms ease,
    border-color 200ms ease;
}
.shift-mark__sheet--back {
  transform: translate3d(-14%, 10%, -14px) rotate(-9deg);
  opacity: 0.35;
}
.shift-mark__sheet--mid {
  transform: translate3d(-6%, 5%, -7px) rotate(-4deg);
  background: color-mix(in srgb, var(--accent) 26%, var(--surface-2));
  opacity: 0.7;
}
.shift-mark__sheet--front {
  inset: 3% 14% 14% 3%;
  border-color: color-mix(in srgb, var(--paper-ink) 22%, var(--paper));
  background:
    linear-gradient(160deg, rgb(255 255 255 / 0.5), transparent 42%),
    var(--paper);
  transform: translate3d(3%, -3%, 0) rotate(2deg);
  box-shadow: 0 2px 6px -2px rgb(0 0 0 / 0.65);
}
.shift-mark__punch {
  position: absolute;
  top: 18%;
  left: 16%;
  width: 22%;
  aspect-ratio: 1;
  border-radius: 50%;
  background: var(--surface);
  box-shadow: inset 0 1px 1px rgb(0 0 0 / 0.45);
}
.shift-mark__line {
  position: absolute;
  left: 16%;
  bottom: 32%;
  width: 62%;
  height: 7%;
  background: color-mix(in srgb, var(--paper-ink) 55%, transparent);
}
.shift-mark__line--short {
  bottom: 18%;
  width: 38%;
  background: var(--signal);
}
.brand:hover .shift-mark__sheet--back {
  transform: translate3d(-18%, 14%, -18px) rotate(-13deg);
}
.brand:hover .shift-mark__sheet--mid {
  transform: translate3d(-9%, 7%, -9px) rotate(-6deg);
}
.brand:hover .shift-mark__sheet--front {
  transform: translate3d(5%, -5%, 6px) rotate(0deg);
}
@media (prefers-reduced-motion: reduce) {
  .shift-mark__sheet {
    transition: none;
  }
}
</style>
