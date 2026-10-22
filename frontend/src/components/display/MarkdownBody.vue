/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed} from 'vue'
import {renderMarkdown} from '~/util/markdown'

const props = defineProps<{ markdown: string }>()

/**
 * Markdown as markup.
 *
 * <p>Rendered here rather than stored rendered, so what somebody wrote stays what they wrote and the
 * sanitising happens against the browser that is about to show it.
 */
const html = computed(() => renderMarkdown(props.markdown))
</script>

<template>
  <!-- eslint-disable-next-line vue/no-v-html -- renderMarkdown sanitises what it returns -->
  <div class="prose-markdown" v-html="html"/>
</template>

<style scoped>
/* Markdown somebody wrote, given the spacing prose needs without pulling in a typography plugin. */
.prose-markdown :deep(h1),
.prose-markdown :deep(h2),
.prose-markdown :deep(h3) {
  font-weight: 600;
  margin-block: 1rem 0.5rem;
}

.prose-markdown :deep(p) {
  margin-block: 0.5rem;
}

.prose-markdown :deep(ul),
.prose-markdown :deep(ol) {
  margin-block: 0.5rem;
  padding-inline-start: 1.5rem;
  list-style: revert;
}

.prose-markdown :deep(a) {
  color: var(--color-primary);
  text-decoration: underline;
}

.prose-markdown :deep(code) {
  font-family: var(--font-data, monospace);
  font-size: 0.9em;
}

.prose-markdown :deep(pre) {
  overflow-x: auto;
  padding: 0.75rem;
  margin-block: 0.75rem;
}
</style>
