/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed} from 'vue'
import BaseInput from '../BaseInput.vue'

const model = defineModel<string>()

defineProps<{
  disabled?: boolean
}>()

const shortModel = computed({
  get: () => model.value?.substring(0, 5) ?? '',
  set: (v: string | number) => {
    model.value = String(v).substring(0, 5)
  },
})
</script>

<template>
  <BaseInput
      v-model="shortModel"
      :disabled="disabled"
      class="no-seconds"
      step="60"
      type="time"
  />
</template>

<style scoped>
/* Hide the seconds segment in browsers that ignore step=60 */
.no-seconds::-webkit-datetime-edit-second-field,
.no-seconds::-webkit-datetime-edit-millisecond-field {
  display: none;
}
</style>
