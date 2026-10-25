<script setup lang="ts">
withDefaults(defineProps<{ size?: 'sm' | 'md' | 'lg' }>(), { size: 'md' })
</script>

<template>
  <span class="shift-mark" :class="`shift-mark--${size}`" aria-hidden="true">
    <i class="shift-mark__plate shift-mark__plate--back" />
    <i class="shift-mark__plate shift-mark__plate--mid" />
    <i class="shift-mark__plate shift-mark__plate--front">
      <b class="shift-mark__signal" />
      <span class="shift-mark__cut" />
    </i>
  </span>
</template>

<style scoped>
.shift-mark {
  --mark-size: 2.35rem;
  position: relative;
  display: inline-block;
  width: var(--mark-size);
  height: var(--mark-size);
  flex: none;
  perspective: 180px;
}
.shift-mark--sm {
  --mark-size: 1.7rem;
}
.shift-mark--lg {
  --mark-size: 4.4rem;
}
.shift-mark__plate {
  position: absolute;
  inset: 14% 8% 8% 14%;
  display: block;
  border: 1px solid var(--line-strong);
  background: var(--surface-2);
  clip-path: polygon(0 0, calc(100% - 22%) 0, 100% 22%, 100% 100%, 0 100%);
  transform-origin: 50% 50%;
  transition:
    transform 320ms var(--ease),
    background 180ms ease,
    border-color 180ms ease;
}
.shift-mark__plate--back {
  transform: translate3d(-16%, 12%, -12px) rotate(-8deg);
  opacity: 0.38;
}
.shift-mark__plate--mid {
  transform: translate3d(-6%, 5%, -6px) rotate(-3deg);
  background: color-mix(in srgb, var(--accent) 22%, var(--surface-2));
  opacity: 0.72;
}
.shift-mark__plate--front {
  inset: 6% 16% 16% 6%;
  border-color: var(--accent);
  background: var(--accent);
  transform: translate3d(4%, -4%, 0);
}
.shift-mark__cut {
  position: absolute;
  inset: 0;
  background: var(--ink);
  clip-path: polygon(100% 0, 100% 34%, 66% 0);
  opacity: 0.2;
}
.shift-mark__signal {
  position: absolute;
  right: 16%;
  bottom: 16%;
  width: 18%;
  height: 18%;
  background: var(--signal);
  box-shadow: 0 0 0 3px color-mix(in srgb, var(--signal) 18%, transparent);
}
.shift-mark:hover .shift-mark__plate--back,
a:hover .shift-mark .shift-mark__plate--back {
  transform: translate3d(-24%, 18%, -16px) rotate(-12deg);
}
.shift-mark:hover .shift-mark__plate--mid,
a:hover .shift-mark .shift-mark__plate--mid {
  transform: translate3d(-10%, 8%, -8px) rotate(-5deg);
}
.shift-mark:hover .shift-mark__plate--front,
a:hover .shift-mark .shift-mark__plate--front {
  transform: translate3d(9%, -8%, 6px) rotate(1deg);
}
@media (prefers-reduced-motion: reduce) {
  .shift-mark__plate {
    transition: none;
  }
}
</style>
