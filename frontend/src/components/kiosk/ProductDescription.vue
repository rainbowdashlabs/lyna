/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import {computed} from 'vue'
import {renderMarkdown} from '~/util/markdown'

const {t} = useI18n()

const props = defineProps<{ markdown: string | null }>()

/**
 * The description as markup.
 *
 * <p>Rendered here rather than stored rendered, so what an operator wrote stays what they wrote and
 * the sanitising happens against the browser that is about to show it.
 */
const html = computed(() => renderMarkdown(props.markdown))
</script>

<template>
  <SectionCard>
    <SectionHeader>{{ t('page.products.id.about') }}</SectionHeader>
    <!-- eslint-disable-next-line vue/no-v-html -- renderMarkdown sanitises what it returns -->
    <div v-if="markdown" class="prose-description mt-3" v-html="html"/>
    <MutedText v-else class="mt-3" tag="p">{{ t('page.products.id.noDescriptionYet') }}</MutedText>
  </SectionCard>
</template>

<style scoped>
/* Markdown an operator wrote, given the spacing prose needs without pulling in a typography plugin. */
.prose-description :deep(h1),
.prose-description :deep(h2),
.prose-description :deep(h3) {
  font-weight: 600;
  margin-block: 1rem 0.5rem;
}

.prose-description :deep(p) {
  margin-block: 0.5rem;
}

.prose-description :deep(ul),
.prose-description :deep(ol) {
  margin-block: 0.5rem;
  padding-inline-start: 1.5rem;
  list-style: revert;
}

.prose-description :deep(a) {
  color: var(--color-primary);
  text-decoration: underline;
}

.prose-description :deep(code) {
  font-family: var(--font-data, monospace);
  font-size: 0.9em;
}

.prose-description :deep(pre) {
  overflow-x: auto;
  padding: 0.75rem;
  margin-block: 0.75rem;
}
</style>
