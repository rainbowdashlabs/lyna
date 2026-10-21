/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'

const {t} = useI18n()

defineProps<{ markdown: string | null }>()
</script>

<template>
  <SectionCard>
    <SectionHeader>{{ t('page.products.id.about') }}</SectionHeader>
    <MarkdownBody v-if="markdown" :markdown="markdown" class="mt-3"/>
    <MutedText v-else class="mt-3" tag="p">{{ t('page.products.id.noDescriptionYet') }}</MutedText>
  </SectionCard>
</template>
