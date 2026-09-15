/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed} from 'vue'

const model = defineModel<boolean>({default: false})

const props = withDefaults(defineProps<{
  size?: 'sm' | 'md' | 'lg' | 'xl' | '2xl' | 'full'
}>(), {
  size: 'md',
})

const sizeClass = computed(() => {
  switch (props.size) {
    case 'sm': return 'max-w-md'
    case 'lg': return 'max-w-2xl'
    case 'xl': return 'max-w-5xl'
    case '2xl': return 'max-w-7xl'
    case 'full': return 'max-w-[95vw]'
    case 'md':
    default: return 'max-w-lg'
  }
})
</script>

<template>
  <Teleport to="body">
    <Transition name="modal">
      <div
          v-if="model"
          class="fixed inset-0 z-50 flex items-center justify-center"
      >
        <!-- Backdrop -->
        <div
            class="absolute inset-0 bg-black/50"
            @click="model = false"
        />
        <!-- Content -->
        <!--
          Capped and scrollable, because a dialog taller than the window otherwise runs off the
          bottom of it: the page behind does not scroll while a modal is open, so whatever fell past
          the edge - a long version list, the buttons under it - could not be reached at all.
        -->
        <div
            :class="['relative z-10 mx-4 max-h-[90vh] w-full overflow-y-auto rounded-theme border border-bg-light-accent bg-bg-light p-6 shadow-xl dark:border-bg-dark-accent dark:bg-bg-dark', sizeClass]">
          <button
              class="absolute top-3 right-3 p-1 text-[var(--text-muted)] hover:text-[var(--text)] transition-colors"
              @click="model = false"
          >
            <font-awesome-icon :icon="['fas', 'xmark']" class="h-5 w-5"/>
          </button>
          <slot/>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>

<style scoped>
.modal-enter-active,
.modal-leave-active {
  transition: opacity 0.2s ease;
}

.modal-enter-from,
.modal-leave-to {
  opacity: 0;
}
</style>
