/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'

/**
 * Picks the Minecraft version somebody runs, so only builds that say they support it are listed.
 * Builds without a manifest make no claim and stay listed whatever is picked.
 */
const {t} = useI18n()

const model = defineModel<string | null>({required: true})

defineProps<{ versions: string[] }>()
</script>

<template>
  <LabelledField :label="t('ui.minecraftFilter.label')">
    <SelectInput v-model="model" data-testid="minecraft-filter">
      <option :value="null">{{ t('ui.minecraftFilter.any') }}</option>
      <option v-for="version in versions" :key="version" :value="version">{{ version }}</option>
    </SelectInput>
  </LabelledField>
</template>
