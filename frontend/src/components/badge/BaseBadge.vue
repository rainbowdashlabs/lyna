<script lang="ts" setup>
defineProps<{
  bgClass: string
}>()
</script>

<template>
  <span
      :class="[bgClass, 'inline-flex items-center rounded-full px-2 py-0.5 text-xs font-medium']"
  >
    <slot />
  </span>
</template>
