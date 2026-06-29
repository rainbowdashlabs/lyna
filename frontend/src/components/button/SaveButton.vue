/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import { ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import BaseButton from './BaseButton.vue'

const props = withDefaults(defineProps<{
  /** Async action invoked on click. Reject to keep the button in idle state. */
  action: () => Promise<unknown> | unknown
  disabled?: boolean
  fullWidth?: boolean
  compact?: boolean
  /** Milliseconds to display the "saved" confirmation before returning to idle. */
  confirmDuration?: number
}>(), {
  confirmDuration: 2000,
})

const emit = defineEmits<{
  saved: []
  error: [err: unknown]
}>()

const { t } = useI18n()

type State = 'idle' | 'saving' | 'saved'
const state = ref<State>('idle')
const pulse = ref(false)
let revertTimer: ReturnType<typeof setTimeout> | null = null
let pulseTimer: ReturnType<typeof setTimeout> | null = null

async function handleClick() {
  if (state.value !== 'idle' || props.disabled) return
  state.value = 'saving'
  try {
    await props.action()
    state.value = 'saved'
    emit('saved')
    if (revertTimer) clearTimeout(revertTimer)
    revertTimer = setTimeout(() => { state.value = 'idle' }, props.confirmDuration)
  } catch (err) {
    state.value = 'idle'
    emit('error', err)
  }
}

watch(state, (s) => {
  if (s !== 'saved') return
  if (pulseTimer) clearTimeout(pulseTimer)
  pulse.value = true
  pulseTimer = setTimeout(() => { pulse.value = false }, 450)
})
</script>

<template>
  <BaseButton
      :disabled="disabled || state !== 'idle'"
      :full-width="fullWidth"
      :compact="compact"
      :class="[
        state === 'saved'
          ? 'bg-success text-success-text hover:brightness-110'
          : 'bg-primary text-primary-text hover:bg-primary-accent hover:text-primary-accent-text',
        pulse ? 'save-pulse' : '',
        'save-button',
      ]"
      @click="handleClick"
  >
    <Transition name="save-state" mode="out-in">
      <span :key="state" class="inline-flex items-center">
        <template v-if="state === 'saving'">
          <font-awesome-icon :icon="['fas', 'spinner']" spin class="mr-1"/>
          {{ t('common.saving') }}
        </template>
        <template v-else-if="state === 'saved'">
          <font-awesome-icon :icon="['fas', 'check']" class="mr-1"/>
          {{ t('common.saved') }}
        </template>
        <template v-else>
          <slot>{{ t('common.save') }}</slot>
        </template>
      </span>
    </Transition>
  </BaseButton>
</template>

<style scoped>
.save-button {
  transition: background-color 200ms ease, color 200ms ease, transform 150ms ease;
}

.save-state-enter-active,
.save-state-leave-active {
  transition: opacity 160ms ease, transform 160ms cubic-bezier(0.34, 1.36, 0.64, 1);
}
.save-state-enter-from {
  opacity: 0;
  transform: translateY(6px) scale(0.85);
}
.save-state-leave-to {
  opacity: 0;
  transform: translateY(-6px) scale(0.85);
}

@keyframes save-pulse {
  0% { transform: scale(1); }
  35% { transform: scale(1.07); }
  70% { transform: scale(0.98); }
  100% { transform: scale(1); }
}
.save-pulse {
  animation: save-pulse 450ms cubic-bezier(0.34, 1.56, 0.64, 1);
}
</style>
