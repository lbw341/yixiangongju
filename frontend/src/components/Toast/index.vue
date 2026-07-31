<template>
  <div class="fixed top-20 right-5 z-[1000] flex flex-col gap-2">
    <div
      v-for="t in toasts"
      :key="t.id"
      :class="['toast', `toast-${t.type}`]"
    >{{ t.msg }}</div>
  </div>
</template>

<script setup>import { useToast } from '../../composables/useToast';
const { toasts } = useToast();
</script>
