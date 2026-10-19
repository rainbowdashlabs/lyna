/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import {RELEASE_TYPES, type ReleaseType} from '~/api/adminDownloads'

/**
 * Which release types a licence about to be issued reaches. Starts on stable builds only, which is
 * what a purchase grants, so issuing one by hand gives the same unless somebody chooses otherwise.
 */
const releaseTypes = defineModel<ReleaseType[]>({required: true})

const {t} = useI18n()

function flip(type: ReleaseType, on: boolean) {
  releaseTypes.value = on
      ? RELEASE_TYPES.filter(known => known === type || releaseTypes.value.includes(known))
      : releaseTypes.value.filter(known => known !== type)
}
</script>

<template>
  <div class="flex flex-wrap items-center gap-4">
    <SectionLabel>{{ t('ui.releaseTypePicker.reaches') }}</SectionLabel>
    <label v-for="type in RELEASE_TYPES" :key="type" class="font-data flex items-center gap-2 text-xs">
      <CheckboxInput :model-value="releaseTypes.includes(type)" @update:model-value="flip(type, $event)"/> {{ type }}
    </label>
  </div>
</template>
