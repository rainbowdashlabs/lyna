<script lang="ts" setup>
import {computed} from 'vue'

const props = defineProps<{
  name: string
}>()

const initials = computed(() => {
  const trimmed = props.name.trim()
  if (!trimmed) return '??'
  const parts = trimmed.split(/\s+/).filter(Boolean)
  if (parts.length === 1) return parts[0]!.slice(0, 2).toUpperCase()
  return (parts[0]![0]! + parts[1]![0]!).toUpperCase()
})
</script>

<template>
  <div
      class="flex h-16 w-16 items-center justify-center rounded-theme bg-primary text-primary-text text-2xl font-bold select-none"
      aria-hidden="true"
  >
    {{ initials }}
  </div>
</template>
